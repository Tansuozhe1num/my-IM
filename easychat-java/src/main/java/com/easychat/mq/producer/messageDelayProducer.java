package com.easychat.mq.producer;

import com.easychat.entity.dto.DelayMessageDto;
import com.easychat.entity.enums.mqMessageStatusEnum;
import com.easychat.redis.redisComponent;
import com.easychat.utils.JsonUtils;
import com.easychat.utils.StringTools;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class messageDelayProducer {

    private final String queuename = "delay-queue";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private redisComponent redisComponent;

    private static final Logger logger = LoggerFactory.getLogger(messageDelayProducer.class);

    public <T> void sendDelayMessage(String topic, T message, Integer delayTime) {
        DelayMessageDto delayMessage = DelayMessageDto.builder()
                .messageId(StringTools.generateSecureRandomString(7))
                .delayTime(delayTime)
                .retryTime(2)
                .topic(topic)
                .message(message)
                .status(mqMessageStatusEnum.Process.getStatus())
                .build();

        send(delayMessage);
    }

    private void send(DelayMessageDto delayMessageDto) {
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(queuename);
        RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

        Integer messageSend = redisComponent.getMessageSend(delayMessageDto.getMessageId());
        if (messageSend != null) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            try {
                delayedQueue.offer(JsonUtils.convertObj2Json(delayMessageDto), delayMessageDto.getDelayTime(), TimeUnit.MILLISECONDS);
                break;
            } catch (Exception e) {
                logger.warn("发送消息失败: messageID: {}, 重试第{}次", delayMessageDto.getMessageId(), i + 1);
            }
        }

        redisComponent.setMessageSend(delayMessageDto.getMessageId());
    }
}
