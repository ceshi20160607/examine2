package com.unique.unexamine.web.config;

import org.apache.coyote.http11.Http11Nio2Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 使用 Windows 与 Linux 均支持的异步连接器运行唯一后端服务。 */
@Configuration
public class TomcatProtocolConfig {
    /** 本机 JDK 的 NIO selector 无法建立环回管道时，NIO2 仍可提供 HTTP 服务。 */
    @Bean
    public TomcatServletWebServerFactory tomcatServletWebServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setProtocol(Http11Nio2Protocol.class.getName());
        return factory;
    }
}
