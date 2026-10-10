package com.easychat.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import com.easychat.entity.constants.Constants;
import com.easychat.entity.po.UserInfo;
import com.easychat.entity.dto.MessageSendDto;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.entity.enums.MessageStatusEnum;
import com.easychat.entity.enums.MessageTypeEnum;
import com.easychat.entity.enums.ResponseCodeEnum;
import com.easychat.entity.enums.UserContactStatusEnum;
import com.easychat.entity.enums.UserContactTypeEnum;
import com.easychat.entity.po.ChatSessionUser;
import com.easychat.entity.po.UserContact;
import com.easychat.entity.query.ChatSessionUserQuery;
import com.easychat.exception.BusinessException;
import com.easychat.mappers.ChatSessionUserMapper;
import com.easychat.redis.redisComponent;
import com.easychat.mappers.UserInfoMapper;
import com.easychat.entity.query.UserInfoQuery;
import com.easychat.service.UserContactService;
import com.easychat.utils.CopyUtils;
import com.easychat.utils.StringTools;
import com.easychat.websocket.messageHandle;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.easychat.entity.enums.PageSize;
import com.easychat.entity.query.ChatSessionQuery;
import com.easychat.entity.query.ChatMessageQuery;
import com.easychat.entity.po.ChatMessage;
import com.easychat.entity.po.ChatSession;
import com.easychat.entity.vo.PaginationResultVO;
import com.easychat.entity.query.SimplePage;
import com.easychat.mappers.ChatMessageMapper;
import com.easychat.mappers.ChatSessionMapper;
import com.easychat.service.ChatMessageService;
import org.springframework.transaction.annotation.Transactional;


/**
 * 聊天消息表 业务接口实现
 */
@Service("chatMessageService")
public class ChatMessageServiceImpl implements ChatMessageService {
	private static final Logger logger = LoggerFactory.getLogger(ChatMessageServiceImpl.class);

	@Resource
	private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

	@Resource
	private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

	@Resource
	private redisComponent redisComponent;

	@Resource
	private messageHandle messageHandle;

	@Resource
	private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

	@Resource
	private UserContactService userContactService;

	@Resource
	private RedissonClient redissonClient;

