package com.easychat.service.impl;

import java.util.*;

import javax.annotation.Resource;

import com.easychat.entity.dto.MessageSendDto;
import com.easychat.entity.dto.SysSettingDto;
import com.easychat.entity.enums.*;
import com.easychat.entity.po.*;
import com.easychat.entity.query.*;
import com.easychat.entity.vo.ContactInfoVO;
import com.easychat.entity.vo.GroupInfoVO;
import com.easychat.entity.vo.SearchVo;
import com.easychat.exception.BusinessException;
import com.easychat.mappers.*;
import com.easychat.redis.redisComponent;
import com.easychat.utils.CopyUtils;
import com.easychat.utils.IdGenerator;
import com.easychat.websocket.ChannelContextUtils;
import com.easychat.websocket.messageHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.easychat.entity.vo.PaginationResultVO;
import com.easychat.service.UserContactService;
import com.easychat.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;


/**
 * 联系人 业务接口实现
 */
@Service("userContactService")
public class UserContactServiceImpl implements UserContactService {

	@Resource
	private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

	@Resource
	private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	@Resource
	private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;

	@Resource
	private UserContactApplyMapper<UserContactApply, UserContactApplyQuery> userContactApplyMapper;

	@Resource
	private redisComponent redisComponent;

	@Resource
	private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

	@Resource
	private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

	@Resource
	private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

	private static final Logger logger = LoggerFactory.getLogger(UserContactServiceImpl.class);

	@Resource
	private messageHandle messageHandle;

