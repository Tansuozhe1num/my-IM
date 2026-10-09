package com.easychat.controller;

import com.easychat.entity.constants.Constants;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.entity.po.UserInfo;
import com.easychat.entity.vo.ResponseVO;
import com.easychat.entity.vo.UserInfoVo;
import com.easychat.exception.BusinessException;
import com.easychat.service.UserInfoService;
import com.easychat.utils.CopyUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.IOException;

@RestController
@RequestMapping("/user")
public class UserController extends ABaseController {

    @Resource
    private UserInfoService userInfoService;

    @RequestMapping("/getUserInfo")
    public ResponseVO getUserInfo(HttpServletRequest request) {
        TokenUserinfoDTO tokenUserinfoDTO = getTokenUserinfoDTO(request);
        if (tokenUserinfoDTO.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        UserInfo userInfoByUserId = userInfoService.getUserInfoByUserId(tokenUserinfoDTO.getUserId());
        UserInfoVo userInfoVo = CopyUtils.copy(userInfoByUserId, UserInfoVo.class);
        return getSuccessResponseVO(userInfoVo);
    }

    @RequestMapping("/updatepassword")
    public ResponseVO updatePassWord(HttpServletRequest request, @NotNull @Pattern(regexp = Constants.PASSWORDPATTERN) String password) {
         TokenUserinfoDTO tokenUserinfoDTO = getTokenUserinfoDTO(request);
         if (tokenUserinfoDTO.getUserId() == null) {
             throw new BusinessException("未登录");
         }
         this.userInfoService.updatePasswd(tokenUserinfoDTO.getUserId(), password);
         return getSuccessResponseVO(null);
    }

    @RequestMapping("/updateUserInfo")
    public ResponseVO updateUserInfo(HttpServletRequest request, @ModelAttribute UserInfo info,
                                     @RequestParam(value = "avator", required = false) MultipartFile avator) throws IOException {
        TokenUserinfoDTO tokenUserinfoDTO = getTokenUserinfoDTO(request);
        if (tokenUserinfoDTO.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        this.userInfoService.updateUserInfo(tokenUserinfoDTO.getUserId(), info, avator);
        return getSuccessResponseVO(null);
    }
}
