package com.illarilegal.ordenlegal;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignTraceConfiguration {
    @Bean
    RequestInterceptor traceIdToCatalogo() {
        return template -> {
            String traceId = Correlation.current();
            if (Correlation.valid(traceId)) template.header("X-Trace-ID",traceId);
        };
    }
}
