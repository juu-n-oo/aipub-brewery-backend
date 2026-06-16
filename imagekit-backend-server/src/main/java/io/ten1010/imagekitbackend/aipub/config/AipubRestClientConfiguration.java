package io.ten1010.imagekitbackend.aipub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AipubRestClientConfiguration {

    @Bean
    public RestClient aipubRestClient(AipubProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

}
