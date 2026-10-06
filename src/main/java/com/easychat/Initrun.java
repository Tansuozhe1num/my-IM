package com.easychat;

import com.easychat.redis.redisUtils;
import com.easychat.websocket.netty.NettyWebsocketStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;

@Component("InitRun")
public class Initrun implements CommandLineRunner {
    private static Logger logger = LoggerFactory.getLogger(Initrun.class);

    @Resource
    private redisUtils redisUtils;

    @Resource
    private DataSource dataSource;

    @Resource
    private NettyWebsocketStart nettyWebsocketStart;

    @Override
    public void run(String... args) throws Exception{
        try {
            // redisUtils.get("y");
            dataSource.getConnection();

            nettyWebsocketStart.startNetty();
            logger.info("服务启动成功");
        } catch (SQLException exception) {
            logger.error("数据库启动失败");
        } catch (Exception e) {
            logger.error("启动失败" + e);
        }
    }
}
