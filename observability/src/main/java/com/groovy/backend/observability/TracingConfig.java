package com.groovy.backend.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.ObservationFilter;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

/**
 * MSA 전환 Phase 12: 분산 트레이싱. 6개 서비스(gateway/identity/study/calendar/content/notification)
 * 의 config/TracingConfig.java 에 byte-identical 로 복붙되어 있던 것을 이 모듈로 모은 것.
 *
 * ⚠️ 소비 배선: 이 클래스는 서비스 base 패키지(com.groovy.backend.&lt;svc&gt;) 밖이라 컴포넌트
 * 스캔에 안 잡힌다. 서비스가 @SpringBootApplication 클래스에 아래 중 하나를 해줘야 한다:
 *   - @Import(com.groovy.backend.observability.TracingConfig.class), 또는
 *   - scanBasePackages 에 "com.groovy.backend.observability" 추가
 * (후속: @AutoConfiguration 전환 — Boot 의 OpenTelemetrySdkAutoConfiguration 과의 순서
 *  (@AutoConfigureBefore)를 study-service 파일럿에서 실기동 검증한 뒤 적용. 이슈 #4.)
 *
 * Boot 4.1의 spring-boot-opentelemetry/spring-boot-micrometer-tracing은 OpenTelemetrySdk의
 * "틀"만 제공하고 실제 SdkTracerProvider(OTLP 익스포터 포함)와 Micrometer Tracer 빈은
 * 만들어주지 않는다. 그래서 여기서 SdkTracerProvider/ContextPropagators/Tracer/Propagator를
 * 직접 조립한다 — SdkTracerProvider와 ContextPropagators를 빈으로 노출하면 Boot의
 * OpenTelemetrySdkAutoConfiguration이 이 둘을 모아 OpenTelemetrySdk 빈을 만들어준다.
 *
 * MDC에 traceId를 채우는 건 여기서 안 한다 — 같은 모듈의 TraceIdTurboFilter가 로그 찍는
 * 순간 Span.current()를 직접 읽는다(등록 순서에 의존하지 않아 더 견고하다).
 */
@Configuration
public class TracingConfig {

	@Bean
	public SdkTracerProvider sdkTracerProvider(
		Resource resource,
		@Value("${management.otlp.tracing.endpoint}") String otlpEndpoint,
		@Value("${management.tracing.sampling.probability:1.0}") double samplingProbability
	) {
		OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
			.setEndpoint(otlpEndpoint)
			.build();

		return SdkTracerProvider.builder()
			.setResource(resource)
			.setSampler(Sampler.traceIdRatioBased(samplingProbability))
			.addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
			.build();
	}

	@Bean
	public ContextPropagators contextPropagators() {
		return ContextPropagators.create(TextMapPropagator.composite(
			W3CTraceContextPropagator.getInstance(),
			W3CBaggagePropagator.getInstance()));
	}

	@Bean
	public Tracer tracer(OpenTelemetrySdk openTelemetrySdk, @Value("${spring.application.name}") String serviceName) {
		return new OtelTracer(openTelemetrySdk.getTracer(serviceName), new OtelCurrentTraceContext(), event -> { });
	}

	@Bean
	public Propagator propagator(OpenTelemetrySdk openTelemetrySdk, @Value("${spring.application.name}") String serviceName) {
		return new OtelPropagator(openTelemetrySdk.getPropagators(), openTelemetrySdk.getTracer(serviceName));
	}

	// Micrometer 기본 HTTP observation convention은 상태 코드를 "status"라는 축약 키로만 span에
	// 붙인다 — Tempo/Grafana가 흔히 찾는 OTel 표준 키(http.response.status_code)로도 남긴다.
	@Bean
	public ObservationFilter httpStatusObservationFilter() {
		return new HttpStatusObservationFilter();
	}
}