	@Resource
	private ChannelContextUtils channelContextUtils;
	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<UserContact> findListByParam(UserContactQuery param) {
		return this.userContactMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(UserContactQuery param) {
		return this.userContactMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<UserContact> findListByPage(UserContactQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<UserContact> list = this.findListByParam(param);
		PaginationResultVO<UserContact> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(UserContact bean) {
		return this.userContactMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<UserContact> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userContactMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<UserContact> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userContactMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(UserContact bean, UserContactQuery param) {
		StringTools.checkParam(param);
		return this.userContactMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(UserContactQuery param) {
		StringTools.checkParam(param);
		return this.userContactMapper.deleteByParam(param);
	}

	/**
	 * 根据UserIdAndContactId获取对象
	 */
	@Override
	public UserContact getUserContactByUserIdAndContactId(String userId, String contactId) {
		return this.userContactMapper.selectByUserIdAndContactId(userId, contactId);
	}

	/**
	 * 根据UserIdAndContactId修改
	 */
	@Override
	public Integer updateUserContactByUserIdAndContactId(UserContact bean, String userId, String contactId) {
		return this.userContactMapper.updateByUserIdAndContactId(bean, userId, contactId);
	}

	/**
	 * 根据UserIdAndContactId删除
	 */
	@Override
	public Integer deleteUserContactByUserIdAndContactId(String userId, String contactId) {
		return this.userContactMapper.deleteByUserIdAndContactId(userId, contactId);
	}

	@Override
	public SearchVo searchFriends(String UserId, String ContactId) {
		if (ContactId == null) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

//		UserContact Info = this.userContactMapper.selectByUserIdAndContactId(UserId, ContactId);
//		if (Info == null) {
//			throw new BusinessException("查询不到群组");
//		}

//		Integer type = Info.getContactType();

		UserInfo userInfo = this.userInfoMapper.selectByUserId(ContactId);

		SearchVo result = new SearchVo();
		List<UserInfo> userInfoList = new ArrayList<>();
		if (userInfo != null) { // 查询单一用户
		//UserInfo userInfo = this.userInfoMapper.selectByUserId(ContactId);
			userInfoList.add(userInfo);
		} else { // 查询群组信息
			GroupInfo groupInfo = this.groupInfoMapper.selectByGroupId(ContactId);
			if (groupInfo == null || GroupInfoStatusEnum.DisSolution.getStatus().equals(groupInfo.getStatus())) {
				throw new BusinessException("群组不存在或已解散");
			}
			UserContactQuery query = new UserContactQuery();
			query.setContactId(ContactId);
			query.setContactType(UserContactTypeEnum.Group.getType());
			query.setStatus(UserContactStatusEnum.FRIEND.getStatus());
			List<UserContact> infos = this.userContactMapper.selectList(query);
			for (UserContact info : infos) {
				UserInfo k = this.userInfoMapper.selectByUserId(info.getUserId());
				userInfoList.add(k);
			}
			result.setGroupInfo(groupInfo);
		}
		result.setUserInfoList(userInfoList);
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean applyAdd(String UserId, String ContactId, UserContactTypeEnum ContactType, String ApplyInfo) {
		if (StringTools.isEmpty(UserId) || StringTools.isEmpty(ContactId) || ContactType == null) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}
		String ReceiveId = ContactId;
		if (UserContactTypeEnum.USER.equals(ContactType)) {
			if (UserId.equals(ContactId) || this.userInfoMapper.selectByUserId(ContactId) == null) {
				throw new BusinessException("用户不存在");
			}
			UserContact relation = this.userContactMapper.selectByUserIdAndContactId(UserId, ContactId);
			if (relation != null && UserContactStatusEnum.FRIEND.getStatus().equals(relation.getStatus())) {
				throw new BusinessException("已经是好友");
			}
			if (relation != null && UserContactStatusEnum.BLACKLIST_BE.getStatus().equals(relation.getStatus())) {
				throw new BusinessException("被对方拉黑");
			}
		} else {
			GroupInfo group = this.groupInfoMapper.selectByGroupId(ContactId);
			if (group == null || GroupInfoStatusEnum.DisSolution.getStatus().equals(group.getStatus())) {
				throw new BusinessException("群组不存在或已解散");
			}
			UserContact membership = this.userContactMapper.selectByUserIdAndContactId(UserId, ContactId);
			if (membership != null && UserContactStatusEnum.FRIEND.getStatus().equals(membership.getStatus())) {
				throw new BusinessException("已经在群组中");
			}
			if (JoinTypeEnum.JOIN.getType().equals(group.getJoinType())) {
				this.addContact(UserId, group.getGroupOwnerId(), ContactId, ContactType.getType(), ApplyInfo);
				createGroupSessionAndGreeting(UserId, ContactId);
				return false;
			}
			ReceiveId = group.getGroupOwnerId();
		}

		UserContactApply apply = this.userContactApplyMapper.selectByApplyUserIdAndReceiveUserIdAndContactId(UserId, ReceiveId, ContactId);
		if (apply == null) {
			apply = new UserContactApply();
			apply.setApplyUserId(UserId);
			apply.setReceiveUserId(ReceiveId);
			apply.setContactId(ContactId);
		} else if (UserContactApplyEnum.Progress.getStatus().equals(apply.getStatus())) {
			return true;
		}
		apply.setContactType(ContactType.getType());
		apply.setApplyInfo(ApplyInfo);
		apply.setLastApplyTime(System.currentTimeMillis());
		apply.setStatus(UserContactApplyEnum.Progress.getStatus());
		if (apply.getApplyId() == null) {
			this.userContactApplyMapper.insert(apply);
		} else {
			this.userContactApplyMapper.updateByApplyId(apply, apply.getApplyId());
		}

		final String receiveId = ReceiveId;
		final UserContactApply savedApply = apply;
		UserInfo applicant = this.userInfoMapper.selectByUserId(UserId);
		MessageSendDto<UserContactApply> event = MessageSendDto.<UserContactApply>builder()
				.messageType(MessageTypeEnum.CONTACT_APPLY.getType())
				.messageId(IdGenerator.nextIdLong())
				.messageContent(ApplyInfo)
				.sendTime(System.currentTimeMillis())
				.sendUserId(UserId)
				.sendUserNickName(applicant == null ? UserId : applicant.getNickName())
				.contactId(receiveId)
				.contactType(UserContactTypeEnum.USER.getType())
				.extendData(apply)
				.build();
		Integer applyId = apply.getApplyId();
		afterCommit(() -> {
			try {
				messageHandle.sendMsg(event);
			} catch (Exception e) {
				logger.warn("申请已保存，但实时通知失败，applyId={}", applyId, e);
			}
			if (!UserContactTypeEnum.Group.equals(ContactType) || UserId.equals(receiveId)) {
				return;
			}
			MessageSendDto<UserContactApply> applicantEvent = MessageSendDto.<UserContactApply>builder()
					.messageId(event.getMessageId())
					.messageType(event.getMessageType())
					.messageContent(event.getMessageContent())
					.sendTime(event.getSendTime())
					.sendUserId(UserId)
					.sendUserNickName(event.getSendUserNickName())
					.contactId(UserId)
					.contactType(UserContactTypeEnum.USER.getType())
					.extendData(savedApply)
					.build();
			try {
				messageHandle.sendMsg(applicantEvent);
			} catch (Exception e) {
				logger.warn("申请已保存，但通知申请人失败，applyId={}", applyId, e);
			}
		});
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void SolveApply(String UserId, String ContactId, Integer accept) {
		if (ContactId == null || accept == null || accept < UserContactApplyEnum.Accept.getStatus() || accept > UserContactApplyEnum.Black.getStatus()) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		Integer applyId;
		try {
			applyId = Integer.valueOf(ContactId);
		} catch (NumberFormatException e) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}
		UserContactApply apply = this.userContactApplyMapper.selectByApplyId(applyId);
		if (apply == null
				|| !UserId.equals(apply.getReceiveUserId())
				|| !UserContactApplyEnum.Progress.getStatus().equals(apply.getStatus())) {
			throw new BusinessException("申请已经失效");
		}
		if (UserContactTypeEnum.Group.getType().equals(apply.getContactType())) {
			GroupInfo group = this.groupInfoMapper.selectByGroupId(apply.getContactId());
			if (group == null || !UserId.equals(group.getGroupOwnerId())
					|| GroupInfoStatusEnum.DisSolution.getStatus().equals(group.getStatus())) {
				throw new BusinessException("仅当前群主可以处理该申请");
			}
			if (UserContactApplyEnum.Black.getStatus().equals(accept)) {
				throw new BusinessException("群申请仅支持接受或拒绝");
			}
		}
		apply.setStatus(accept);
		apply.setLastApplyTime((new Date()).getTime());

		UserContactApplyQuery query = new UserContactApplyQuery();
		query.setApplyId(apply.getApplyId());
		query.setStatus(UserContactApplyEnum.Progress.getStatus());
		if (this.userContactApplyMapper.updateByParam(apply, query) == 0) {
			throw new BusinessException("申请已被处理");
		}

		if (accept.equals(UserContactApplyEnum.Accept.getStatus())) {
			this.addContact(apply.getApplyUserId(), apply.getReceiveUserId(), apply.getContactId(), apply.getContactType(), apply.getApplyInfo());
			if (UserContactTypeEnum.USER.getType().equals(apply.getContactType())) {
				createFriendSessionAndGreeting(apply);
			} else {
				createGroupSessionAndGreeting(apply);
			}
		} else if (accept.equals(UserContactApplyEnum.Black.getStatus())) {
			UserContact contact = this.userContactMapper.selectByUserIdAndContactId(UserId, apply.getApplyUserId());
			if (contact == null) {
				contact = new UserContact();
				contact.setUserId(UserId);
				contact.setContactId(apply.getApplyUserId());
				contact.setContactType(UserContactTypeEnum.USER.getType());
				contact.setCreateTime(new Date());
			}
			contact.setLastUpdateTime(new Date());
			contact.setStatus(UserContactStatusEnum.BLACKLIST.getStatus());
			if (this.userContactMapper.selectByUserIdAndContactId(UserId, apply.getApplyUserId()) == null) {
				this.userContactMapper.insert(contact);
			} else {
				this.userContactMapper.updateByUserIdAndContactId(contact, UserId, apply.getApplyUserId());
			}
			logger.info("新加黑名单关系 : (" + contact.getUserId() + " : " + contact.getContactId() + ")");
		}

		UserInfo resolver = this.userInfoMapper.selectByUserId(UserId);
		long resolvedAt = System.currentTimeMillis();
		Long resolutionMessageId = IdGenerator.nextIdLong();
		String resolverName = resolver == null ? UserId : resolver.getNickName();
		MessageSendDto<UserContactApply> resolution = MessageSendDto.<UserContactApply>builder()
				.messageType(MessageTypeEnum.CONTACT_APPLY.getType())
				.messageId(resolutionMessageId)
				.messageContent(UserContactApplyEnum.Accept.getStatus().equals(accept) ? "申请已通过" : "申请已处理")
				.sendTime(resolvedAt)
				.sendUserId(UserId)
				.sendUserNickName(resolverName)
				.contactId(apply.getApplyUserId())
				.contactType(UserContactTypeEnum.USER.getType())
				.extendData(apply)
				.build();
		MessageSendDto<UserContactApply> resolverUpdate = MessageSendDto.<UserContactApply>builder()
				.messageType(MessageTypeEnum.CONTACT_APPLY.getType())
				.messageId(resolutionMessageId)
				.messageContent(resolution.getMessageContent())
				.sendTime(resolvedAt)
				.sendUserId(UserId)
				.sendUserNickName(resolverName)
				.contactId(UserId)
				.contactType(UserContactTypeEnum.USER.getType())
				.extendData(apply)
				.build();
		afterCommit(() -> {
			try {
				messageHandle.sendMsg(resolution);
			} catch (Exception e) {
				logger.warn("申请已处理，但结果通知申请人失败，applyId={}", apply.getApplyId(), e);
			}
			try {
				messageHandle.sendMsg(resolverUpdate);
			} catch (Exception e) {
				logger.warn("申请已处理，但未能同步刷新处理方，applyId={}", apply.getApplyId(), e);
			}
		});
	}

	private void createFriendSessionAndGreeting(UserContactApply apply) {
		String senderId = apply.getApplyUserId();
		String receiverId = apply.getReceiveUserId();
		String sessionId = StringTools.getChatSessionIdUser(new String[]{senderId, receiverId});
		String greetingContent = StringTools.isEmpty(apply.getApplyInfo()) ? "你好，很高兴认识你" : apply.getApplyInfo();
		UserInfo sender = this.userInfoMapper.selectByUserId(senderId);
		UserInfo receiver = this.userInfoMapper.selectByUserId(receiverId);
		if (sender == null || receiver == null) {
			throw new BusinessException("联系人不存在，无法创建会话");
		}

		long sendTime = System.currentTimeMillis();
		ChatSession chatSession = new ChatSession();
		chatSession.setSessionId(sessionId);
		chatSession.setLastMessage(greetingContent);
		chatSession.setLastReceiveTime(sendTime);
		this.chatSessionMapper.insertOrUpdate(chatSession);

		ChatSessionUser senderSession = new ChatSessionUser();
		senderSession.setUserId(senderId);
		senderSession.setContactId(receiverId);
		senderSession.setSessionId(sessionId);
		senderSession.setContactName(receiver.getNickName());

		ChatSessionUser receiverSession = new ChatSessionUser();
		receiverSession.setUserId(receiverId);
		receiverSession.setContactId(senderId);
		receiverSession.setSessionId(sessionId);
		receiverSession.setContactName(sender.getNickName());
		this.chatSessionUserMapper.insertOrUpdateBatch(Arrays.asList(senderSession, receiverSession));

		ChatMessage greeting = ChatMessage.builder()
				.sessionId(sessionId)
				.sendUserId(senderId)
				.sendUserNickName(sender.getNickName())
				.messageType(MessageTypeEnum.ADD_FRIEND.getType())
				.sendTime(sendTime)
				.messageContent(greetingContent)
				.contactId(receiverId)
				.contactType(UserContactTypeEnum.USER.getType())
				.status(MessageStatusEnum.SENDED.getStatus())
				.build();
		this.chatMessageMapper.insert(greeting);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					redisComponent.addUser(senderId, receiverId);
					redisComponent.addUser(receiverId, senderId);
				} catch (Exception e) {
					logger.warn("好友已建立，但更新 WebSocket 联系人缓存失败，senderId={}, receiverId={}", senderId, receiverId, e);
				}

				MessageSendDto receiverEvent = CopyUtils.copy(greeting, MessageSendDto.class);
				receiverEvent.setExtendData(receiverSession);
				try {
					messageHandle.sendMsg(receiverEvent);
				} catch (Exception e) {
					logger.warn("好友欢迎消息通知接收方失败，messageId={}", greeting.getMessageId(), e);
				}

				MessageSendDto senderEvent = CopyUtils.copy(greeting, MessageSendDto.class);
				senderEvent.setContactId(senderId);
				senderEvent.setMessageType(MessageTypeEnum.ADD_FRIEND_SELF.getType());
				senderEvent.setExtendData(senderSession);
				try {
					messageHandle.sendMsg(senderEvent);
				} catch (Exception e) {
					logger.warn("好友欢迎消息通知发送方失败，messageId={}", greeting.getMessageId(), e);
				}
			}
		});
	}

	private void createGroupSessionAndGreeting(UserContactApply apply) {
		createGroupSessionAndGreeting(apply.getApplyUserId(), apply.getContactId());
	}

	private void createGroupSessionAndGreeting(String userId, String groupId) {
		GroupInfo group = this.groupInfoMapper.selectByGroupId(groupId);
		UserInfo member = this.userInfoMapper.selectByUserId(userId);
		if (group == null || member == null) {
			throw new BusinessException("群组或用户不存在，无法创建会话");
		}

		long sendTime = System.currentTimeMillis();
		String sessionId = StringTools.getChatSessionIdGroup(groupId);
		String content = String.format(MessageTypeEnum.ADD_GROUP.getInitMessage(), member.getNickName());
		ChatSession session = new ChatSession();
		session.setSessionId(sessionId);
		session.setLastMessage(content);
		session.setLastReceiveTime(sendTime);
		this.chatSessionMapper.insertOrUpdate(session);

		UserContactQuery membersQuery = new UserContactQuery();
		membersQuery.setContactId(groupId);
		membersQuery.setContactType(UserContactTypeEnum.Group.getType());
		membersQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
		ChatSessionUser newMemberSession = ChatSessionUser.builder()
				.userId(userId)
				.contactId(groupId)
				.sessionId(sessionId)
				.contactName(group.getGroupName())
				.memberCount(this.userContactMapper.selectCount(membersQuery))
				.build();
		this.chatSessionUserMapper.insertOrUpdate(newMemberSession);

		ChatMessage message = ChatMessage.builder()
				.sessionId(sessionId)
				.messageType(MessageTypeEnum.ADD_GROUP.getType())
				.messageContent(content)
				.sendUserId(userId)
				.sendUserNickName(member.getNickName())
				.sendTime(sendTime)
				.contactId(groupId)
				.contactType(UserContactTypeEnum.Group.getType())
				.status(MessageStatusEnum.SENDED.getStatus())
				.build();
		this.chatMessageMapper.insert(message);

		MessageSendDto event = CopyUtils.copy(message, MessageSendDto.class);
		event.setExtendData(newMemberSession);
		afterCommit(() -> {
			try {
				redisComponent.addUser(userId, groupId);
			} catch (Exception e) {
				logger.warn("已加入群聊，但更新 Redis 联系人缓存失败，userId={}, groupId={}", userId, groupId, e);
			}
			try {
				channelContextUtils.addUser2Group(userId, groupId);
			} catch (Exception e) {
				logger.warn("已加入群聊，但更新本机群组频道失败，userId={}, groupId={}", userId, groupId, e);
			}
			try {
				messageHandle.sendMsg(event);
			} catch (Exception e) {
				logger.warn("群成员已加入，但实时通知失败，userId={}, groupId={}", userId, groupId, e);
			}
		});
	}

	private void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
	}

