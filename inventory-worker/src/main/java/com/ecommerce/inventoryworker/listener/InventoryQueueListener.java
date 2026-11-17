package com.ecommerce.inventoryworker.listener;

import com.ecommerce.inventoryworker.command.OrderTaskCommand;
import com.ecommerce.inventoryworker.service.InventoryResult;
import com.ecommerce.inventoryworker.service.InventoryService;
import com.ecommerce.inventoryworker.event.InventoryEvent;
import com.ecommerce.inventoryworker.publisher.InventoryEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryQueueListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryQueueListener.class);
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final InventoryEventPublisher publisher;

    public InventoryQueueListener(ObjectMapper objectMapper, InventoryService inventoryService,
                                  InventoryEventPublisher publisher) {
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
        this.publisher = publisher;
    }

    @RabbitListener(queues = "inventory_queue", containerFactory = "inventoryRabbitListenerContainerFactory")
    public void onMessage(Message message) {
        OrderTaskCommand command;
        try {
            // Ignora __TypeId__: el contrato compartido es el cuerpo JSON.
            command = objectMapper.readValue(message.getBody(), OrderTaskCommand.class);
            if (command == null) {
                throw new IllegalArgumentException("El comando no puede ser null");
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Comando de inventario invalido", exception);
        }

        InventoryResult result;
        try {
            result = inventoryService.reserveStock(command.orderId(), command.productName(), command.quantity());
        } catch (IllegalArgumentException exception) {
            // Incluye ordenes duplicadas con datos inconsistentes, un error permanente.
            throw new AmqpRejectAndDontRequeueException("Datos de la orden inconsistentes", exception);
        }
        // Los errores de persistencia/concurrencia se propagan sin convertirse en errores de formato.
        // El proxy transaccional del servicio ya confirmo la reserva al retornar.
        // El listener no abre una transaccion y Kafka no altera la reserva confirmada.
        publisher.publish(new InventoryEvent(result.orderId(), result.status().name()));
        log.info("Evento de inventario confirmado en Kafka: orderId={}, status={}",
                result.orderId(), result.status());
        log.info("Resultado de inventario: orderId={}, producto={}, cantidad={}, status={}",
                result.orderId(), result.productName(), result.quantity(), result.status());
    }
}
