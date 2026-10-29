package com.illarilegal.ordenlegal;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.web.*;
import org.springframework.transaction.support.*;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.Level;
import java.util.Map;
class ObservabilityRegressionTest {
    @AfterEach void cleanup(){MDC.clear();if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();}
    @Test void restoresNestedContextAndWorkerAfterFailure(){
        MDC.put("traceId","outer_trace");MDC.put("eventId","outer_event");
        assertThrows(IllegalStateException.class,()->{try(var scope=new Correlation("inner_trace","inner_event")){
            assertEquals("inner_trace",Correlation.current());throw new IllegalStateException();
        }});
        assertEquals("outer_trace",Correlation.current());assertEquals("outer_event",MDC.get("eventId"));
    }
    @Test void rejectsHeaderInjectionAndCreatesTechnicalIdentifier(){
        try(var scope=new Correlation("bad\r\nheader",null)){assertTrue(Correlation.valid(Correlation.current()));assertNull(MDC.get("eventId"));}
        assertNull(Correlation.current());
    }
    @Test void envelopePreservesTechnicalTraceAcrossOutboxRetries(){
        String payload;
        try(var scope=new Correlation("purchase_trace_1",null)){payload=EventEnvelope.enrich("{\"ordenId\":7}");}
        var first=EventEnvelope.parse(payload);var next=EventEnvelope.parse(EventEnvelope.enrich(payload));
        assertEquals("purchase_trace_1",next.path("traceId").asText());assertEquals(first.path("eventId"),next.path("eventId"));
    }
    @Test void transportCorrelationDoesNotChangeBusinessInboxFingerprint(){
        var original=EventEnvelope.parse("{\"ordenId\":7,\"total\":10}");var traced=original.deepCopy();traced.put("traceId","purchase_trace_2");
        assertEquals(EventEnvelope.hash(original),EventEnvelope.hash(traced));traced.put("total",11);
        assertNotEquals(EventEnvelope.hash(original),EventEnvelope.hash(traced));
    }
    @Test void feignUsesGeneratedContextEvenWithoutIncomingRequestHeader(){
        var request=new feign.RequestTemplate();
        try(var scope=new Correlation("generated_trace",null)){new FeignTraceConfiguration().traceIdToCatalogo().apply(request);}
        assertTrue(request.headers().get("X-Trace-ID").contains("generated_trace"));
    }
    @Test void httpReturnsTraceAndClearsThreadOnException(){
        var req=new MockHttpServletRequest("GET","/api/v1/ordenes");req.addHeader("X-Trace-ID","http_trace_123");
        var res=new MockHttpServletResponse();
        assertThrows(jakarta.servlet.ServletException.class,()->new TraceIdFilter().doFilter(req,res,(a,b)->{throw new jakarta.servlet.ServletException();}));
        assertEquals("http_trace_123",res.getHeader("X-Trace-ID"));assertNull(Correlation.current());
    }
    @Test void structuredLayoutNeverSerializesThrowablesAndRedactsUnknownMessages(){
        var e=new LoggingEvent();e.setTimeStamp(System.currentTimeMillis());e.setLevel(Level.ERROR);e.setMessage("Authorization: Bearer abc/def");
        e.setMDCPropertyMap(Map.of("traceId","safe_trace_123","eventId","invalid\nvalue","password","not-collected"));
        String output=new SafeObservationLayout().doLayout(e);
        assertTrue(output.contains("observation_redacted"));assertTrue(output.contains("safe_trace_123"));
        e=new LoggingEvent();e.setTimeStamp(System.currentTimeMillis());e.setLevel(Level.INFO);e.setMDCPropertyMap(Map.of());
        e.setMessage("password=SuperSecret123");
        assertTrue(new SafeObservationLayout().doLayout(e).contains("observation_redacted"));
        e=new LoggingEvent();e.setTimeStamp(System.currentTimeMillis());e.setLevel(Level.INFO);e.setMDCPropertyMap(Map.of());
        e.setMessage("http_completed method=GET status=200 durationMs=3");
        assertTrue(new SafeObservationLayout().doLayout(e).contains("http_completed"));
        assertFalse(output.contains("Bearer"));assertFalse(output.contains("password"));assertFalse(output.contains("invalid"));
    }
    @Test void commitObservationDoesNotInstallCallbackWithoutTransaction(){
        try(var scope=new Correlation("commit_trace_1",null)){ObservationCommit.log("kafka_processed");}
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());assertNull(Correlation.current());
    }
    @Test void commitObservationWaitsForTransactionCompletion(){
        TransactionSynchronizationManager.initSynchronization();
        try(var scope=new Correlation("commit_trace_2","event_trace_2")){ObservationCommit.log("kafka_processed");}
        assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertNull(Correlation.current());assertNull(MDC.get("eventId"));
    }
}
