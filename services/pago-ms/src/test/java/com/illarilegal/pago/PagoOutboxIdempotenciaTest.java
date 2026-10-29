package com.illarilegal.pago;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.*;

class PagoOutboxIdempotenciaTest {
    @Test
    void firstEventCreatesOneDurableResultAndReplayDoesNotDuplicateIt() throws Exception {
        PagoService service = mock(PagoService.class);
        PagoOutboxRepository outbox = mock(PagoOutboxRepository.class);
        when(service.iniciarDesdeOrden(eq(95L), eq("client-sub"), any(BigDecimal.class)))
                .thenReturn(new PagoService.Pago(9L, 95L, "client-sub", new BigDecimal("80.00"),
                        "PENDIENTE", null, null));
        when(service.resolver(9L, true))
                .thenReturn(new PagoService.Pago(9L, 95L, "client-sub", new BigDecimal("80.00"),
                        "VALIDADO", null, null));
        OrdenEventConsumer consumer = new OrdenEventConsumer(service,
                new ObjectMapper().findAndRegisterModules(), outbox, mock(EventoRecibidoRepository.class));
        String evento = "{\"tipoEvento\":\"orden.creada\",\"origen\":\"orden-legal-ms\",\"ordenId\":95,\"idCliente\":\"client-sub\",\"total\":80.00,\"metodoPago\":\"YAPE\"}";
        when(outbox.findForUpdate(95L)).thenReturn(Optional.empty());
        consumer.onOrden(evento);
        verify(outbox, times(1)).save(any(PagoOutboxEntity.class));
        when(outbox.findForUpdate(95L)).thenReturn(Optional.of(
                new PagoOutboxEntity(95L, "{\"estado\":\"VALIDADO\",\"pagoId\":9,\"revisionPago\":1}")));
        consumer.onOrden(evento);
        verify(outbox, times(1)).save(any(PagoOutboxEntity.class));
    }
}
