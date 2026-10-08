package com.easychat.websocket.netty;

import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.entity.dto.MessageSendDto;
import com.easychat.entity.enums.MessageStatusEnum;
import com.easychat.entity.enums.MessageTypeEnum;
import com.easychat.entity.enums.UserContactStatusEnum;
import com.easychat.entity.po.ChatMessage;
import com.easychat.entity.po.ChatSessionUser;
import com.easychat.entity.po.UserContact;
import com.easychat.entity.po.UserInfo;
import com.easychat.entity.query.UserInfoQuery;
import com.easychat.exception.BusinessException;
import com.easychat.mappers.UserInfoMapper;
import com.easychat.redis.redisComponent;
import com.easychat.service.ChatMessageService;
import com.easychat.service.ChatSessionUserService;
import com.easychat.service.UserContactService;
import com.easychat.utils.JsonUtils;
import com.easychat.websocket.messageHandle;
import com.easychat.websocket.ChannelContextUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@ChannelHandler.Sharable
public class HandleWebSocket extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(HandleWebSocket.class);

    @Resource
    private redisComponent redisComponent;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ChatSessionUserService chatSessionUserService;

    @Resource
    private UserContactService userContactService;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private messageHandle messageHandle;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) throws Exception {
        Channel channel = ctx.channel();
        Attribute<String> attribute = channel.attr(AttributeKey.valueOf(channel.id().toString()));
        String userId = attribute.get();
        redisComponent.setUserHeartBeat(userId);
        MessageSendDto<?> request = null;
        try {
            request = JsonUtils.convertJson2Obj(textWebSocketFrame.text(), MessageSendDto.class);
            if (request == null || !MessageTypeEnum.CHAT.getType().equals(request.getMessageType())) {
                throw new BusinessException("暂不支持该消息类型");
            }
            sendChatMessage(ctx, userId, request);
        } catch (BusinessException e) {
            logger.warn("WebSocket 消息校验失败，userId={}: {}", userId, e.getMessage());
            sendFailure(ctx, e.getMessage(), request == null ? null : request.getClientMessageId());
        } catch (Exception e) {
            logger.error("WebSocket 消息处理失败，userId={}", userId, e);
            sendFailure(ctx, "消息发送失败，请稍后重试", request == null ? null : request.getClientMessageId());
        }
    }

    private void sendChatMessage(ChannelHandlerContext ctx, String userId, MessageSendDto<?> request) {
        String contactId = request.getContactId();
        String content = request.getMessageContent() == null ? "" : request.getMessageContent().trim();
        if (userId == null || contactId == null || contactId.isEmpty() || content.isEmpty()) {
            throw new BusinessException("消息内容或联系人无效");
        }
        if (!Integer.valueOf(0).equals(request.getContactType())) {
            throw new BusinessException("当前只支持好友私聊");
        }
        if (content.length() > 10000) {
            throw new BusinessException("消息内容不能超过10000个字符");
        }

        ChatSessionUser senderSession = chatSessionUserService.getChatSessionUserByUserIdAndContactId(userId, contactId);
        if (senderSession == null || request.getSessionId() == null || !senderSession.getSessionId().equals(request.getSessionId())) {
            throw new BusinessException("无权向该会话发送消息");
        }
        UserContact contact = userContactService.getUserContactByUserIdAndContactId(userId, contactId);
        UserContact receiverContact = userContactService.getUserContactByUserIdAndContactId(contactId, userId);
        if (contact == null || receiverContact == null
                || !UserContactStatusEnum.FRIEND.getStatus().equals(contact.getStatus())
                || !UserContactStatusEnum.FRIEND.getStatus().equals(receiverContact.getStatus())) {
            throw new BusinessException("该联系人当前不可用");
        }
        ChatSessionUser receiverSession = chatSessionUserService.getChatSessionUserByUserIdAndContactId(contactId, userId);
        if (receiverSession == null || !senderSession.getSessionId().equals(receiverSession.getSessionId())) {
            throw new BusinessException("会话联系人信息不完整");
        }
        UserInfo sender = userInfoMapper.selectByUserId(userId);
        if (sender == null) {
            throw new BusinessException("发送用户不存在");
        }

        long sendTime = System.currentTimeMillis();
        ChatMessage chatMessage = ChatMessage.builder()
                .sessionId(senderSession.getSessionId())
                .messageType(MessageTypeEnum.CHAT.getType())
                .messageContent(content)
                .sendUserId(userId)
                .sendUserNickName(sender.getNickName())
                .sendTime(sendTime)
                .contactId(contactId)
                .contactType(0)
                .status(MessageStatusEnum.SENDED.getStatus())
                .build();
        chatMessageService.addAndUpdateSession(chatMessage);

        MessageSendDto<ChatSessionUser> receiverEvent = toChatEvent(chatMessage, receiverSession, request.getClientMessageId());
        receiverEvent.setContactId(contactId);

        MessageSendDto<ChatSessionUser> senderEvent = toChatEvent(chatMessage, senderSession, request.getClientMessageId());
        senderEvent.setContactId(senderSession.getContactId());
        senderEvent.setContactName(senderSession.getContactName());
        ctx.writeAndFlush(new TextWebSocketFrame(JsonUtils.convertObj2Json(senderEvent)));
        try {
            messageHandle.sendMsg(receiverEvent);
        } catch (Exception e) {
            // The message is committed and acknowledged to its sender. The recipient can still load it from history.
            logger.error("消息已保存，但通知接收方失败，messageId={}, contactId={}", chatMessage.getMessageId(), contactId, e);
        }
    }

    private MessageSendDto<ChatSessionUser> toChatEvent(ChatMessage message, ChatSessionUser session, String clientMessageId) {
        return MessageSendDto.<ChatSessionUser>builder()
                .messageId(message.getMessageId())
                .sessionId(message.getSessionId())
                .sendUserId(message.getSendUserId())
                .sendUserNickName(message.getSendUserNickName())
                .messageContent(message.getMessageContent())
                .messageType(message.getMessageType())
                .sendTime(message.getSendTime())
                .contactId(session.getContactId())
                .contactName(session.getContactName())
                .contactType(0)
                .status(message.getStatus())
                .clientMessageId(clientMessageId)
                .extendData(session)
                .build();
    }

    private void sendFailure(ChannelHandlerContext ctx, String reason, String clientMessageId) {
        MessageSendDto<Void> response = MessageSendDto.<Void>builder()
                .messageType(MessageTypeEnum.CHAT.getType())
                .messageContent(reason == null || reason.isEmpty() ? "消息发送失败" : reason)
                .status(2)
                .clientMessageId(clientMessageId)
                .build();
        ctx.writeAndFlush(new TextWebSocketFrame(JsonUtils.convertObj2Json(response)));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("有新的链接加入");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("有链接断开");
        channelContextUtils.removeContext(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("websocket连接异常: {}", cause.getMessage());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete complete = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String token = new QueryStringDecoder(complete.requestUri()).parameters()
                    .getOrDefault("token", java.util.Collections.<String>emptyList())
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (token == null) {
                logger.warn("WebSocket 握手缺少 token 查询参数");
                ctx.channel().close();
                return;
            }

            TokenUserinfoDTO tokenUserInfoDTO = redisComponent.getTokenUserInfoDTO(token);
            if (null == tokenUserInfoDTO) {
               logger.warn("WebSocket 握手 token 无效");
               ctx.channel().close();
               return;
            }

            channelContextUtils.addContext(tokenUserInfoDTO.getUserId(), ctx.channel());
        }
    }
}
