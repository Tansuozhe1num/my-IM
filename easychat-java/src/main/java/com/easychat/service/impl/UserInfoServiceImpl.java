package com.easychat.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.easychat.entity.config.Appconfig;
import com.easychat.entity.constants.Constants;
import com.easychat.entity.dto.MessageSendDto;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.entity.enums.*;
import com.easychat.entity.po.*;
import com.easychat.entity.query.*;
import com.easychat.entity.vo.UserInfoVo;
import com.easychat.exception.BusinessException;
import com.easychat.mappers.*;
import com.easychat.redis.redisComponent;
import com.easychat.service.UserContactService;
import com.easychat.utils.CopyUtils;
import com.easychat.websocket.ChannelContextUtils;
import com.easychat.websocket.messageHandle;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.easychat.entity.vo.PaginationResultVO;
import com.easychat.service.UserInfoService;
import com.easychat.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


/**
 *  业务接口实现
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {

	@Resource
	private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	@Resource
	private UserInfoBeautyMapper<UserInfoBeauty, UserInfoBeautyQuery> userInfoBeautyMapper;

	@Resource
	private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

	@Resource
	private Appconfig appconfig;

	@Resource
	private UserContactService userContactService;

	@Resource
	private redisComponent redisComponent;

	@Resource
	private ChannelContextUtils channelContextUtils;

	@Resource
	private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

	@Resource
	private messageHandle messageHandle;

	private static final Logger logger = LoggerFactory.getLogger(UserInfoServiceImpl.class);

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<UserInfo> findListByParam(UserInfoQuery param) {
		return this.userInfoMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(UserInfoQuery param) {
		return this.userInfoMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<UserInfo> list = this.findListByParam(param);
		PaginationResultVO<UserInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(UserInfo bean) {
		return this.userInfoMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<UserInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userInfoMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<UserInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userInfoMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(UserInfo bean, UserInfoQuery param) {
		StringTools.checkParam(param);
		return this.userInfoMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(UserInfoQuery param) {
		StringTools.checkParam(param);
		return this.userInfoMapper.deleteByParam(param);
	}

	/**
	 * 根据UserId获取对象
	 */
	@Override
        public UserInfo getUserInfoByUserId(String userId) {
		return this.userInfoMapper.selectByUserId(userId);
	}

	/**
	 * 根据UserId修改
	 */
	@Override
        public Integer updateUserInfoByUserId(UserInfo bean, String userId) {
		return this.userInfoMapper.updateByUserId(bean, userId);
	}

	/**
	 * 根据UserId删除
	 */
	@Override
        public Integer deleteUserInfoByUserId(String userId) {
		return this.userInfoMapper.deleteByUserId(userId);
	}

	/**
	 * 根据Email获取对象
	 */
	@Override
	public UserInfo getUserInfoByEmail(String email) {
		return this.userInfoMapper.selectByEmail(email);
	}

	/**
	 * 根据Email修改
	 */
	@Override
	public Integer updateUserInfoByEmail(UserInfo bean, String email) {
		return this.userInfoMapper.updateByEmail(bean, email);
	}

	/**
	 * 根据Email删除
	 */
	@Override
	public Integer deleteUserInfoByEmail(String email) {
		return this.userInfoMapper.deleteByEmail(email);
	}

	@Transactional(rollbackFor = Exception.class)
	public void register(String email, String nickname, String password) {
		UserInfo userinfo = this.userInfoMapper.selectByEmail(email);
		if (userinfo != null) {
			throw new BusinessException("账号已经存在");
		}

		String userid = "U" + StringTools.generateSecureRandomString(8);

		UserInfoBeauty beautyuserinfo = this.userInfoBeautyMapper.selectByEmail(email);
		boolean isBeauty = beautyuserinfo != null && BeautyAccountStatusEnum.NO_USE.getStatus().equals(beautyuserinfo.getStatus());
		if (isBeauty) {
			userid = "U" + beautyuserinfo.getUserId();
			UserInfoBeauty updateinfo = new UserInfoBeauty();
			updateinfo.setStatus(BeautyAccountStatusEnum.USED.getStatus());
			this.userInfoBeautyMapper.updateByUserId(updateinfo, beautyuserinfo.getUserId());
		}

		userinfo = new UserInfo();
		userinfo.setUserId(userid);
		userinfo.setEmail(email);
		userinfo.setNickName(nickname);
		userinfo.setPassword(StringTools.encodeMD5(password));
		userinfo.setCreateTime(new Date());
		userinfo.setStatus(UserStatusEnum.Enable.getCode());

		this.userInfoMapper.insert(userinfo);

		userContactService.addContact4Robot(userid);
	}

	public UserInfoVo login(String email, String password) {
		UserInfo userinfo = this.userInfoMapper.selectByEmail(email);
		if (userinfo == null) {
			throw new BusinessException("账号不存在");
		}

		if (userinfo.getStatus() == UserStatusEnum.Disable.getCode()) {
			throw new BusinessException("账号被禁用");
		}

		// TODO: 查询我的群组
		// TODO: 查询我的联系人
		UserContactQuery contactQuery = new UserContactQuery();
		contactQuery.setUserId(userinfo.getUserId());
		contactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
		List<UserContact> userContacts = userContactMapper.selectList(contactQuery);
		List<String> contactIdList = userContacts.stream().map(UserContact::getContactId).collect(Collectors.toList());

		redisComponent.cleanUserContact(userinfo.getUserId());
		if (!contactIdList.isEmpty()) {
			redisComponent.addUserBatch(userinfo.getUserId(), contactIdList);
		}

		if (null != redisComponent.getUserHeartBeat(userinfo.getUserId())) {
			logger.info("用户已在别处登录");
			return null;
		}

		if (!userinfo.getPassword().equals(StringTools.encodeMD5(password))) {
			throw new BusinessException("密码不正确");
		}

		String token = StringTools.encodeMD5(userinfo.getUserId() + StringTools.generateSecureRandomString(20));

		TokenUserinfoDTO tokenUserinfoDTO = TokenUserinfoDTO.builder()
				.userId(userinfo.getUserId())
				.token(token)
				.nickname(userinfo.getNickName())
				.build();
		String[] emails = appconfig.getAdminEmails().split(",");
		if (!StringTools.isEmpty(userinfo.getEmail()) && ArrayUtils.contains(emails, userinfo.getEmail())) {
			tokenUserinfoDTO.setAdmin(true);
		}

		redisComponent.saveTokenUserInfoDTO(tokenUserinfoDTO);

		UserInfoVo userInfoVo = CopyUtils.copy(userinfo, UserInfoVo.class);
		userInfoVo.setAdmin(tokenUserinfoDTO.isAdmin());
		userInfoVo.setToken(tokenUserinfoDTO.getToken());
		return userInfoVo;
	}

	public void updatePasswd(String UserID, String passwd) {
		if (StringTools.isEmpty(passwd)) {
			throw new BusinessException("密码格式不好");
		}
		UserInfo update = new UserInfo();
		update.setPassword(StringTools.encodeMD5(passwd));
		if (this.userInfoMapper.selectByUserId(UserID) == null) {
			throw new BusinessException("用户不存在");
		}
		this.userInfoMapper.updateByUserId(update, UserID);
		channelContextUtils.outUser(UserID);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateUserInfo(String userId, UserInfo userInfo, MultipartFile avatar) throws IOException {
		if (StringTools.isEmpty(userId) || userInfo == null) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}
		UserInfo dbInfo = this.userInfoMapper.selectByUserId(userId);
		if (dbInfo == null) {
			throw new BusinessException("用户不存在");
		}

		UserInfo update = new UserInfo();
		String nickname = userInfo.getNickName();
		if (nickname != null) {
			nickname = nickname.trim();
			if (nickname.isEmpty() || nickname.length() > 40) {
				throw new BusinessException("昵称不能为空且不能超过40个字符");
			}
			update.setNickName(nickname);
		}
		update.setPersonalSignature(userInfo.getPersonalSignature());
		if (userInfo.getSex() != null) {
			if (userInfo.getSex() != 0 && userInfo.getSex() != 1) {
				throw new BusinessException(ResponseCodeEnum.CODE_600);
			}
			update.setSex(userInfo.getSex());
		}
		update.setAreaName(userInfo.getAreaName());
		update.setAreaCode(userInfo.getAreaCode());
		if (update.getNickName() == null && update.getPersonalSignature() == null && update.getSex() == null
				&& update.getAreaName() == null && update.getAreaCode() == null && (avatar == null || avatar.isEmpty())) {
			throw new BusinessException("没有可更新的资料");
		}

		if (avatar != null && !avatar.isEmpty()) {
			if (!"image/png".equalsIgnoreCase(avatar.getContentType())) {
				throw new BusinessException("头像请使用 PNG 图片");
			}
			File targetFolder = new File(appconfig.getProjectFolder() + Constants.FILE_PATH + Constants.AVATOR_FILE_PATH);
			if (!targetFolder.exists() && !targetFolder.mkdirs()) {
				throw new IOException("无法创建头像目录");
			}
			avatar.transferTo(new File(targetFolder, userId + Constants.IMAGE_SUFFER));
		}
		boolean hasProfileFields = update.getNickName() != null || update.getPersonalSignature() != null
				|| update.getSex() != null || update.getAreaName() != null || update.getAreaCode() != null;
		if (hasProfileFields) {
			this.userInfoMapper.updateByUserId(update, userId);
		}

		String newNickname = update.getNickName();
		if (newNickname != null && !Objects.equals(dbInfo.getNickName(), newNickname)) {
			ChatSessionUser sessionUpdate = ChatSessionUser.builder().contactName(newNickname).build();
			ChatSessionUserQuery sessionQuery = ChatSessionUserQuery.builder().contactId(userId).build();
			this.chatSessionUserMapper.updateByParam(sessionUpdate, sessionQuery);
		}

		Map<String, Object> profileUpdate = new HashMap<>();
		profileUpdate.put("userId", userId);
		if (newNickname != null) profileUpdate.put("nickName", newNickname);
		if (update.getPersonalSignature() != null) profileUpdate.put("personalSignature", update.getPersonalSignature());
		if (update.getSex() != null) profileUpdate.put("sex", update.getSex());
		if (update.getAreaName() != null) profileUpdate.put("areaName", update.getAreaName());
		if (update.getAreaCode() != null) profileUpdate.put("areaCode", update.getAreaCode());
		if (avatar != null && !avatar.isEmpty()) profileUpdate.put("avatarVersion", System.currentTimeMillis());

		UserContactQuery contactQuery = new UserContactQuery();
		contactQuery.setUserId(userId);
		contactQuery.setContactType(UserContactTypeEnum.USER.getType());
		contactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
		List<String> recipients = new ArrayList<>();
		recipients.add(userId);
		for (UserContact contact : userContactMapper.selectList(contactQuery)) {
			if (!StringTools.isEmpty(contact.getContactId())) recipients.add(contact.getContactId());
		}
		String displayNickname = newNickname == null ? dbInfo.getNickName() : newNickname;
		afterCommit(() -> recipients.forEach(recipient -> {
			MessageSendDto<Map<String, Object>> event = MessageSendDto.<Map<String, Object>>builder()
					.messageId(com.easychat.utils.IdGenerator.nextIdLong())
					.messageType(MessageTypeEnum.CONTACT_NAME_UPDATE.getType())
					.contactType(UserContactTypeEnum.USER.getType())
					.contactId(recipient)
					.sendUserId(userId)
					.sendUserNickName(displayNickname)
					.messageContent("个人资料已更新")
					.extendData(profileUpdate)
					.sendTime(System.currentTimeMillis())
					.build();
			try {
				messageHandle.sendMsg(event);
			} catch (Exception e) {
				logger.warn("个人资料已更新，但实时通知失败，userId={}, recipient={}", userId, recipient, e);
			}
		}));
	}

	private void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override public void afterCommit() { action.run(); }
			});
		} else {
			action.run();
		}
	}
}
