package com.ecommerce.inventoryworker.config;

import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.RetryContext;

@Configuration
public class InventoryRabbitConfig {

    @Bean
    public Declarables inventoryDeadLetterTopology() {
        DirectExchange exchange = new DirectExchange("inventory_dlx", true, false);
        Queue queue = new Queue("inventory_dead_queue", true);
        return new Declarables(exchange, queue,
                BindingBuilder.bind(queue).to(exchange).with("inventory.failed"));
    }

    @Bean
    public RetryTemplate inventoryRetryTemplate() {
        RetryTemplate template = new RetryTemplate();
        // Recorre las causas: el contenedor puede envolver la excepcion del listener.
        template.setRetryPolicy(new SimpleRetryPolicy(3,
                Map.of(Exception.class, true, AmqpRejectAndDontRequeueException.class, false,
                        IllegalArgumentException.class, false, InterruptedException.class, false), true) {
            @Override
            public boolean canRetry(RetryContext context) {
                Throwable cause = context.getLastThrowable();
                while (cause != null) {
                    if (cause instanceof AmqpRejectAndDontRequeueException
                            || cause instanceof IllegalArgumentException
                            || cause instanceof InterruptedException) {
                        return false;
                    }
                    if (cause == cause.getCause()) { break; }
                    cause = cause.getCause();
                }
                return super.canRetry(context);
            }
        });
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(200);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(400);
        template.setBackOffPolicy(backOff);
        return template;
    }

    @Bean
    public RetryOperationsInterceptor inventoryRetryInterceptor(RetryTemplate inventoryRetryTemplate) {
        return RetryInterceptorBuilder.stateless().retryOperations(inventoryRetryTemplate)
                .recoverer(new RejectAndDontRequeueRecoverer()).build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory inventoryRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            RetryOperationsInterceptor inventoryRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(inventoryRetryInterceptor);
        return factory;
    }
}
