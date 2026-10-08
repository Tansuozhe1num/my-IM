package com.easychat.websocket;

import com.easychat.entity.dto.MessageSendDto;
import com.easychat.entity.dto.WsInitData;
import com.easychat.entity.enums.MessageTypeEnum;
import com.easychat.entity.enums.UserContactApplyEnum;
import com.easychat.entity.enums.UserContactStatusEnum;
import com.easychat.entity.enums.UserContactTypeEnum;
import com.easychat.entity.po.ChatMessage;
import com.easychat.entity.po.ChatSessionUser;
import com.easychat.entity.po.UserInfo;
import com.easychat.entity.query.*;
import com.easychat.mappers.UserInfoMapper;
import com.easychat.redis.redisComponent;
import com.easychat.service.ChatMessageService;
import com.easychat.service.ChatSessionUserService;
import com.easychat.service.UserContactApplyService;
import com.easychat.utils.JsonUtils;
import com.easychat.utils.StringTools;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component("ChannelContextUtils")
public class ChannelContextUtils {

    private static final Logger logger = LoggerFactory.getLogger(ChannelContextUtils.class);

    private ConcurrentHashMap<String, ChannelGroup> GROUP_CONTEXT_LIST = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Channel> USER_CONTEXT_MAP = new ConcurrentHashMap<>();

    @Resource
    private redisComponent redisComponent;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private ChatSessionUserService chatSessionUserService;

    @Resource
    private UserContactApplyService userContactApplyService;

    public void addContext(String UserId, Channel channel) {
        String ChannelId = channel.id().toString();
        AttributeKey attributeKey = null;
        if (!AttributeKey.exists(ChannelId)) {
            attributeKey = AttributeKey.newInstance(ChannelId);
        } else {
            attributeKey = AttributeKey.valueOf(ChannelId);
        }
        channel.attr(attributeKey).set(UserId);

        List<String> userContactList = redisComponent.getUserContactList(UserId);
        for (String id : userContactList) {
            if (id.startsWith("G")) {
                add2Group(id, channel);
            }
        }

        USER_CONTEXT_MAP.put(UserId, channel);
        redisComponent.setUserHeartBeat(UserId);

        UserInfo userInfo = new UserInfo();
        userInfo.setLastLoginTime(new Date());
        userInfoMapper.updateByUserId(userInfo, UserId);

        UserInfo info = userInfoMapper.selectByUserId(UserId);
        Long lastOffTime = info.getLastOffTime();
        if (lastOffTime != null) {
            lastOffTime = Math.max(lastOffTime, System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000);
        }

        /**
         * 会话记录
         */
        ChatSessionUserQuery query = new ChatSessionUserQuery();
        query.setUserId(UserId);
        query.setOrderBy("last_receive_time desc");
        query.setGetLastMessage(true);
        List<ChatSessionUser> sessonsInfo = this.chatSessionUserService.findListByParam(query);;

        /**
         * 查询消息
         */
        List<String> sessions = sessonsInfo.stream().map(ChatSessionUser::getSessionId).collect(Collectors.toList());
        ChatMessageQuery q2 = ChatMessageQuery.builder().sessionIds(sessions).build();
        List<ChatMessage> chatmessages = chatMessageService.findListByParam(q2);

        /**
         * 好友申请
         */
        UserContactApplyQuery q3 = new UserContactApplyQuery();
        q3.setStatus(UserContactApplyEnum.Progress.getStatus());
        q3.setReceiveUserId(UserId);
        Integer applyCount = userContactApplyService.findCountByParam(q3);

        WsInitData initData = WsInitData.builder()
                .chatSessionUsers(sessonsInfo)
                .chatMessages(chatmessages)
                .applyCount(applyCount)
                .build();

        MessageSendDto messageSendDto = MessageSendDto.builder()
                .messageType(MessageTypeEnum.INIT.getType())
                .extendData(initData)
                .build();

        sendMsg(messageSendDto, UserId);
    }

