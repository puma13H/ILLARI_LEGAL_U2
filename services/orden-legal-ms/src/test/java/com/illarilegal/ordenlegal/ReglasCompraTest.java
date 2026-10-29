package com.illarilegal.ordenlegal;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReglasCompraTest {
    private static OrdenLegalController.NuevaOrden request(String method, Long id, Integer qty) {
        return new OrdenLegalController.NuevaOrden(method,List.of(new OrdenLegalController.DetalleNuevaOrden(id,qty)));
    }
    private static void invalid(OrdenLegalController.NuevaOrden request) {
        assertThatThrownBy(()->ReglasCompra.normalizar(request,CompraFase2IntegrationTest.cliente("client")))
                .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));
    }
    @Test void quantitiesMustBeBetweenOneAndOneThousand() {
        for(Integer qty:Arrays.asList(null,0,-1,1001)) invalid(request("YAPE",7L,qty));
        assertThat(ReglasCompra.normalizar(request("YAPE",7L,1000),CompraFase2IntegrationTest.cliente("client"))
                .detalles().getFirst().cantidad()).isEqualTo(1000);
    }
    @Test void paymentMethodsUseOneExplicitWhitelist() {
        for(String method:Arrays.asList(null,"","EFECTIVO","desconocido")) invalid(request(method,7L,1));
        assertThat(ReglasCompra.normalizar(request(" plin ",7L,1),CompraFase2IntegrationTest.cliente("client")).metodoPago()).isEqualTo("PLIN");
    }
    @Test void duplicatedServicesAreAggregatedBeforeFingerprintAndPricing() {
        var request=new OrdenLegalController.NuevaOrden("YAPE",List.of(new OrdenLegalController.DetalleNuevaOrden(8L,1),
                new OrdenLegalController.DetalleNuevaOrden(7L,2),new OrdenLegalController.DetalleNuevaOrden(7L,3)));
        var normal=ReglasCompra.normalizar(request,CompraFase2IntegrationTest.cliente("client"));
        assertThat(normal.detalles()).containsExactly(new OrdenLegalController.DetalleNuevaOrden(7L,5),new OrdenLegalController.DetalleNuevaOrden(8L,1));
    }
    @Test void duplicatesCannotBypassThePerServiceQuantityLimit() {
        invalid(new OrdenLegalController.NuevaOrden("YAPE",List.of(new OrdenLegalController.DetalleNuevaOrden(7L,600),
                new OrdenLegalController.DetalleNuevaOrden(7L,600))));
    }
    @Test void cartMustHaveBoundedSizeAndPositiveServiceIdentifiers() {
        invalid(null); invalid(new OrdenLegalController.NuevaOrden("YAPE",List.of()));
        invalid(new OrdenLegalController.NuevaOrden("YAPE",Collections.nCopies(101,new OrdenLegalController.DetalleNuevaOrden(7L,1))));
        invalid(request("YAPE",null,1)); invalid(request("YAPE",0L,1)); invalid(request("YAPE",-1L,1));
    }
}
