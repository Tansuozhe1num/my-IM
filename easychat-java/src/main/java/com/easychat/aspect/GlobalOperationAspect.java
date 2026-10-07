package com.easychat.aspect;

import com.easychat.annotation.GlobalInterceptor;
import com.easychat.entity.constants.Constants;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.entity.enums.ResponseCodeEnum;
import com.easychat.exception.BusinessException;
import com.easychat.redis.redisUtils;
import org.aspectj.apache.bcel.classfile.Constant;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component("globalOperationAspect")
public class GlobalOperationAspect {

    @Resource
    private redisUtils redisUtils;

    private static final Logger logger = LoggerFactory.getLogger(GlobalOperationAspect.class);

    @Before("@annotation(com.easychat.annotation.GlobalInterceptor)")
    public void interceptorDO(JoinPoint point) {
        try {
            Method method = ((MethodSignature) point.getSignature()).getMethod();
            GlobalInterceptor interceptor = method.getAnnotation(GlobalInterceptor.class);
            if (interceptor == null) {
                return;
            }
            if (interceptor.checklogin() || interceptor.checkadmin()) {
                checkLogin(interceptor.checkadmin());
            }
        } catch (BusinessException e) {
            logger.error("全局拦截错误" + e);
            throw e;
        } catch (Exception e) {
            logger.error("系统错误" + e);
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    private void checkLogin(boolean checkadmin) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader("token");
        TokenUserinfoDTO userinfoDTO = (TokenUserinfoDTO) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN + token);

        if (userinfoDTO == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }

        if (checkadmin && !userinfoDTO.isAdmin()) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
    }
}