	@Override
	public void addContact(String applyUserId, String receiveUserID, String ContactId, Integer ContactType, String ApplyInfo) {
		if (UserContactTypeEnum.Group.getType().equals(ContactType)) {
			GroupInfo group = this.groupInfoMapper.selectByGroupId(ContactId);
			if (group == null || GroupInfoStatusEnum.DisSolution.getStatus().equals(group.getStatus())) {
				throw new BusinessException("群组不存在或已解散");
			}
			UserContact existing = this.userContactMapper.selectByUserIdAndContactId(applyUserId, ContactId);
			if (existing != null && UserContactStatusEnum.FRIEND.getStatus().equals(existing.getStatus())) {
				throw new BusinessException("已经在群组中");
			}
			SysSettingDto sysSettingDTO = redisComponent.getSysSettingDTO();
			UserContactQuery query = new UserContactQuery();
			query.setContactId(ContactId);
			query.setContactType(UserContactTypeEnum.Group.getType());
			query.setStatus(UserContactStatusEnum.FRIEND.getStatus());
			Integer count = this.userContactMapper.selectCount(query);

			if (sysSettingDTO.getMaxGroupMemberCount() != null && count >= sysSettingDTO.getMaxGroupMemberCount()) {
				throw new BusinessException("群聊人数已满");
			}
		}
		UserContact contact = this.userContactMapper.selectByUserIdAndContactId(applyUserId, ContactId);
		if (UserContactTypeEnum.USER.getType().equals(ContactType)) {
			if (contact != null && contact.getStatus().equals(UserContactStatusEnum.BLACKLIST_BE.getStatus())) {
				throw new BusinessException("已被拉黑");
			}
		}

		List<UserContact> contacts = new ArrayList<>();
		Date cur = new Date();
		if (contact == null) {
			contact = new UserContact();
		}
		contact.setUserId(applyUserId);
		contact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
		contact.setContactType(ContactType);
		contact.setLastUpdateTime(cur);
		contact.setCreateTime(cur);
		contact.setContactId(ContactId);
		contacts.add(contact);

		if (UserContactTypeEnum.USER.getType().equals(ContactType)) {
			contact = new UserContact();
			contact.setUserId(ContactId);
			contact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
			contact.setContactType(ContactType);
			contact.setLastUpdateTime(cur);
			contact.setCreateTime(cur);
			contact.setContactId(applyUserId);
			contacts.add(contact);
		}
		this.userContactMapper.insertOrUpdateBatch(contacts);
	}

