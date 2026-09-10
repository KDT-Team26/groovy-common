package com.groovy.backend.common.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 컨트롤러/필터 어디에도 요청 단위 로깅이 없어 GET 요청은 정상 처리돼도 로그가 전혀 안 남던 문제
 * (IR-#14 참고) 대응. 요청당 한 번, method/uri/status/처리시간을 남긴다 — 바디는 남기지 않는다
 * (민감정보 로깅 방지, 로그 볼륨 절감).
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long startTime = System.currentTimeMillis();
		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMs = System.currentTimeMillis() - startTime;
			log.info("HTTP 요청 처리: method={} uri={} status={} durationMs={}",
					request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
		}
	}
}
