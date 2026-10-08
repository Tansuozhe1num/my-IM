package com.easychat.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageSendDto<T> implements Serializable {

    private static final long serialVersionUID = -104575203317142417L;

    // 消息ID
    private Long messageId;

    // 会话ID
    private String sessionId;

    // 发送人
    private String sendUserId;

    // 发送人昵称
    private String sendUserNickName;

    // 联系人ID
    private String contactId;

    // 联系人名称
    private String contactName;

    // 消息内容
    private String messageContent;

    // 最后的消息
    private String lastMessage;

    // 消息类型
    private Integer messageType;

    // 发送时间
    private Long sendTime;

    // 联系人类型
    private Integer contactType;

    // 扩展信息
    private T extendData;

    // 消息状态 0:发送中 1:已发送 对于文件是异步上传状态处理
    private Integer status;

    // 客户端用于关联发送回执，不写入消息表
    private String clientMessageId;

    // 文件信息
    private Long fileSize;
    private String fileName;
    private Integer fileType;

    // 群员
    private Integer memberCount;

    // --- 以下为省略的 getter 和 setter 方法 ---
    // 提示：实际开发中通常需要补充 Getter/Setter 方法，
    // 或者使用 Lombok 的 @Data 注解来简化代码。
}
