package com.ecommerce.hibrido.notification.consumer;

import com.ecommerce.hibrido.notification.model.OrderCommand;
import com.ecommerce.hibrido.notification.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationQueueListener {
    private final NotificationService notificationService;

    public NotificationQueueListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void onMessage(OrderCommand command) {
        notificationService.notifyOrderReceived(command);
    }
}
