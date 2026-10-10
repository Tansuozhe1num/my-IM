package com.easychat.controller;

import com.easychat.annotation.GlobalInterceptor;
import com.easychat.entity.dto.MessageSendDto;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.entity.enums.MessageTypeEnum;
import com.easychat.entity.enums.ResponseCodeEnum;
import com.easychat.entity.po.ChatMessage;
import com.easychat.entity.vo.ResponseVO;
import com.easychat.exception.BusinessException;
import com.easychat.service.ChatMessageService;
import com.easychat.utils.StringTools;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("chat")
public class ChatController extends ABaseController {
    @Resource
    private ChatMessageService chatMessageService;

    @PostMapping("/sendMessage")
    @GlobalInterceptor
    public ResponseVO sendMessage(HttpServletRequest request,
                                  @RequestParam(value = "ContactId", required = false) String contactId,
                                  @RequestParam(value = "MessageContext", required = false) String messageContent,
                                  @RequestParam(value = "messageType", defaultValue = "2") Integer messageType,
                                  @RequestParam(value = "clientMessageId", required = false) String clientMessageId) {
        if (StringTools.isEmpty(contactId) || contactId.length() > 64) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (StringUtils.isBlank(messageContent) || messageContent.trim().length() > 10000) {
            throw new BusinessException("消息不能为空且不能超过10000个字符");
        }
        if (!MessageTypeEnum.CHAT.getType().equals(messageType)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (clientMessageId != null && (clientMessageId.length() > 64 || !clientMessageId.matches("[A-Za-z0-9_-]+"))) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        ChatMessage chatMessage = ChatMessage.builder()
                .messageContent(messageContent.trim())
                .contactId(contactId.trim())
                .messageType(messageType)
                .build();
        TokenUserinfoDTO tokenUserinfoDTO = getTokenUserinfoDTO(request);
        MessageSendDto messageSendDto = chatMessageService.saveMessage(chatMessage, tokenUserinfoDTO, clientMessageId);
        return getSuccessResponseVO(messageSendDto);
    }
}
