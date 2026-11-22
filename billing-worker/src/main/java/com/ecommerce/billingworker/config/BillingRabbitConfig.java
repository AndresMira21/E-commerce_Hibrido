package com.ecommerce.billingworker.config;

import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.RetryContext;

@Configuration
public class BillingRabbitConfig {

    @Bean
    public Declarables billingDeadLetterTopology() {
        DirectExchange exchange = new DirectExchange("billing_dlx", true, false);
        Queue queue = new Queue("billing_dead_queue", true);
        return new Declarables(exchange, queue,
                BindingBuilder.bind(queue).to(exchange).with("billing.failed"));
    }

    @Bean
    public RetryTemplate billingRetryTemplate() {
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
                            || cause instanceof InterruptedException
                            || cause instanceof org.springframework.amqp.support.converter.MessageConversionException
                            || cause instanceof org.springframework.messaging.converter.MessageConversionException) {
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
        template.registerListener(new org.springframework.retry.RetryListener() {
            @Override public <T, E extends Throwable> void onError(org.springframework.retry.RetryContext context,
                    org.springframework.retry.RetryCallback<T, E> callback, Throwable failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Intento RabbitMQ={} tipo={}",
                        context.getRetryCount(), failure.getClass().getSimpleName());
            }
        });
        return template;
    }

    @Bean
    public RetryOperationsInterceptor billingRetryInterceptor(RetryTemplate billingRetryTemplate) {
        return RetryInterceptorBuilder.stateless().retryOperations(billingRetryTemplate)
                .recoverer((message, failure) -> {
                    org.slf4j.LoggerFactory.getLogger(getClass()).error("Mensaje rechazado sin reencolar: cola={} tipo={}; revisar DLQ",
                            message.getMessageProperties().getConsumerQueue(), failure.getClass().getSimpleName());
                    throw new org.springframework.amqp.rabbit.support.ListenerExecutionFailedException("Intentos agotados o mensaje invalido", new AmqpRejectAndDontRequeueException("Rechazo sin reencolar", failure), message);
                }).build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory billingRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            RetryOperationsInterceptor billingRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(billingRetryInterceptor);
        return factory;
    }
}
