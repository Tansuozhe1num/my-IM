package com.easychat.websocket;

import com.easychat.entity.dto.MessageSendDto;
import com.easychat.mq.listener.impl.messageDelayListener;
import com.easychat.mq.producer.messageDelayProducer;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class messageHandle {

    private static final String Topic = "message_server";
    @Resource
    private ChannelContextUtils channelContextUtils;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private messageDelayListener messageDelayListener;

    @Resource
    private messageDelayProducer messageDelayProducer;

    public void sendMsg(MessageSendDto messageSendDto) {
        messageDelayProducer.sendDelayMessage(Topic, messageSendDto, 0);
    }
}
