package com.groovy.backend.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

/**
 * study/content-service처럼 spring-boot-starter-data-redis를 붙이고 REDIS_HOST/PORT를 설정한 서비스는 이름 캐시가 켜지고,
 * calendar-service처럼 redis 의존성 자체가 없는 서비스는 getIfAvailable()이 null을 돌려줘
 * 캐시 없이(기존과 동일하게 매번 identity-service 직접 호출) 동작한다.
 */
@AutoConfiguration
public class ClientCommonAutoConfiguration {

	@Bean
	public UserServiceClient userServiceClient(
		RestClient.Builder restClientBuilder,
		@Value("${identity-service.url:http://identity-service:8081}") String identityServiceUrl,
		@Value("${identity-service.connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${identity-service.read-timeout-ms:3000}") long readTimeoutMs,
		ObjectProvider<StringRedisTemplate> redisTemplateProvider,
		@Value("${identity-service.name-cache-ttl-seconds:86400}") long nameCacheTtlSeconds
	) {
		return new UserServiceClient(restClientBuilder, identityServiceUrl, connectTimeoutMs, readTimeoutMs,
			redisTemplateProvider.getIfAvailable(), nameCacheTtlSeconds);
	}
}
