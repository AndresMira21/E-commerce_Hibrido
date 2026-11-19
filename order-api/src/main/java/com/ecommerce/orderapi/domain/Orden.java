package com.ecommerce.orderapi.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity
@Table(name="ordenes", schema="orders")
public class Orden {
    @Id @Column(name="order_id", nullable=false, updatable=false) private UUID id;
    @Column(nullable=false) private String cliente;
    @Column(nullable=false) private String correo;
    @Column(name="producto", nullable=false) private String producto;
    @Column(nullable=false) private int cantidad;
    @Column(nullable=false, columnDefinition="numeric") private BigDecimal monto;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private EstadoOrden estado;
    @Column(name="creada_en", nullable=false, updatable=false) private Instant creadaEn;
    @Column(name="actualizada_en", nullable=false) private Instant actualizadaEn;
    @Version private Long version;
    @ElementCollection
    @CollectionTable(name="orden_cambios", schema="orders", joinColumns=@JoinColumn(name="order_id"))
    @OrderColumn(name="secuencia")
    private List<CambioOrden> cambios = new ArrayList<>();
    protected Orden() {}
    private Orden(Builder builder) {
        Objects.requireNonNull(builder.id, "Identificador obligatorio");
        Objects.requireNonNull(builder.detalle, "Detalle obligatorio");
        Objects.requireNonNull(builder.dinero, "Dinero obligatorio");
        if (builder.cliente == null || builder.cliente.isBlank() || builder.cliente.length()>255)
            throw new IllegalArgumentException("Cliente obligatorio, maximo 255 caracteres");
        if (builder.correo == null || builder.correo.isBlank() || builder.correo.length()>255
                || !builder.correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("Correo invalido");
        id=builder.id.valor(); cliente=builder.cliente; correo=builder.correo;
        producto=builder.detalle.producto(); cantidad=builder.detalle.cantidad(); monto=builder.dinero.valor();
        creadaEn=Instant.now();
        registrar(EstadoOrden.PENDIENTE_PUBLICACION, "Orden persistida antes del envio");
    }
    private void registrar(EstadoOrden nuevo, String motivo) {
        estado=nuevo; actualizadaEn=Instant.now();
        cambios.add(new CambioOrden(nuevo, actualizadaEn, motivo));
    }
    public void confirmarPublicacion() {
        exigirPendiente(); registrar(EstadoOrden.PUBLICADA, "Kafka confirmo el envio");
    }
    public void registrarPublicacionIncierta() {
        exigirPendiente();
        registrar(EstadoOrden.PUBLICACION_INCIERTA, "Envio no confirmado; requiere revision, sin reenvio automatico");
    }
    private void exigirPendiente() {
        if (estado != EstadoOrden.PENDIENTE_PUBLICACION)
            throw new IllegalStateException("La publicacion ya tiene un resultado registrado");
    }
    public IdentificadorOrden identificador() { return new IdentificadorOrden(id); }
    public DetalleItem detalle() { return new DetalleItem(producto, cantidad); }
    public Dinero dinero() { return new Dinero(monto); }
    public String getCliente() { return cliente; }
    public String getCorreo() { return correo; }
    public EstadoOrden getEstado() { return estado; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getActualizadaEn() { return actualizadaEn; }
    public List<CambioOrden> getCambios() { return List.copyOf(cambios); }
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private IdentificadorOrden id;
        private String cliente, correo;
        private DetalleItem detalle;
        private Dinero dinero;
        public Builder identificador(IdentificadorOrden id) { this.id=id; return this; }
        public Builder cliente(String cliente) { this.cliente=cliente; return this; }
        public Builder correo(String correo) { this.correo=correo; return this; }
        public Builder detalle(DetalleItem detalle) { this.detalle=detalle; return this; }
        public Builder dinero(Dinero dinero) { this.dinero=dinero; return this; }
        public Orden build() { return new Orden(this); }
    }
}
