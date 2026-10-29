package com.illarilegal.ordenlegal;
import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
/** Only the ILLARI_OBS logger is collected. No throwable, URL, body, header or user identity. */
public class SafeObservationLayout extends LayoutBase<ILoggingEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override public String doLayout(ILoggingEvent e) {
        try {
            var fields = new LinkedHashMap<String,Object>();
            fields.put("timestamp", java.time.Instant.ofEpochMilli(e.getTimeStamp()).toString());
            fields.put("level",e.getLevel().toString());
            for(String key: java.util.List.of("traceId","eventId")) {
                String value=e.getMDCPropertyMap().get(key);
                if(Correlation.valid(value)) fields.put(key,value);
            }
            // The collector receives only explicitly instrumented diagnostic events.
            String message=e.getFormattedMessage();
            String allowed="(?:http_completed method=(?:GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS) status=[0-9]{3} durationMs=[0-9]+"
                +"|outbox_retry attempts=[0-9]+ exhausted=(?:true|false)"
                +"|kafka_dlt topic=illari[.](?:orden[.]creada|pago[.]resultado) partition=[0-9]+ offset=[0-9]+"
                +"|kafka_processed|outbox_published|kafka_replay_sent)";
            if(!message.matches(allowed)) message="observation_redacted";
            fields.put("message",message);
            return JSON.writeValueAsString(fields)+"\n";
        } catch(Exception ignored) { return "{\"message\":\"observation_encoding_error\"}\n"; }
    }
}