    public void add2Group(String groupId, Channel channel) {
        ChannelGroup group = GROUP_CONTEXT_LIST.get(groupId);
        if (group == null) {
            group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            GROUP_CONTEXT_LIST.put(groupId, group);
        }
        if (channel == null) {
            return;
        }
        group.add(channel);
    }

    public void removeContext(Channel channel) {
        Attribute<String> attribute = channel.attr(AttributeKey.valueOf(channel.id().toString()));
        String userId = attribute.get();
        if (StringTools.isEmpty(userId)) {
            return;
        }
        USER_CONTEXT_MAP.remove(userId);
        try {
            redisComponent.deleteUserHeartBeat(userId);
        } catch (Exception e) {
            logger.warn("清理用户心跳缓存失败, userId={}", userId, e);
        }
        try {
            UserInfo info = new UserInfo();
            info.setLastOffTime(System.currentTimeMillis());
            userInfoMapper.updateByUserId(info, userId);
        } catch (Exception e) {
            // 应用停机时连接池可能已关闭，避免异常冲到 Netty pipeline 尾部
            logger.warn("更新用户离线时间失败, userId={}", userId, e);
        }
    }

    public void sendMessage(MessageSendDto msg) {
        UserContactTypeEnum userContactTypeEnum = UserContactTypeEnum.getByPrefix(msg.getContactId());
        switch (userContactTypeEnum) {
            case USER:
                send2User(msg);
                break;
            case Group:
                send2Group(msg);
                break;
        }
    }

    public void send2User(MessageSendDto msg) {
        String ContactId = msg.getContactId();
        if (ContactId == null) {
            return;
        }
        sendMsg(msg, ContactId);
        // 强制下线
        if (MessageTypeEnum.FORCE_OFF_LINE.getType().equals(msg.getMessageType())) {
            String userId = msg.getContactId();
            if (StringUtils.isEmpty(userId)) {
                return;
            }
            redisComponent.deleteTokenUserInfoDTO(userId);
            Channel channel = USER_CONTEXT_MAP.get(userId);
            if (channel == null) {
                return;
            }
            channel.close();
        }
    }

    public void send2Group(MessageSendDto msg) {
        if (msg.getContactId() == null) {
            return;
        }
        ChannelGroup receiveChan = GROUP_CONTEXT_LIST.get(msg.getContactId());
        if (receiveChan == null) {
            return;
        }
        receiveChan.writeAndFlush(new TextWebSocketFrame(JsonUtils.convertObj2Json(msg.getMessageContent())));
    }

    public void sendMsg(MessageSendDto msg, String receive) {
        if (receive == null) {
            return;
        }
        Channel receiveChan = USER_CONTEXT_MAP.get(receive);
        if (receiveChan == null) {
            return;
        }
        if (!MessageTypeEnum.ADD_FRIEND_SELF.getType().equals(msg.getMessageType())) {
            msg.setContactId(msg.getSendUserId());
            msg.setContactName(msg.getSendUserNickName());
        } else {
            if (msg.getExtendData() == null) {
                logger.warn("好友欢迎消息缺少会话联系人信息，messageId={}", msg.getMessageId());
                return;
            }
            ChatSessionUser sessionUser = msg.getExtendData() instanceof ChatSessionUser
                    ? (ChatSessionUser) msg.getExtendData()
                    : JsonUtils.convertJson2Obj(JsonUtils.convertObj2Json(msg.getExtendData()), ChatSessionUser.class);
            if (sessionUser == null || sessionUser.getContactId() == null) {
                logger.warn("好友欢迎消息联系人信息无效，messageId={}", msg.getMessageId());
                return;
            }
            msg.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());
            msg.setContactId(sessionUser.getContactId());
            msg.setContactName(sessionUser.getContactName());
        }
        receiveChan.writeAndFlush(new TextWebSocketFrame(JsonUtils.convertObj2Json(msg)));
    }
}
