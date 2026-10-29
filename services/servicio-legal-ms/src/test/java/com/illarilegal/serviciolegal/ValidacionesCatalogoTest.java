package com.illarilegal.serviciolegal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ValidacionesCatalogoTest {
    ServicioLegalRepository repo=mock(ServicioLegalRepository.class);
    ServicioLegalController controller=new ServicioLegalController(repo);
    @ParameterizedTest @ValueSource(strings={"-1.00","10.001","10000000000.00"})
    void invalidAmountsDoNotPersist(String price) {
        assertThatThrownBy(()->controller.crear(new ServicioLegalController.ServicioLegal(null,"Consulta","",
                new BigDecimal(price),"LEGAL","ACTIVO"))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repo);
    }
    @ParameterizedTest @ValueSource(strings={"ACTIVA","activo","INACTIVA","inactivo"})
    void stateAliasesNormalizeToTheSameContract(String state) {
        when(repo.save(any())).thenAnswer(i->i.getArgument(0));
        assertThat(controller.crear(new ServicioLegalController.ServicioLegal(null,"Consulta","",
                BigDecimal.TEN,"LEGAL",state)).estado()).isIn("ACTIVO","INACTIVO");
    }
    @Test void arbitraryStatesAreRejected() {
        assertThatThrownBy(()->controller.crear(new ServicioLegalController.ServicioLegal(null,"Consulta","",
                BigDecimal.TEN,"LEGAL","UNKNOWN"))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repo);
    }
}
