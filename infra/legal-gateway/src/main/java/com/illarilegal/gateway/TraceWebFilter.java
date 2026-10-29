package com.illarilegal.gateway;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceWebFilter implements WebFilter {
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain) {
        String trace=Correlation.normalize(exchange.getRequest().getHeaders().getFirst("X-Trace-ID"));
        long start=System.nanoTime();
        var request=exchange.getRequest().mutate().headers(h->h.set("X-Trace-ID",trace)).build();
        exchange.getResponse().getHeaders().set("X-Trace-ID",trace);
        return chain.filter(exchange.mutate().request(request).build()).doFinally(signal->{
            try(var scope=new Correlation(trace,null)) {
                var status=exchange.getResponse().getStatusCode();
                org.slf4j.LoggerFactory.getLogger("ILLARI_OBS").info("http_completed method={} status={} durationMs={}",
                    request.getMethod(),status==null?200:status.value(),(System.nanoTime()-start)/1000000);
            }
        });
    }
}