	@Resource
	private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<ChatMessage> findListByParam(ChatMessageQuery param) {
		return this.chatMessageMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(ChatMessageQuery param) {
		return this.chatMessageMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<ChatMessage> findListByPage(ChatMessageQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<ChatMessage> list = this.findListByParam(param);
		PaginationResultVO<ChatMessage> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(ChatMessage bean) {
		return this.chatMessageMapper.insert(bean);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Integer addAndUpdateSession(ChatMessage bean) {
		if (this.chatSessionMapper.selectBySessionId(bean.getSessionId()) == null) {
			throw new IllegalStateException("会话不存在");
		}
		Integer inserted = this.chatMessageMapper.insert(bean);
		ChatSession session = new ChatSession();
		session.setLastMessage(bean.getMessageContent());
		session.setLastReceiveTime(bean.getSendTime());
		Integer updated = this.chatSessionMapper.updateLastMessageIfNewer(session, bean.getSessionId());
		if (inserted == null || inserted != 1 || updated == null) {
			throw new IllegalStateException("消息或会话预览保存失败");
		}
		return inserted;
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<ChatMessage> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.chatMessageMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<ChatMessage> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.chatMessageMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(ChatMessage bean, ChatMessageQuery param) {
		StringTools.checkParam(param);
		return this.chatMessageMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(ChatMessageQuery param) {
		StringTools.checkParam(param);
		return this.chatMessageMapper.deleteByParam(param);
	}

	/**
	 * 根据MessageId获取对象
	 */
	@Override
	public ChatMessage getChatMessageByMessageId(Long messageId) {
		return this.chatMessageMapper.selectByMessageId(messageId);
	}

	/**
	 * 根据MessageId修改
	 */
	@Override
	public Integer updateChatMessageByMessageId(ChatMessage bean, Long messageId) {
		return this.chatMessageMapper.updateByMessageId(bean, messageId);
	}

	/**
	 * 根据MessageId删除
	 */
	@Override
	public Integer deleteChatMessageByMessageId(Long messageId) {
		return this.chatMessageMapper.deleteByMessageId(messageId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public MessageSendDto saveMessage(ChatMessage chatMessage, TokenUserinfoDTO tokenUserinfoDTO, String clientMessageId) {
		if (tokenUserinfoDTO == null || StringTools.isEmpty(tokenUserinfoDTO.getUserId())) {
			throw new BusinessException(ResponseCodeEnum.CODE_901);
		}
		if (chatMessage == null || chatMessage.getMessageType() == null
				|| !MessageTypeEnum.CHAT.getType().equals(chatMessage.getMessageType())
				|| StringTools.isEmpty(chatMessage.getMessageContent()) || chatMessage.getMessageContent().length() > 10000) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		String userId = tokenUserinfoDTO.getUserId();
		String contactId = chatMessage.getContactId();
		if (StringTools.isEmpty(contactId)) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		RLock sendLock = null;
		boolean lockHeld = false;
		boolean unlockAfterTransaction = false;
		if (!StringTools.isEmpty(clientMessageId)) {
			sendLock = redissonClient.getLock(Constants.REDIS_CHAT_SEND_LOCK + userId + ":" + clientMessageId);
			try {
				lockHeld = sendLock.tryLock(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new BusinessException("消息请求已中断，请重试");
			}
			if (!lockHeld) {
				throw new BusinessException("消息正在处理中，请稍后重试");
			}
		}

		try {
			if (clientMessageId != null) {
				MessageSendDto cached = redisComponent.getCachedChatMessage(userId, clientMessageId);
				if (cached != null) {
					return cached;
				}
			}
			UserContact senderContact = userContactService.getUserContactByUserIdAndContactId(userId, contactId);
			UserContactTypeEnum contactType = senderContact == null
					? UserContactTypeEnum.getByPrefix(contactId)
					: UserContactTypeEnum.getByType(senderContact.getContactType());
			UserContactTypeEnum idContactType = UserContactTypeEnum.getByPrefix(contactId);
			if (contactType == null || (idContactType != null && idContactType != contactType)) {
				throw new BusinessException(ResponseCodeEnum.CODE_600);
			}

			String sessionId;
			ChatSessionUser senderSession;
			ChatSessionUser receiverSession = null;
			if (contactType == UserContactTypeEnum.USER) {
				if (Objects.equals(userId, contactId)) {
					throw new BusinessException(ResponseCodeEnum.CODE_902);
				}
				boolean robotContact = Objects.equals(contactId, redisComponent.getSysSettingDTO().getRobotUid());
				UserContact receiverContact = robotContact ? null : userContactService.getUserContactByUserIdAndContactId(contactId, userId);
				if (!isFriend(senderContact) || (!robotContact && !isFriend(receiverContact))) {
					throw new BusinessException(ResponseCodeEnum.CODE_902);
				}

				sessionId = StringTools.getChatSessionIdUser(new String[]{userId, contactId});
				senderSession = chatSessionUserMapper.selectByUserIdAndContactId(userId, contactId);
				receiverSession = robotContact ? null : chatSessionUserMapper.selectByUserIdAndContactId(contactId, userId);
				if (!hasSession(senderSession, sessionId) || (!robotContact && !hasSession(receiverSession, sessionId))) {
					throw new BusinessException("会话信息不完整，请刷新后重试");
				}
			} else {
				UserContact membership = userContactService.getUserContactByUserIdAndContactId(userId, contactId);
				if (membership == null || !Objects.equals(membership.getContactType(), UserContactTypeEnum.Group.getType())
						|| !UserContactStatusEnum.FRIEND.getStatus().equals(membership.getStatus())) {
					throw new BusinessException(ResponseCodeEnum.CODE_903);
				}
				sessionId = StringTools.getChatSessionIdGroup(contactId);
				senderSession = chatSessionUserMapper.selectByUserIdAndContactId(userId, contactId);
				if (!hasSession(senderSession, sessionId)) {
					throw new BusinessException("会话信息不完整，请刷新后重试");
				}
			}

			chatMessage.setSessionId(sessionId);
			chatMessage.setContactType(contactType.getType());
			chatMessage.setSendUserId(userId);
			String sendUserNickName = tokenUserinfoDTO.getNickname();
			if (StringTools.isEmpty(sendUserNickName)) {
				com.easychat.entity.po.UserInfo senderInfo = userInfoMapper.selectByUserId(userId);
				sendUserNickName = senderInfo == null ? userId : senderInfo.getNickName();
			}
			chatMessage.setSendUserNickName(sendUserNickName);
			chatMessage.setSendTime(System.currentTimeMillis());
			chatMessage.setStatus(MessageStatusEnum.SENDED.getStatus());
			Integer inserted = addAndUpdateSession(chatMessage);
			if (inserted == null || inserted != 1) {
				throw new IllegalStateException("消息保存失败");
			}

			MessageSendDto messageSendDto = CopyUtils.copy(chatMessage, MessageSendDto.class);
			messageSendDto.setClientMessageId(clientMessageId);
			messageSendDto.setContactName(senderSession.getContactName());
			messageSendDto.setExtendData(senderSession);
			messageSendDto.setLastMessage(chatMessage.getMessageContent());
			MessageSendDto messageEvent = messageSendDto;
			if (receiverSession != null) {
				messageEvent = CopyUtils.copy(chatMessage, MessageSendDto.class);
				messageEvent.setClientMessageId(clientMessageId);
				messageEvent.setExtendData(receiverSession);
			}
			final MessageSendDto committedMessage = messageSendDto;
			final MessageSendDto outboundEvent = messageEvent;
			final String senderId = userId;
			final String requestId = clientMessageId;
			final RLock transactionLock = sendLock;
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
				@Override
				public void afterCommit() {
					if (requestId != null) {
						try {
							redisComponent.cacheChatMessage(senderId, requestId, committedMessage);
						} catch (Exception e) {
						logger.warn("已保存的消息回执未能缓存，clientMessageId={}", requestId, e);
					}
					}
					try {
						messageHandle.sendMsg(outboundEvent);
					} catch (Exception e) {
						logger.warn("消息已保存，但实时通知失败，messageId={}", committedMessage.getMessageId(), e);
					}
				}

				@Override
				public void afterCompletion(int status) {
					if (transactionLock != null && transactionLock.isHeldByCurrentThread()) {
						transactionLock.unlock();
					}
				}
			});
			unlockAfterTransaction = sendLock != null;
			return messageSendDto;
		} finally {
			if (lockHeld && !unlockAfterTransaction && sendLock.isHeldByCurrentThread()) {
				sendLock.unlock();
			}
		}
	}

	private boolean isFriend(UserContact contact) {
		return contact != null
				&& Objects.equals(contact.getContactType(), UserContactTypeEnum.USER.getType())
				&& UserContactStatusEnum.FRIEND.getStatus().equals(contact.getStatus());
	}

	private boolean hasSession(ChatSessionUser session, String sessionId) {
		return session != null && sessionId.equals(session.getSessionId());
	}
}
