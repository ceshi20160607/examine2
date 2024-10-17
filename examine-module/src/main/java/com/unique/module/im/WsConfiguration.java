package com.unique.module.im;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.tio.server.TioServerConfig;
import org.tio.server.intf.TioServerListener;
import org.tio.websocket.server.WsServerStarter;
import org.tio.websocket.server.handler.IWsMsgHandler;

/**
 * im配置类对象
 *
 * @author UNIQUE
 */
@Component
public class WsConfiguration implements ApplicationRunner {

    private final IWsMsgHandler handler;

    private final TioServerListener tioServerListener;

    public WsConfiguration(IWsMsgHandler handler, TioServerListener tioServerListener) {
        this.handler = handler;
        this.tioServerListener = tioServerListener;
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        WsServerStarter wsServerStarter = new WsServerStarter(36335, handler);
        TioServerConfig serverConfig = wsServerStarter.getTioServerConfig();
        serverConfig.setName("examine-im");
        //设置心跳超时时间
        serverConfig.setHeartbeatTimeout(60000);
        if (tioServerListener != null) {
            serverConfig.setTioServerListener(tioServerListener);
        }
        wsServerStarter.start();
    }
}
