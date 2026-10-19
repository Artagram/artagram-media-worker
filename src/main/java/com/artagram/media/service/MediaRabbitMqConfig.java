package com.artagram.media.service;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaRabbitMqConfig {

    public static final String EXCHANGE = "artagram.events";
    public static final String DLX_EXCHANGE = "artagram.dlx";
    public static final String QUEUE_POST_PUBLISHED = "media.post.published";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue mediaPostPublishedQueue() {
        return QueueBuilder.durable(QUEUE_POST_PUBLISHED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_POST_PUBLISHED + ".dlq")
                .build();
    }

    @Bean
    public Binding mediaPostPublishedBinding(Queue mediaPostPublishedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(mediaPostPublishedQueue).to(eventsExchange).with("post.published");
    }
}
