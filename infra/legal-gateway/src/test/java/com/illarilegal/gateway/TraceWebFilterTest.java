package com.illarilegal.gateway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class TraceWebFilterTest {
    @Test void propagatesExactlyOneValidatedHeaderToDownstream(){
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/servicios").header("X-Trace-ID","purchase_1234","ignored_1234"));
        new TraceWebFilter().filter(exchange,e->{assertEquals(java.util.List.of("purchase_1234"),e.getRequest().getHeaders().get("X-Trace-ID"));return Mono.empty();}).block();
        assertEquals("purchase_1234",exchange.getResponse().getHeaders().getFirst("X-Trace-ID"));assertNull(Correlation.current());
    }
    @Test void createsTraceWhenBrowserDoesNotSupplyOne(){
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/servicios"));
        new TraceWebFilter().filter(exchange,e->Mono.empty()).block();
        assertTrue(Correlation.valid(exchange.getResponse().getHeaders().getFirst("X-Trace-ID")));
    }
    @Test void asyncCompletionRestoresWorkerContextEvenOnError(){
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/servicios"));
        assertThrows(IllegalStateException.class,()->new TraceWebFilter().filter(exchange,e->Mono.error(new IllegalStateException())).block());
        assertNull(Correlation.current());
    }
}
