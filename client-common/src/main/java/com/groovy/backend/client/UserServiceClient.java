package com.groovy.backend.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.groovy.backend.common.response.ApiResponse;

/**
 * study/content-service에 복붙되어 있던 클래스를 client-common으로 통합했다
 * (ClientCommonAutoConfiguration이 빈으로 등록). 작성자/신청자 이름처럼 표시용 이름이 필요한데
 * 자기 서비스엔 User 테이블이 없어서 identity-service에 물어봐야 하는 서비스들이 쓴다.
 *
 * identity-service가 죽어 있거나 느려지면(ResilientCallExecutor가 서킷브레이커/재시도로 처리)
 * 예외 대신 빈 Map을 반환한다 — 이름 없이("null")라도 나머지 데이터는 보여주는 편이 요청 전체가
 * 500으로 죽는 것보다 낫다는 기존 판단을 그대로 유지한다.
 *
 * study/memoir 목록 조회처럼 요청마다 반복되는 leaderId/authorId 조회가 그대로 identity-service
 * 왕복으로 이어져 부하 상황에서 /api/users/names 호출이 폭증하는 문제가 있었다. 이름은
 * signup 이후 바꾸는 API 자체가 없어(사실상 불변) Redis에 cache-aside로 얹기 좋은 데이터라
 * 캐시를 추가했다. redisTemplate이 없는 서비스(calendar-service 등)에서는 캐시 없이 기존과
 * 동일하게 매번 identity-service를 direct 호출한다.
 */
public class UserServiceClient {

	private static final String CACHE_KEY_PREFIX = "user:name:";

	private final RestClient restClient;
	private final ResilientCallExecutor executor;
	private final StringRedisTemplate redisTemplate;
	private final Duration cacheTtl;

	public UserServiceClient(RestClient.Builder restClientBuilder, String identityServiceUrl, long connectTimeoutMs, long readTimeoutMs) {
		this(restClientBuilder, identityServiceUrl, connectTimeoutMs, readTimeoutMs, null, 0);
	}

	public UserServiceClient(RestClient.Builder restClientBuilder, String identityServiceUrl, long connectTimeoutMs, long readTimeoutMs,
			StringRedisTemplate redisTemplate, long cacheTtlSeconds) {
		var httpClient = java.net.http.HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		this.restClient = restClientBuilder
			.baseUrl(identityServiceUrl)
			.requestFactory(requestFactory)
			.build();
		this.executor = new ResilientCallExecutor("user-service-client");
		this.redisTemplate = redisTemplate;
		this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
	}

	public Map<Long, String> findNamesByIds(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		if (redisTemplate == null) {
			return fetchFromIdentityService(userIds);
		}

		List<Long> distinctIds = userIds.stream().distinct().toList();
		List<String> cachedValues = redisTemplate.opsForValue().multiGet(distinctIds.stream().map(this::cacheKey).toList());

		Map<Long, String> result = new HashMap<>();
		List<Long> missingIds = new ArrayList<>();
		for (int i = 0; i < distinctIds.size(); i++) {
			String cached = cachedValues == null ? null : cachedValues.get(i);
			if (cached != null) {
				result.put(distinctIds.get(i), cached);
			} else {
				missingIds.add(distinctIds.get(i));
			}
		}

		if (!missingIds.isEmpty()) {
			Map<Long, String> fetched = fetchFromIdentityService(missingIds);
			fetched.forEach((userId, name) -> {
				result.put(userId, name);
				redisTemplate.opsForValue().set(cacheKey(userId), name, cacheTtl);
			});
		}

		return result;
	}

	private String cacheKey(Long userId) {
		return CACHE_KEY_PREFIX + userId;
	}

	private Map<Long, String> fetchFromIdentityService(List<Long> userIds) {
		return executor.execute(
			() -> {
				ApiResponse<Map<Long, String>> response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/api/users/names").queryParam("ids", userIds).build())
					.retrieve()
					.body(new ParameterizedTypeReference<ApiResponse<Map<Long, String>>>() {
					});
				return response == null || response.data() == null ? Map.<Long, String>of() : response.data();
			},
			Map::of);
	}
}
