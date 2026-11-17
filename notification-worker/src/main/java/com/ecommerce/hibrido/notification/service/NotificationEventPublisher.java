package com.ecommerce.hibrido.notification.service;

import com.ecommerce.hibrido.notification.model.NotificationEvent;

public interface NotificationEventPublisher {
    void publish(NotificationEvent event);
}
