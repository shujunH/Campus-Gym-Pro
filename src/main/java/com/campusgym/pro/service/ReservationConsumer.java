package com.campusgym.pro.service;

import com.campusgym.pro.entity.Reservation;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationConsumer {

    private final ReservationService reservationService;

    @RabbitListener(queues = "${gym.reservation.queue.queue}", concurrency = "5")
    public void handleReservation(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
            Reservation reservation = (Reservation) converter.fromMessage(message);
            log.info("消费预约消息 deliveryTag={}, userId={}, slotId={}",
                    deliveryTag, reservation.getUserId(), reservation.getSlotId());

            reservationService.processReservation(reservation);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("消费预约消息失败 deliveryTag={}", deliveryTag, e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("消息Nack失败", ex);
            }
        }
    }
}