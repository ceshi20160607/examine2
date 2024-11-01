package com.unique.core.api;


import org.springframework.context.annotation.Configuration;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.web.client.RestClient;
//import org.springframework.web.client.support.RestClientAdapter;
//import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class ResApiClientConfig {

//    @Bean
//    public ResApiClient ResApiClientConfig(RestClient.Builder builder, @Value("${game.res.url}") String url) {
//        RestClient build = builder.baseUrl(url)
//                .defaultHeader("Content-Type", "application/json")
//                .defaultHeader("Manager-Token", CommonConst.READ_RES_MANAGER_ADMINMISTRATOR_TOKEN)
//                .build();
//        RestClientAdapter restClientAdapter = RestClientAdapter.create(build);
//        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(restClientAdapter).build();
//        return proxyFactory.createClient(ResApiClient.class);
//    }
}
