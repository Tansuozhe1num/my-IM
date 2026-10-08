package com.easychat.controller;

import com.easychat.annotation.GlobalInterceptor;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.entity.enums.ResponseCodeEnum;
import com.easychat.entity.enums.UserContactStatusEnum;
import com.easychat.entity.enums.UserContactTypeEnum;
import com.easychat.entity.po.GroupInfo;
import com.easychat.entity.po.UserContact;
import com.easychat.entity.query.UserContactQuery;
import com.easychat.entity.vo.GroupInfoVO;
import com.easychat.entity.vo.ResponseVO;
import com.easychat.exception.BusinessException;
import com.easychat.service.GroupInfoService;
import com.easychat.service.UserContactService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@RestController("groupInfoController")
@RequestMapping("/group")
@Validated
public class GroupInfoController extends ABaseController {

    @Resource
    private GroupInfoService groupInfoService;

    @Resource
    private UserContactService userContactService;

    @RequestMapping("/savegroup")
    @GlobalInterceptor
    public ResponseVO saveGroup(HttpServletRequest request, String groupId,
                                @NotEmpty String groupName,
                                String groupNotice,
                                @NotNull Integer joinType,
                                MultipartFile avaterfile,
                                MultipartFile avaterCover) throws IOException {

        TokenUserinfoDTO tokenUserinfoDTO = getTokenUserinfoDTO(request);
        GroupInfo info = new GroupInfo();
        info.setGroupId(groupId);
        info.setGroupName(groupName);
        info.setGroupNotice(groupNotice);
        info.setGroupOwnerId(tokenUserinfoDTO.getUserId());
        info.setCreateTime(new Date());
        info.setJoinType(joinType);
        this.groupInfoService.saveGroup(info, avaterfile, avaterCover);
        return getSuccessResponseVO(null);
    }

    @RequestMapping("/loadmygroup")
    @GlobalInterceptor
    public ResponseVO loadMyGroup(HttpServletRequest request) throws IOException {

        TokenUserinfoDTO tokenUserinfoDTO = getTokenUserinfoDTO(request);
        if (tokenUserinfoDTO.getUserId() == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
        List<GroupInfo> groupInfos = this.groupInfoService.loadMyGroup(tokenUserinfoDTO.getUserId());
        return getSuccessResponseVO(groupInfos);
    }

    @RequestMapping("/getgroupinfo")
    @GlobalInterceptor
    public ResponseVO getGroupInfo(HttpServletRequest request, @NotEmpty String groupId) throws IOException {

        TokenUserinfoDTO tokenUserinfoDTO = getTokenUserinfoDTO(request);
        if (tokenUserinfoDTO.getUserId() == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
        GroupInfo groupInfos = this.groupInfoService.getGroupInfo(tokenUserinfoDTO.getUserId(), groupId);
        return getSuccessResponseVO(groupInfos);
    }

    @RequestMapping("/getgroupinfobychat")
    @GlobalInterceptor
    public ResponseVO getGroupInfoByChat(HttpServletRequest request, @NotEmpty String groupId) throws IOException {
        TokenUserinfoDTO tokenUserinfoDTO = getTokenUserinfoDTO(request);
        if (tokenUserinfoDTO.getUserId() == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
        GroupInfo groupInfos = this.groupInfoService.getGroupInfo(tokenUserinfoDTO.getUserId(), groupId);

        UserContactQuery query = new UserContactQuery();
        query.setContactId(groupId);
        query.setContactType(UserContactTypeEnum.Group.getType());
        query.setQueryUserInfo(true);
        query.setQueryUserInfoByUserId(true);
        query.setOrderBy("create_time desc");
        query.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        List<UserContact> listByParam = this.userContactService.findListByParam(query);

        GroupInfoVO info = new GroupInfoVO();
        info.setGroupInfo(groupInfos);
        info.setUserContactList(listByParam);
        return getSuccessResponseVO(info);
    }
}
