package com.easychat.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

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
import org.apache.catalina.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

	private static final Logger logger = LoggerFactory.getLogger(UserInfoServiceImpl.class);
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

		UserContact Info = this.userContactMapper.selectByUserIdAndContactId(UserId, ContactId);
		if (Info == null) {
			throw new BusinessException("查询不到群组");
		}

		Integer type = Info.getContactType();
		SearchVo result = new SearchVo();
		List<UserInfo> userInfoList = new ArrayList<>();
		if (type.equals(UserContactTypeEnum.USER.getType())) { // 查询单一用户
			UserInfo userInfo = this.userInfoMapper.selectByUserId(ContactId);
			userInfoList.add(userInfo);
		} else { // 查询群组信息
			GroupInfo groupInfo = this.groupInfoMapper.selectByGroupId(ContactId);
			if (groupInfo == null) {
				throw new BusinessException("群组不存在");
			}
			UserContactQuery query = new UserContactQuery();
			query.setContactId(ContactId);
			query.setContactType(UserContactTypeEnum.Group.getType());
			List<UserContact> infos = this.userContactMapper.selectList(query);
			for (UserContact info : infos) {
				UserInfo userInfo = this.userInfoMapper.selectByUserId(info.getUserId());
				userInfoList.add(userInfo);
			}
			result.setGroupInfo(groupInfo);
		}
		result.setUserInfoList(userInfoList);
		return result;
	}

	@Override
	public void applyAdd(String UserId, String ContactId, UserContactTypeEnum ContactType, String ApplyInfo) {
		if (ContactType == null) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		UserContact contact = this.userContactMapper.selectByUserIdAndContactId(UserId, ContactId);
		if (contact != null && contact.getStatus().equals(UserContactStatusEnum.FRIEND.getStatus())) {
			throw new BusinessException("已经是好友或者已经在群组中");
		}
		if (contact != null && contact.getStatus().equals(UserContactStatusEnum.BLACKLIST_BE.getStatus())) {
			throw new BusinessException("被拉黑");
		}

		UserContactApply apply = this.userContactApplyMapper.selectByApplyUserIdAndReceiveUserIdAndContactId(UserId, ContactId, ContactId);
		boolean needToSendWs = (apply == null || !apply.getStatus().equals(UserContactApplyEnum.Progress.getStatus()));
		if (apply != null) {
			apply.setLastApplyTime(System.currentTimeMillis());
			apply.setStatus(UserContactApplyEnum.Progress.getStatus());
			apply.setApplyInfo(ApplyInfo);
			this.userContactApplyMapper.updateByApplyId(apply, apply.getApplyId());
		} else {
			String ReceiveId = ContactId;
			if (ContactType.equals(UserContactTypeEnum.Group)) {
				GroupInfo groupinfo = this.groupInfoMapper.selectByGroupId(ContactId);
				ReceiveId = groupinfo.getGroupOwnerId();
			}
			apply = new UserContactApply();
			apply.setApplyUserId(UserId);
			apply.setReceiveUserId(ReceiveId);
			apply.setContactType(UserContactTypeEnum.USER.getType());
			apply.setApplyInfo(ApplyInfo);
			apply.setContactId(ContactId);
			apply.setLastApplyTime((new Date()).getTime());
			apply.setStatus(UserContactApplyEnum.Progress.getStatus());

			this.userContactApplyMapper.insert(apply);
		}

		if (needToSendWs) {

		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void SolveApply(String UserId, String ContactId, Integer accept) {
		if (ContactId == null) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		UserContactApply apply = this.userContactApplyMapper.selectByContactId(ContactId);
		if (apply == null || !apply.getStatus().equals(UserContactApplyEnum.Progress.getStatus())) {
			throw new BusinessException("申请已经失效");
		}
		apply.setStatus(accept);
		apply.setLastApplyTime((new Date()).getTime());

		UserContactApplyQuery query = new UserContactApplyQuery();
		query.setApplyId(apply.getApplyId());
		query.setStatus(UserContactApplyEnum.Progress.getStatus());
		this.userContactApplyMapper.updateByParam(apply, query);

		if (accept.equals(UserContactApplyEnum.Accept.getStatus())) {
			this.addContact(apply.getApplyUserId(), apply.getReceiveUserId(), apply.getContactId(), apply.getContactType(), apply.getApplyInfo());
		}

		if (accept.equals(UserContactApplyEnum.Black.getStatus())) {
			UserContact contact = new UserContact();
			contact.setUserId(UserId);
			contact.setContactType(apply.getContactType());
			contact.setContactId(apply.getReceiveUserId());
			contact.setLastUpdateTime(new Date());
			contact.setStatus(UserContactStatusEnum.BLACKLIST.getStatus());
			this.userContactMapper.insert(contact);
			logger.info("新加关系 : (" + contact.getUserId() + " : " + contact.getContactId() + ")");
		}
	}

	@Override
	public void addContact(String applyUserId, String receiveUserID, String ContactId, Integer ContactType, String ApplyInfo) {
		if (UserContactTypeEnum.Group.getType().equals(ContactType)) {
			SysSettingDto sysSettingDTO = redisComponent.getSysSettingDTO();
			UserContactQuery query = new UserContactQuery();
			query.setContactId(ContactId);
			query.setStatus(UserContactStatusEnum.FRIEND.getStatus());
			Integer count = this.userContactMapper.selectCount(query);

			if (count >= sysSettingDTO.getMaxGroupMemberCount()) {
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
				List<GroupInfo> groupInfos = this.groupInfoMapper.selectList(groupQuery);
				Map<String, GroupInfo> groupInfoMap = new HashMap<>();
				for (GroupInfo groupInfo : groupInfos) {
					groupInfoMap.put(groupInfo.getGroupId(), groupInfo);
				}

				UserContactQuery groupUserQuery = new UserContactQuery();
				groupUserQuery.setContactType(ContactType.getType());
				groupUserQuery.setContactIdList(groupIds);
				groupUserQuery.setQueryUserInfo(true);
				groupUserQuery.setQueryUserInfoByUserId(true);
				List<UserContact> groupUserInfos = this.userContactMapper.selectList(groupUserQuery);
				Map<String, List<UserContact>> groupMemberMap = new HashMap<>();
				for (UserContact member : groupUserInfos) {
					groupMemberMap.computeIfAbsent(member.getContactId(), key -> new ArrayList<>()).add(member);
				}

				List<GroupInfoVO> gpinfos = new ArrayList<>();
				for (UserContact info : userContacts) {
					GroupInfoVO groupinfoVO = new GroupInfoVO();
					groupinfoVO.setGroupInfo(groupInfoMap.get(info.getContactId()));
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
