package com.easychat.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.xml.crypto.Data;

import com.easychat.entity.config.Appconfig;
import com.easychat.entity.constants.Constants;
import com.easychat.entity.dto.SysSettingDto;
import com.easychat.entity.enums.*;
import com.easychat.entity.po.UserContact;
import com.easychat.entity.query.UserContactQuery;
import com.easychat.exception.BusinessException;
import com.easychat.mappers.UserContactMapper;
import org.springframework.stereotype.Service;

import com.easychat.entity.query.GroupInfoQuery;
import com.easychat.entity.po.GroupInfo;
import com.easychat.entity.vo.PaginationResultVO;
import com.easychat.entity.query.SimplePage;
import com.easychat.mappers.GroupInfoMapper;
import com.easychat.service.GroupInfoService;
import com.easychat.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.easychat.redis.redisComponent;


/**
 *  业务接口实现
 */
@Service("groupInfoService")
public class GroupInfoServiceImpl implements GroupInfoService {

	@Resource
	private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;

	@Resource
	private redisComponent redisComponent;

	@Resource
	private UserContactMapper userContactMapper;

	@Resource
	private Appconfig appconfig;
	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<GroupInfo> findListByParam(GroupInfoQuery param) {
		return this.groupInfoMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(GroupInfoQuery param) {
		return this.groupInfoMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<GroupInfo> findListByPage(GroupInfoQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<GroupInfo> list = this.findListByParam(param);
		PaginationResultVO<GroupInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(GroupInfo bean) {
		return this.groupInfoMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<GroupInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.groupInfoMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<GroupInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.groupInfoMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(GroupInfo bean, GroupInfoQuery param) {
		StringTools.checkParam(param);
		return this.groupInfoMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(GroupInfoQuery param) {
		StringTools.checkParam(param);
		return this.groupInfoMapper.deleteByParam(param);
	}

	/**
	 * 根据GroupId获取对象
	 */
	@Override
	public GroupInfo getGroupInfoByGroupId(String groupId) {
		return this.groupInfoMapper.selectByGroupId(groupId);
	}

	/**
	 * 根据GroupId修改
	 */
	@Override
	public Integer updateGroupInfoByGroupId(GroupInfo bean, String groupId) {
		return this.groupInfoMapper.updateByGroupId(bean, groupId);
	}

	/**
	 * 根据GroupId删除
	 */
	@Override
	public Integer deleteGroupInfoByGroupId(String groupId) {
		return this.groupInfoMapper.deleteByGroupId(groupId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveGroup(GroupInfo groupInfo, MultipartFile avator, MultipartFile avatorCover) throws IOException {
		// 群组未存在
		if (StringTools.isEmpty(groupInfo.getGroupId())) {
			GroupInfoQuery query = new GroupInfoQuery();
			query.setGroupOwnerId(groupInfo.getGroupOwnerId());
			Integer cnt = this.groupInfoMapper.selectCount(query);
			SysSettingDto sysSettingDTO = redisComponent.getSysSettingDTO();

			if (cnt >= sysSettingDTO.getMaxGroupCount()) {
				throw new BusinessException("创建群组过多, 最多" + sysSettingDTO.getMaxGroupCount() + "个群组");
			}

			if (avator == null) {
				throw new BusinessException(ResponseCodeEnum.CODE_600);
			}

			groupInfo.setCreateTime(new Date());
			groupInfo.setGroupId("G" + StringTools.generateSecureRandomString(8));
			this.groupInfoMapper.insert(groupInfo);

			UserContact contact = new UserContact();
			contact.setUserId(groupInfo.getGroupOwnerId());
			contact.setStatus(UserContactStatusEnum.FRIEND.getStatus());
			contact.setContactId(groupInfo.getGroupId());
			contact.setContactType(UserContactTypeEnum.Group.getType());
			contact.setCreateTime(new Date());
			contact.setLastUpdateTime(new Date());
			this.userContactMapper.insert(contact);

			// TODO 创建对话， 发送消息

		} else {
			GroupInfo curGroupinfo = this.groupInfoMapper.selectByGroupId(groupInfo.getGroupId());
			if (!curGroupinfo.getGroupOwnerId().equals(groupInfo.getGroupOwnerId())) {
				throw new BusinessException("修改者不是群主");
			}
			this.groupInfoMapper.updateByGroupId(groupInfo, groupInfo.getGroupId());
			// TODO 更新相关表冗余信息

			// TODO: 修改群昵称发送ws消息
		}

		if (avator == null) {
			return;
		}

		String baseFolder = appconfig.getProjectFolder() + Constants.FILE_PATH;
		File targetFileFolder = new File(baseFolder + Constants.AVATOR_FILE_PATH);
		if (!targetFileFolder.exists()) {
			targetFileFolder.mkdirs();
		}
		String filePath = targetFileFolder.getPath() + "/" + groupInfo.getGroupId() + Constants.IMAGE_SUFFER;
		avator.transferTo(new File(filePath));
		if (avatorCover != null) {
			avatorCover.transferTo(new File(filePath + Constants.COVER_IMAGE_SUFFER));
		}
	}

	@Override
	public List<GroupInfo> loadMyGroup(String UserID) {
		if (UserID == null) {
			throw new BusinessException(ResponseCodeEnum.CODE_600);
		}

		List<GroupInfo> groupInfo = this.groupInfoMapper.selectByGroupOwnerId(UserID);
		return groupInfo;
	}

	@Override
	public GroupInfo getGroupInfo(String UserId, String groupId) {
		GroupInfo groupInfo = getGroupInfoNormal(UserId, groupId);
		UserContactQuery query = new UserContactQuery();
		query.setContactId(groupId);
		Integer cnt = this.userContactMapper.selectCount(query);
		groupInfo.setMemberCount(cnt);
		return groupInfo;
	}


	public GroupInfo getGroupInfoNormal(String UserId, String groupId) {
		UserContact contact = (UserContact) this.userContactMapper.selectByUserIdAndContactId(UserId, groupId);
		if (contact == null) {
			throw new BusinessException("非本群组用户");
		}
		GroupInfo groupInfo = this.groupInfoMapper.selectByGroupId(groupId);
		if (null == groupInfo || groupInfo.getStatus().equals(GroupInfoStatusEnum.DisSolution.getStatus())) {
			throw new BusinessException("群聊已解散");
		}
		return groupInfo;
	}
}