	public ContactInfoVO loadContact(String UserID, UserContactTypeEnum ContactType) {
		ContactInfoVO ret = new ContactInfoVO();

		UserContactQuery query = new UserContactQuery();
		query.setUserId(UserID);
		query.setContactType(ContactType.getType());
		query.setStatus(UserContactStatusEnum.FRIEND.getStatus());
		if (ContactType.equals(UserContactTypeEnum.USER)) {
			query.setQueryUserInfo(true);
		}
		List<UserContact> userContacts = this.userContactMapper.selectList(query);

		switch (ContactType) {
			case USER:
				ret.setFriendUserInfo(userContacts);
				ret.setContactType(ContactType.getType());
				break;
			case Group:
				ret.setContactType(ContactType.getType());
				if (userContacts == null || userContacts.isEmpty()) {
					ret.setGroupInfos(new ArrayList<>());
					break;
				}

				List<String> groupIds = new ArrayList<>(new LinkedHashSet<>());
				for (UserContact info : userContacts) {
					groupIds.add(info.getContactId());
				}

				GroupInfoQuery groupQuery = new GroupInfoQuery();
				groupQuery.setGroupIdList(groupIds);
				groupQuery.setStatus(GroupInfoStatusEnum.Normal.getStatus());
				List<GroupInfo> groupInfos = this.groupInfoMapper.selectList(groupQuery);
				Map<String, GroupInfo> groupInfoMap = new HashMap<>();
				for (GroupInfo groupInfo : groupInfos) {
					groupInfoMap.put(groupInfo.getGroupId(), groupInfo);
				}

				UserContactQuery groupUserQuery = new UserContactQuery();
				groupUserQuery.setContactType(ContactType.getType());
				groupUserQuery.setContactIdList(groupIds);
				groupUserQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
				groupUserQuery.setQueryUserInfo(true);
				groupUserQuery.setQueryUserInfoByUserId(true);
				List<UserContact> groupUserInfos = this.userContactMapper.selectList(groupUserQuery);
				Map<String, List<UserContact>> groupMemberMap = new HashMap<>();
				for (UserContact member : groupUserInfos) {
					groupMemberMap.computeIfAbsent(member.getContactId(), key -> new ArrayList<>()).add(member);
				}

				List<GroupInfoVO> gpinfos = new ArrayList<>();
				for (UserContact info : userContacts) {
					GroupInfo groupInfo = groupInfoMap.get(info.getContactId());
					if (groupInfo == null) {
						continue;
					}
					GroupInfoVO groupinfoVO = new GroupInfoVO();
					groupinfoVO.setGroupInfo(groupInfo);
					groupinfoVO.setUserContactList(groupMemberMap.getOrDefault(info.getContactId(), Collections.emptyList()));
					gpinfos.add(groupinfoVO);
				}
				ret.setGroupInfos(gpinfos);
				break;
		}

		return ret;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteContact(String UserID, String ContactID, UserContactStatusEnum userContactStatusEnum) {
		if (ContactID == null || ContactID.isEmpty()) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		UserContact contact = this.userContactMapper.selectByUserIdAndContactId(UserID, ContactID);
		UserContact contact2 = this.userContactMapper.selectByUserIdAndContactId(ContactID, UserID);
		if (contact == null || !contact.getStatus().equals(UserContactStatusEnum.FRIEND.getStatus())) {
			throw new BusinessException("不是好友");
		}
		if (!contact2.getStatus().equals(UserContactStatusEnum.FRIEND.getStatus())) {
			throw new BusinessException("不是好友");
		}

		contact.setLastUpdateTime(new Date());
		contact.setStatus(userContactStatusEnum.getStatus());

		if (userContactStatusEnum.equals(UserContactStatusEnum.DEL)) {
			contact2.setStatus(UserContactStatusEnum.DEL_BE.getStatus());
		} else {
			contact2.setStatus(UserContactStatusEnum.BLACKLIST_BE.getStatus());
		}
		contact2.setLastUpdateTime(new Date());

		this.userContactMapper.updateByUserIdAndContactId(contact, UserID, ContactID);
		this.userContactMapper.updateByUserIdAndContactId(contact2, ContactID, UserID);

		// TODO: 发送消息刷新页面
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void addContact4Robot(String userId) {
		Date curDate = new Date();
		SysSettingDto sysSettingDto = redisComponent.getSysSettingDTO();
		String robotId = sysSettingDto.getRobotUid();
		String msg = sysSettingDto.getRobotWelcome();
		String name = sysSettingDto.getRobotNickName();

		// 添加机器人
		UserContact userContact = new UserContact();
		userContact.setUserId(userId);
		userContact.setContactId(robotId);
		userContact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
		userContact.setContactType(UserContactTypeEnum.USER.getType());
		userContact.setCreateTime(curDate);
		this.userContactMapper.insert(userContact);
		// 添加会话记录
		String sessionId = StringTools.getChatSessionIdUser(new String[] {userId, robotId});
		ChatSession chatSession = new ChatSession();
		chatSession.setSessionId(sessionId);
		chatSession.setLastMessage(msg);
		chatSession.setLastReceiveTime(curDate.getTime());
		this.chatSessionMapper.insert(chatSession);
		// 添加会话人信息
		ChatSessionUser chatSessionUser = new ChatSessionUser();
		chatSessionUser.setSessionId(sessionId);
		chatSessionUser.setUserId(userId);
		chatSessionUser.setContactName(name);
		chatSessionUser.setContactId(robotId);
		this.chatSessionUserMapper.insert(chatSessionUser);
		// 添加聊天消息
		ChatMessage cmsg = new ChatMessage();
		cmsg.setSessionId(sessionId);
		cmsg.setMessageType(MessageTypeEnum.CHAT.getType());
		cmsg.setMessageContent(msg);
		cmsg.setSendUserId(robotId);
		cmsg.setContactId(userId);
		cmsg.setSendUserNickName(name);
		cmsg.setStatus(MessageStatusEnum.SENDED.getStatus());
		cmsg.setContactType(UserContactTypeEnum.USER.getType());
		cmsg.setSendTime(curDate.getTime());
		this.chatMessageMapper.insert(cmsg);
	}
}
