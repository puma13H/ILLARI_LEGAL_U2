package com.illarilegal.pago;
import org.springframework.transaction.support.*;
/** Snapshot IDs before returning to the Kafka worker; log only after local commit. */
public final class ObservationCommit {
    private ObservationCommit() {}
    public static void log(String action) {
        String trace=Correlation.current(),event=org.slf4j.MDC.get("eventId");
        Runnable emit=()->{try(var scope=new Correlation(trace,event)) {
            org.slf4j.LoggerFactory.getLogger("ILLARI_OBS").info(action);
        }};
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit(){emit.run();}
            });
        } else emit.run();
    }
}
