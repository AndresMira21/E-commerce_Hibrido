package com.ecommerce.orderapi.service;
import com.ecommerce.orderapi.domain.*;
import com.ecommerce.orderapi.repository.OrdenRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class OrdenPersistence {
    private final OrdenRepository repository;
    public OrdenPersistence(OrdenRepository repository) { this.repository=repository; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Orden guardarNueva(Orden orden) { return repository.saveAndFlush(orden); }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void registrarResultado(UUID id, boolean confirmada) {
        Orden orden=repository.findById(id).orElseThrow(() -> new IllegalStateException("Orden no encontrada"));
        if (confirmada) orden.confirmarPublicacion(); else orden.registrarPublicacionIncierta();
        repository.saveAndFlush(orden);
    }
}
