package com.easychat.service;

import java.util.List;

import com.easychat.entity.enums.UserContactStatusEnum;
import com.easychat.entity.enums.UserContactTypeEnum;
import com.easychat.entity.po.UserInfo;
import com.easychat.entity.query.UserContactQuery;
import com.easychat.entity.po.UserContact;
import com.easychat.entity.vo.ContactInfoVO;
import com.easychat.entity.vo.PaginationResultVO;
import com.easychat.entity.vo.SearchVo;


/**
 * 联系人 业务接口
 */
public interface UserContactService {

	/**
	 * 根据条件查询列表
	 */
	List<UserContact> findListByParam(UserContactQuery param);

	/**
	 * 根据条件查询列表
	 */
	Integer findCountByParam(UserContactQuery param);

	/**
	 * 分页查询
	 */
	PaginationResultVO<UserContact> findListByPage(UserContactQuery param);

	/**
	 * 新增
	 */
	Integer add(UserContact bean);

	/**
	 * 批量新增
	 */
	Integer addBatch(List<UserContact> listBean);

	/**
	 * 批量新增/修改
	 */
	Integer addOrUpdateBatch(List<UserContact> listBean);

	/**
	 * 多条件更新
	 */
	Integer updateByParam(UserContact bean,UserContactQuery param);

	/**
	 * 多条件删除
	 */
	Integer deleteByParam(UserContactQuery param);

	/**
	 * 根据UserIdAndContactId查询对象
	 */
	UserContact getUserContactByUserIdAndContactId(String userId,String contactId);


	/**
	 * 根据UserIdAndContactId修改
	 */
	Integer updateUserContactByUserIdAndContactId(UserContact bean,String userId,String contactId);


	/**
	 * 根据UserIdAndContactId删除
	 */
	Integer deleteUserContactByUserIdAndContactId(String userId,String contactId);

	SearchVo searchFriends(String UserId, String ContactId);

	boolean applyAdd(String UserId, String ContactId, UserContactTypeEnum ContactType, String ApplyInfo);

	void SolveApply(String UserId, String ContactId, Integer accept);

	void addContact(String applyUserId, String receiveUserID, String ContactId, Integer ContactType, String ApplyInfo);

	ContactInfoVO loadContact(String UserID, UserContactTypeEnum ContactType);

	void deleteContact(String UserId, String ContactId, UserContactStatusEnum userContactStatusEnum);

	void addContact4Robot(String userId);

}
