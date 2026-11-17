package com.artagram.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

class MediaRabbitMqConfigTest {

    private final MediaRabbitMqConfig config = new MediaRabbitMqConfig();

    @Test
    void declaraLaColaConDeadLetterYLaEnlazaAPostPublished() {
        TopicExchange exchange = config.eventsExchange();
        Queue cola = config.mediaPostPublishedQueue();
        Binding binding = config.mediaPostPublishedBinding(cola, exchange);

        assertThat(exchange.getName()).isEqualTo("artagram.events");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(cola.getName()).isEqualTo("media.post.published");
        assertThat(cola.getArguments())
                .containsEntry("x-dead-letter-exchange", "artagram.dlx")
                .containsEntry("x-dead-letter-routing-key", "media.post.published.dlq");
        assertThat(binding.getRoutingKey()).isEqualTo("post.published");
        assertThat(binding.getDestination()).isEqualTo("media.post.published");
        assertThat(config.jsonMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
