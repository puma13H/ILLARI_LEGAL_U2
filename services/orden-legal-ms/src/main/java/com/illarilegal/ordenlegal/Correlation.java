package com.illarilegal.ordenlegal;
import org.slf4j.MDC;
import java.util.UUID;
/** Technical correlation only. Nested scopes restore the previous worker/request context. */
public final class Correlation implements AutoCloseable {
    private final String previousTrace = MDC.get("traceId"), previousEvent = MDC.get("eventId");
    public static boolean valid(String value) { return value != null && value.matches("[a-zA-Z0-9_-]{8,64}"); }
    public static String normalize(String value) { return valid(value) ? value : UUID.randomUUID().toString(); }
    public static String current() { return MDC.get("traceId"); }
    public Correlation(String trace, String event) {
        MDC.put("traceId", normalize(trace));
        if (valid(event)) MDC.put("eventId",event); else MDC.remove("eventId");
    }
    public void close() {
        if(previousTrace==null) MDC.remove("traceId"); else MDC.put("traceId",previousTrace);
        if(previousEvent==null) MDC.remove("eventId"); else MDC.put("eventId",previousEvent);
    }
}
