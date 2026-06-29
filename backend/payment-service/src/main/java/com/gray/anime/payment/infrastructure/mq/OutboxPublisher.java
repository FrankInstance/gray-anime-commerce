package com.gray.anime.payment.infrastructure.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gray.anime.payment.domain.OutboxEvent;
import com.gray.anime.payment.infrastructure.mapper.OutboxEventMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OutboxPublisher {
    private final OutboxEventMapper mapper;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventMapper mapper, RabbitTemplate rabbitTemplate) {
        this.mapper = mapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:5000}")
    public void publishNewEvents() {
        for (OutboxEvent event : mapper.selectList(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "NEW")
                .last("limit 20"))) {
            try {
                rabbitTemplate.convertAndSend("gray.events", event.getEventType(), event.getPayload());
                event.setStatus("PUBLISHED");
                event.setPublishedAt(LocalDateTime.now());
            } catch (Exception ignored) {
                event.setRetryCount(event.getRetryCount() + 1);
            }
            mapper.updateById(event);
        }
    }
}
