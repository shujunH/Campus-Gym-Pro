package com.campusgym.pro.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${gym.reservation.queue.exchange}")
    private String exchange;

    @Value("${gym.reservation.queue.queue}")
    private String queue;

    @Value("${gym.reservation.queue.routing-key}")
    private String routingKey;

    @Bean
    public TopicExchange reservationExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue reservationQueue() {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding reservationBinding() {
        return BindingBuilder.bind(reservationQueue())
                .to(reservationExchange())
                .with(routingKey);
    }
}