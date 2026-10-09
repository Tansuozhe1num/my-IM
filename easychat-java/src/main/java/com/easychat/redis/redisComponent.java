package com.easychat.redis;

import com.easychat.entity.constants.Constants;
import com.easychat.entity.dto.DelayMessageDto;
import com.easychat.entity.dto.SysSettingDto;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.utils.JsonUtils;
import com.easychat.utils.StringTools;
import jdk.nashorn.internal.parser.Token;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component("redisComponent")
public class redisComponent {

    @Resource
    private redisUtils redisUtils;

    public Long getUserHeartBeat(String userId) {
        return (Long) redisUtils.get(Constants.REDIS_CHECK_Heart_Beat + userId);
    }

    public void setUserHeartBeat(String userId) {
        redisUtils.setex(Constants.REDIS_CHECK_Heart_Beat + userId, StringTools.generateSecureRandomString(11), Constants.REDIS_Heart_Beat);
    }

    public void deleteUserHeartBeat(String userId) {
        redisUtils.delete(Constants.REDIS_CHECK_Heart_Beat + userId);
    }

    public void saveTokenUserInfoDTO(TokenUserinfoDTO tokenUserinfoDTO) {
        redisUtils.setex(Constants.REDIS_KEY_WS_TOKEN + tokenUserinfoDTO.getToken(), tokenUserinfoDTO, 60 * 60 * 24 * 2);
        redisUtils.setex(Constants.REDIS_KEY_WS_TOKEN_USERID + tokenUserinfoDTO.getUserId(), tokenUserinfoDTO.getToken(), 2 * 60 * 60 * 24);
    }

    public TokenUserinfoDTO getTokenUserInfoDTO(String token) {
        TokenUserinfoDTO dto = (TokenUserinfoDTO) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN + token);
        return dto;
    }

    public void deleteTokenUserInfoDTO(String userId) {
        String token = (String) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN + token);
    }

    public SysSettingDto getSysSettingDTO() {
        SysSettingDto settingdto = (SysSettingDto) redisUtils.get(Constants.REDIS_SYSTEM_CONFIG);
        settingdto = settingdto == null ? new SysSettingDto() : settingdto;
        return settingdto;
    }

    public boolean outUser(TokenUserinfoDTO tokenUserinfoDTO) {
        if (redisUtils.get(Constants.REDIS_KEY_WS_TOKEN + tokenUserinfoDTO.getToken()) == null) {
            return false;
        }
        redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN + tokenUserinfoDTO.getToken());
        redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN_USERID + tokenUserinfoDTO.getUserId());
        return true;
    }

    public boolean outUser(String userId) {
        String token = (String) redisUtils.get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        if (token == null) {
            return false;
        }
        redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN + token);
        redisUtils.delete(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        return true;
    }

    public void cleanUserContact(String userId) {
        redisUtils.delete(Constants.REDIS_CONTACT_LIST + userId);
    }

    public void addUserBatch(String userID, List<String> contactIdList) {
        redisUtils.lpushAll(Constants.REDIS_CONTACT_LIST + userID, contactIdList, Constants.ONE_DAY);
    }

    public void addUser(String userID, String contactId) {
        List<String> userContactList = getUserContactList(userID);
        if (userContactList != null && userContactList.contains(contactId)) {
            return;
        }
        redisUtils.lpush(Constants.REDIS_CONTACT_LIST + userID, contactId, Constants.ONE_DAY);
    }


    public List<String> getUserContactList(String userId) {
        List<String> result = (List<String>) redisUtils.getQueueList(Constants.REDIS_CONTACT_LIST + userId);
        return result == null ? Collections.<String>emptyList() : result;
    }

    public void setMessageSend(String messageId) {
        redisUtils.setex(Constants.REDIS_MQ_MESSAGE_ONLY_MARK + messageId, 1, 3 * 60);
    }

    public Integer getMessageSend(String messageId) {
        return (Integer) redisUtils.get(Constants.REDIS_MQ_MESSAGE_ONLY_MARK + messageId);
    }

    public void saveExtendMqMessage(DelayMessageDto msg) {
        redisUtils.set(Constants.REDIS_MQ_EXTEND_MESSAGE + msg.getMessageId(), JsonUtils.convertObj2Json(msg));
    }

}
