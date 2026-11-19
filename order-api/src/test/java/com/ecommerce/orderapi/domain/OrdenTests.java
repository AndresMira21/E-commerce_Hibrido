package com.ecommerce.orderapi.domain;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import static org.assertj.core.api.Assertions.*;

class OrdenTests {
    @Test void aceptaCorreoCompatibleConAnalytics() {
        Orden orden = builder().correo("aleja@example.com").build();
        assertThat(orden.getCorreo()).isEqualTo("aleja@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "aleja@localhost", "correo-invalido"})
    void rechazaCorreoIncompatibleAntesDeConstruirOrden(String correo) {
        assertThatThrownBy(() -> builder().correo(correo).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Correo invalido");
    }

    @ParameterizedTest
    @ValueSource(strings={"0","-1","-0.01"})
    void dineroRechazaMontosInvalidos(String value) {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new Dinero(new BigDecimal(value))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void valorObligatorio() {
        assertThatThrownBy(() -> new Dinero(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentificadorOrden(null)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(ints={0,-1})
    void detalleRechazaCantidadInvalida(int quantity) {
        assertThatThrownBy(() -> new DetalleItem("Libro",quantity)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void detalleRechazaProductoVacio() {
        assertThatThrownBy(() -> new DetalleItem(" ",1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DetalleItem(null,1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void builderExigeDatosCompletos() {
        assertThatThrownBy(() -> Orden.builder().build()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> builder().cliente(" ").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder().correo("invalido").build()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void builderConservaValoresYAuditaCreacion() {
        // Arrange
        Orden orden=builder().build();
        // Act / Assert
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PENDIENTE_PUBLICACION);
        assertThat(orden.detalle()).isEqualTo(new DetalleItem("Libro",1));
        assertThat(orden.dinero()).isEqualTo(new Dinero(new BigDecimal("15000")));
        assertThat(orden.getCreadaEn()).isNotNull();
        assertThat(orden.getCambios()).hasSize(1);
        assertThatThrownBy(() -> orden.getCambios().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void confirmacionEsTransicionAuditadaYNoSePuedeRepetir() {
        Orden orden=builder().build();
        orden.confirmarPublicacion();
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PUBLICADA);
        assertThat(orden.getCambios()).extracting(CambioOrden::getEstado)
                .containsExactly(EstadoOrden.PENDIENTE_PUBLICACION,EstadoOrden.PUBLICADA);
        assertThatThrownBy(orden::confirmarPublicacion).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(orden::registrarPublicacionIncierta).isInstanceOf(IllegalStateException.class);
    }
    @Test void falloQuedaInciertoSinReenvioAutomatico() {
        Orden orden=builder().build();
        orden.registrarPublicacionIncierta();
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PUBLICACION_INCIERTA);
        assertThat(orden.getCambios()).hasSize(2);
        assertThatThrownBy(orden::confirmarPublicacion).isInstanceOf(IllegalStateException.class);
    }
    private Orden.Builder builder() {
        return Orden.builder().identificador(IdentificadorOrden.nuevo()).cliente("Alejandra")
                .correo("alejandra@example.com").detalle(new DetalleItem("Libro",1))
                .dinero(new Dinero(new BigDecimal("15000")));
    }
}
