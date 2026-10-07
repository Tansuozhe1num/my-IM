package com.easychat.entity.dto;

import com.easychat.entity.po.ChatMessage;
import com.easychat.entity.po.ChatSessionUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsInitData {
    /**
     * 聊天记录
     */
    private List<ChatSessionUser> chatSessionUsers;

    private List<ChatMessage> chatMessages;

    /**
     * 好友申请条数
     */
    public Integer applyCount;
}
