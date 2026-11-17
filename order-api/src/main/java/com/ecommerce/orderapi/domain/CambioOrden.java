package com.ecommerce.orderapi.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Embeddable
public class CambioOrden {
    @Enumerated(EnumType.STRING) @Column(name="estado", nullable=false)
    private EstadoOrden estado;
    @Column(name="fecha", nullable=false) private Instant fecha;
    @Column(name="motivo", nullable=false) private String motivo;
    protected CambioOrden() {}
    CambioOrden(EstadoOrden estado, Instant fecha, String motivo) {
        this.estado=estado; this.fecha=fecha; this.motivo=motivo;
    }
    public EstadoOrden getEstado() { return estado; }
    public Instant getFecha() { return fecha; }
    public String getMotivo() { return motivo; }
}
