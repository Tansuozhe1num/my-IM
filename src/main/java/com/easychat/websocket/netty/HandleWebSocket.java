package com.easychat.websocket.netty;

import com.easychat.entity.constants.Constants;
import com.easychat.entity.dto.TokenUserinfoDTO;
import com.easychat.redis.redisComponent;
import com.easychat.redis.redisUtils;
import com.easychat.websocket.ChannelContextUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@ChannelHandler.Sharable
public class HandleWebSocket extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(HandleWebSocket.class);

    @Resource
    private redisComponent redisComponent;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) throws Exception {
        Channel channel = ctx.channel();
        Attribute<String> attribute = channel.attr(AttributeKey.valueOf(channel.id().toString()));
        String userId = attribute.get();
        logger.info("收到用户{} 消息:{}", userId, textWebSocketFrame.text());
        redisComponent.setUserHeartBeat(userId);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("有新的链接加入");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("有链接断开");
        channelContextUtils.removeContext(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("websocket连接异常: {}", cause.getMessage());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete complete = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            HttpHeaders header = complete.requestHeaders();
            String token = header.get("token");
            if (token == null) {
                ctx.channel().close();
                return;
            }

            TokenUserinfoDTO tokenUserInfoDTO = redisComponent.getTokenUserInfoDTO(token);
            if (null == tokenUserInfoDTO) {
               ctx.channel().close();
               return;
            }

            channelContextUtils.addContext(tokenUserInfoDTO.getUserId(), ctx.channel());
        }
    }
}
