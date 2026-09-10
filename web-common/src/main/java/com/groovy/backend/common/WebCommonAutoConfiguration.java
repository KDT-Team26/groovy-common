package com.groovy.backend.common;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import com.groovy.backend.common.auth.JwtAuthenticationEntryPoint;
import com.groovy.backend.common.exception.GlobalExceptionHandler;
import com.groovy.backend.common.logging.RequestLoggingFilter;

/**
 * GlobalExceptionHandler(@RestControllerAdvice)와 JwtAuthenticationEntryPoint(@Component)는
 * com.groovy.backend.common 패키지에 있어 각 서비스의 기본 컴포넌트 스캔 범위
 * (com.groovy.backend.&lt;service&gt;)에 들어오지 않는다 — 그래서 스캔에 기대는 대신, Spring Boot의
 * 자동 설정(META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)으로
 * 이 모듈을 의존성에 추가한 서비스에 명시적으로 빈을 등록한다.
 */
@AutoConfiguration
public class WebCommonAutoConfiguration {

	@Bean
	public GlobalExceptionHandler globalExceptionHandler() {
		return new GlobalExceptionHandler();
	}

	@Bean
	public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
		return new JwtAuthenticationEntryPoint();
	}

	// 필터 체인 가장 바깥에 둬서 JwtAuthenticationFilter에서 거부된 요청(401 등)도 로그에 남는다.
	@Bean
	@ConditionalOnProperty(name = "logging.request.enabled", havingValue = "true", matchIfMissing = true)
	public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
		FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
