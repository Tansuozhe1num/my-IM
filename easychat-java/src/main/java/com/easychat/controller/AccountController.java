package com.easychat.controller;

import com.easychat.annotation.GlobalInterceptor;
import com.easychat.entity.constants.Constants;
import com.easychat.entity.dto.MessageSendDto;
import com.easychat.entity.dto.SysSettingDto;

import com.easychat.entity.vo.ResponseVO;
import com.easychat.entity.vo.UserInfoVo;
import com.easychat.exception.BusinessException;
import com.easychat.redis.redisComponent;
import com.easychat.redis.redisUtils;
import com.easychat.service.UserInfoService;

import com.easychat.websocket.messageHandle;
import com.wf.captcha.ArithmeticCaptcha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequestMapping("/account")
@RestController("accountController")
@Validated
public class AccountController extends ABaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    @Resource
    private redisUtils redisUtils;

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private redisComponent redisComponent;

    @RequestMapping("/checkcode")
    public ResponseVO CheckCode() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(100, 43);
        String shizi = captcha.getArithmeticString();
        String code = captcha.text();
        String base64 = captcha.toBase64();
        String mark = UUID.randomUUID().toString();

        logger.info("需要的验证码 : " + shizi + " = " + code);
        redisUtils.setex(Constants.REDIS_CHECK_KEY_CODE + mark, code, 60 * 5);
        Map<String, String> mp = new HashMap<>();
        mp.put("checkCode", base64);
        mp.put("checkCodeKey", mark);

        return getSuccessResponseVO(mp);
    }

    @RequestMapping("/register")
    public ResponseVO register(@NotEmpty String checkcodekey,
                                @NotEmpty @Email String email,
                                @NotEmpty String nickname,
                                @NotEmpty String password,
                                @NotEmpty String checkCode) {

        String key = Constants.REDIS_CHECK_KEY_CODE + checkcodekey;
        try {
            if (!checkCode.equalsIgnoreCase((String) redisUtils.get(key))) {
                throw new BusinessException("图片验证码验证失败");
            }

            userInfoService.register(email, nickname, password);
        } finally {
            redisUtils.delete(key);
        }

        return getSuccessResponseVO("");
    }

    @RequestMapping("/login")
    public ResponseVO login(@NotEmpty String checkcodekey,
                               @NotEmpty @Email String email,
                               @NotEmpty String password,
                               @NotEmpty String checkCode) {

        String key = Constants.REDIS_CHECK_KEY_CODE + checkcodekey;
        try {
            if (!checkCode.equalsIgnoreCase((String) redisUtils.get(key))) {
                throw new BusinessException("图片验证码验证失败");
            }
            UserInfoVo userInfoVo = userInfoService.login(email, password);
            return getSuccessResponseVO(userInfoVo);
        } finally {
            redisUtils.delete(key);
        }
    }

    @RequestMapping("/getSysSetting")
    @GlobalInterceptor
    public ResponseVO getSysSetting() {
        SysSettingDto sysSettingDTO = redisComponent.getSysSettingDTO();
        return getSuccessResponseVO(sysSettingDTO);
    }
}
