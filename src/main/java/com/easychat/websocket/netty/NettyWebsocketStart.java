package com.easychat.websocket.netty;

import com.easychat.entity.config.Appconfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component("NettyWebsocketStart")
public class NettyWebsocketStart {

    private static final Logger logger = LoggerFactory.getLogger(NettyWebsocketStart.class);
    private EventLoopGroup bossGroup = new NioEventLoopGroup(1);

    private EventLoopGroup workGroup = new NioEventLoopGroup();

    /** 持有服务端 Channel，停机时先关闭，才能触发客户端断开并写完离线状态 */
    private Channel serverChannel;

    @Resource
    private Appconfig appconfig;

    @Resource
    private HandleWebSocket handleWebSocket;

    /**
     * 必须等 EventLoop 处理完 channelInactive（会更新数据库）之后再返回，
     * 否则 Spring 会先关闭 Hikari 连接池，断开连接时就会报 DataSource has been closed。
     */
    @PreDestroy
    public void close() {
        logger.info("正在关闭 Netty WebSocket 服务");
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        workGroup.shutdownGracefully().awaitUninterruptibly();
        bossGroup.shutdownGracefully().awaitUninterruptibly();
        logger.info("Netty 已关闭");
    }

    @Async
    public void startNetty() {
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workGroup);
            serverBootstrap.channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            ChannelPipeline pipeline = channel.pipeline();
                            // 设置几个重要的处理器
                            // 对http协议支持，使用http编码器，解码器
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                            // 心跳
                            // readerIdleTime, writerIdleTime, allIdleTime, unit
                            pipeline.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
                            pipeline.addLast(new HandlerHeartBeat());

                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
                            pipeline.addLast(handleWebSocket);
                        }
                    });

            ChannelFuture channelFuture = serverBootstrap.bind(appconfig.getWsport()).sync();
            serverChannel = channelFuture.channel();
            logger.info("netty服务器启动成功, 端口: {}", appconfig.getWsport());
            serverChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.warn("netty被打断");
        } catch (Exception e) {
            logger.error("启动netty失败", e);
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }
}
