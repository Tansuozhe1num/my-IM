package com.easychat.controller;

import com.easychat.annotation.GlobalInterceptor;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.entity.enums.ResponseCodeEnum;
import com.easychat.entity.enums.UserContactApplyEnum;
import com.easychat.entity.enums.UserContactStatusEnum;
import com.easychat.entity.enums.UserContactTypeEnum;
import com.easychat.entity.po.UserContact;
import com.easychat.entity.po.UserContactApply;
import com.easychat.entity.po.UserInfo;
import com.easychat.entity.query.UserContactApplyQuery;
import com.easychat.entity.query.UserContactQuery;
import com.easychat.entity.vo.ContactInfoVO;
import com.easychat.entity.vo.ResponseVO;
import com.easychat.entity.vo.SearchVo;
import com.easychat.exception.BusinessException;
import com.easychat.service.UserContactApplyService;
import com.easychat.service.UserContactService;
import com.easychat.service.UserInfoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping("/contact")
public class UserContactController extends ABaseController {

    @Resource
    private UserContactService userContactService;

    @Resource
    private UserContactApplyService userContactApplyService;

    @Resource
    private UserInfoService userInfoService;

    @RequestMapping("/searchfriends")
    @GlobalInterceptor
    public ResponseVO SearchFriends(HttpServletRequest request, @NotNull String ContactId) {
        TokenUserinfoDTO userinfoDTO = getTokenUserinfoDTO(request);
        if (userinfoDTO == null || userinfoDTO.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        SearchVo searchVo = this.userContactService.searchFriends(userinfoDTO.getUserId(), ContactId);
        return getSuccessResponseVO(searchVo);
    }

    @RequestMapping("/applyAdd")
    @GlobalInterceptor
    public ResponseVO applyAdd(HttpServletRequest request, @NotNull String ContactId, @NotNull Integer ContactType, String ApplyInfo) {
        TokenUserinfoDTO userinfoDTO = getTokenUserinfoDTO(request);
        if (userinfoDTO == null || userinfoDTO.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        UserContactTypeEnum type = UserContactTypeEnum.getByType(ContactType);
        ApplyInfo = ApplyInfo == null ? "申请添加" : ApplyInfo;
        this.userContactService.applyAdd(userinfoDTO.getUserId(), ContactId, type, ApplyInfo);
        return getSuccessResponseVO("");
    }

    @RequestMapping("/solveApply")
    @GlobalInterceptor
    public ResponseVO SolveApply(HttpServletRequest request, @NotNull String ContactId, @NotNull Integer accept) {
        TokenUserinfoDTO userinfoDTO = getTokenUserinfoDTO(request);
        if (userinfoDTO == null || userinfoDTO.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        this.userContactService.SolveApply(userinfoDTO.getUserId(), ContactId, accept);
        return getSuccessResponseVO("");
    }

    @RequestMapping("/loadContact")
    @GlobalInterceptor
    public ResponseVO loadContact(HttpServletRequest request, Integer ContactType) {
        TokenUserinfoDTO userinfoDTO = getTokenUserinfoDTO(request);
        if (userinfoDTO == null || userinfoDTO.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        UserContactTypeEnum type = UserContactTypeEnum.getByType(ContactType);
        ContactInfoVO listByParam = this.userContactService.loadContact(userinfoDTO.getUserId(), UserContactTypeEnum.getByType(ContactType));
        return getSuccessResponseVO(listByParam);
    }

    @RequestMapping("/loadApply")
    @GlobalInterceptor
    public ResponseVO loadApply(HttpServletRequest request) {
        TokenUserinfoDTO userinfoDTO = getTokenUserinfoDTO(request);
        if (userinfoDTO == null || userinfoDTO.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        UserContactApplyQuery query = new UserContactApplyQuery();
        query.setReceiveUserId(userinfoDTO.getUserId());
        query.setStatus(UserContactApplyEnum.Progress.getStatus());
        List<UserContactApply> listByParam = this.userContactApplyService.findListByParam(query);
        return getSuccessResponseVO(listByParam);
    }

    @RequestMapping("/deleteContact")
    @GlobalInterceptor
    public ResponseVO deleteContact(HttpServletRequest request, @NotNull String ContactId, Integer userContactstatus) {
        TokenUserinfoDTO userinfoDTO = getTokenUserinfoDTO(request);
        if (userinfoDTO == null || userinfoDTO.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        if (userContactstatus != 2 && userContactstatus != 4) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserContactStatusEnum byStatus = UserContactStatusEnum.getByStatus(userContactstatus);
        this.userContactService.deleteContact(userinfoDTO.getUserId(), ContactId, byStatus);
        return getSuccessResponseVO(null);
    }


}
