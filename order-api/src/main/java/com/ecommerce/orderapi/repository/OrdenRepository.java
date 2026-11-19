package com.ecommerce.orderapi.repository;
import com.ecommerce.orderapi.domain.Orden;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrdenRepository extends JpaRepository<Orden, UUID> {}
