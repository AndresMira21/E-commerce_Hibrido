package com.ecommerce.hibrido.notification.service;

import com.ecommerce.hibrido.notification.model.NotificationEvent;
import com.ecommerce.hibrido.notification.model.OrderCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationEventPublisher publisher;

    public NotificationService(NotificationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public NotificationEvent notifyOrderReceived(OrderCommand command) {
        if (!"PROCESS_ORDER".equals(command.accion())) {
            throw new IllegalArgumentException("Acción no soportada: " + command.accion());
        }
        log.info("Pedido recibido: order_id={}, item={}, cantidad={}. Correo simulado al cliente.",
                command.order_id(), command.item(), command.cantidad());
        var event = new NotificationEvent(command.order_id(), "EMAIL_SENT");
        publisher.publish(event);
        return event;
    }
}
