package com.groovy.backend.common;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

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

	// 순서를 지정하지 않으면 기본값(LOWEST_PRECEDENCE)이라 Security/observation 필터보다 안쪽에서
	// 실행된다 — Span이 아직 열려있는 동안 로그를 찍어야 TraceIdTurboFilter가 traceId를 MDC에
	// 붙여줄 수 있다(observation 필터 바깥에 두면 doFilter()가 반환된 시점엔 이미 span이 종료돼
	// traceId가 안 붙는다). 대신 JwtAuthenticationFilter가 거부한 요청(401 등)은 체인이 여기까지
	// 안 와서 로그에 안 남는다 — 이번엔 정상 처리된 요청의 trace 연결을 우선하기로 함.
	@Bean
	@ConditionalOnProperty(name = "logging.request.enabled", havingValue = "true", matchIfMissing = true)
	public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
		return new FilterRegistrationBean<>(new RequestLoggingFilter());
	}
}
