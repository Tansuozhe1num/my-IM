package com.easychat.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class DelayMessageDto<T> {

    private String topic;

    private T message;

    private Integer retryTime;

    private String messageId;

    private Integer delayTime;

    private Integer status;
}
