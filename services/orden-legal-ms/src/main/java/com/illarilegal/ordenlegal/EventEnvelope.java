package com.illarilegal.ordenlegal;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** IDs persist in the outbox; legacy v1 IDs are derived deterministically from the business transition. */
public final class EventEnvelope {
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    private EventEnvelope() {}
    public static ObjectNode parse(String payload) {
        try {
            JsonNode n=JSON.readTree(payload);
            if(!(n instanceof ObjectNode o)) throw new IllegalArgumentException("Evento JSON inválido.");
            return o;
        } catch(Exception e) { throw new IllegalArgumentException("Evento JSON inválido.",e); }
    }
    public static String enrich(String payload) {
        ObjectNode o=parse(payload);
        if(!o.hasNonNull("eventId")) o.put("eventId",UUID.randomUUID().toString());
        id(o); o.put("schemaVersion",2);
        if(!Correlation.valid(o.path("traceId").asText(null))) o.put("traceId",Correlation.normalize(Correlation.current()));
        return o.toString();
    }
    public static String id(ObjectNode o) {
        if(o.hasNonNull("eventId")) {
            String id=o.path("eventId").asText();
            try { if(!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
            catch(Exception e) { throw new IllegalArgumentException("eventId debe ser un UUID canónico."); }
            return id;
        }
        String semantic=o.path("origen").asText()+":"+o.path("tipoEvento").asText()+":"
                +o.path("ordenId").asText()+":"+o.path("pagoId").asText()+":"+o.path("revisionPago").asText();
        return UUID.nameUUIDFromBytes(semantic.getBytes(StandardCharsets.UTF_8)).toString();
    }
    public static String hash(ObjectNode original) {
        ObjectNode copy=original.deepCopy(); copy.remove(List.of("eventId","timestamp","schemaVersion","traceId"));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical(copy).toString().getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static JsonNode canonical(JsonNode n) {
        if(n.isObject()) {
            ObjectNode o=JSON.createObjectNode();
            List<String> names=new ArrayList<>(); n.fieldNames().forEachRemaining(names::add); Collections.sort(names);
            names.forEach(k->o.set(k,canonical(n.get(k)))); return o;
        }
        if(n.isArray()){ArrayNode a=JSON.createArrayNode();n.forEach(v->a.add(canonical(v)));return a;}
        if(n.isNumber()) return DecimalNode.valueOf(n.decimalValue().stripTrailingZeros());
        return n;
    }
}

