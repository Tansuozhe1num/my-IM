package com.easychat.mq.listener.impl;

import com.easychat.entity.config.ThreadPoolConfig;
import com.easychat.entity.dto.DelayMessageDto;
import com.easychat.entity.dto.MessageSendDto;
import com.easychat.mq.listener.DelayListener;
import com.easychat.utils.JsonUtils;
import com.easychat.websocket.ChannelContextUtils;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class messageDelayListener implements DelayListener {

    private static final String QUEUE_NAME = "delay-queue";
    private static final String DLQ_NAME = "delay-queue-dlq";
    private static final int MAX_RETRY = 3;

    private static final Logger logger = LoggerFactory.getLogger(messageDelayListener.class);

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ThreadPoolConfig threadPoolConfig;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @PostConstruct
    public void init() {
        this.work();
    }

    public void work() {
        threadPoolConfig.execute(() -> {
            RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(QUEUE_NAME);
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
            RBlockingQueue<String> dlqQueue = redissonClient.getBlockingQueue(DLQ_NAME);

            logger.info("延迟队列消费者已启动，开始监听队列: {}", QUEUE_NAME);

            while (true) {
                String take = null;
                try {
                    take = blockingQueue.take();
                    if (take == null) {
                        continue;
                    }

                    DelayMessageDto delayMessageDto = JsonUtils.convertJson2Obj(take, DelayMessageDto.class);
                    if (delayMessageDto == null) {
                        logger.error("消息解析为空，直接送入死信队列: {}", take);
                        dlqQueue.offer(take);
                        continue;
                    }

                    logger.info("收到延迟消息: {}", delayMessageDto);

                    // JSON enters the queue as a JSONObject, so convert the nested payload explicitly.
                    MessageSendDto message = JsonUtils.convertJson2Obj(
                            JsonUtils.convertObj2Json(delayMessageDto.getMessage()),
                            MessageSendDto.class
                    );
                    channelContextUtils.sendMessage(message);
                } catch (Exception e) {
                    logger.error("消息处理失败, 原始消息: {}", take, e);
                    if (take == null) {
                        continue;
                    }
                    try {
                        DelayMessageDto dto = JsonUtils.convertJson2Obj(take, DelayMessageDto.class);
                        if (dto == null) {
                            dlqQueue.offer(take);
                            continue;
                        }

                        int retryTime = dto.getRetryTime() == null ? 0 : dto.getRetryTime();

                        if (retryTime < MAX_RETRY) {
                            dto.setRetryTime(retryTime + 1);

                            long delaySeconds = (long) retryTime * 5 + 1;

                            String newMsg = JsonUtils.convertObj2Json(dto);
                            delayedQueue.offer(newMsg, delaySeconds, TimeUnit.SECONDS);

                            logger.warn("消息将在 {} 秒后重试，当前重试次数: {}/{}", delaySeconds, retryTime + 1, MAX_RETRY);
                        } else {
                            logger.error("消息达到最大重试次数，进入死信队列: {}", take);
                            dlqQueue.offer(take);
                        }
                    } catch (Exception ex) {
                        logger.error("重试逻辑处理异常，直接进入死信队列", ex);
                        dlqQueue.offer(take);
                    }
                }
            }
        });
    }
}
