package com.artagram.media.common.tracing;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El traceId viaja en la cabecera {@code X-Trace-Id} de cada mensaje de
 * RabbitMQ: se pone al publicar (el de la peticion en curso o uno nuevo) y se
 * restaura en el MDC al consumir. Asi un evento se sigue en los logs de todos
 * los servicios que toca (escenario 10).
 */
@Configuration
public class RabbitTraceConfig {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    static final String MDC_TRACE_ID = "traceId";

    static final MessagePostProcessor PONER_TRAZA = RabbitTraceConfig::ponerTraza;
    static final MessagePostProcessor TOMAR_TRAZA = RabbitTraceConfig::tomarTraza;

    @Bean
    public RabbitTemplateCustomizer trazaAlPublicar() {
        return template -> template.addBeforePublishPostProcessors(PONER_TRAZA);
    }

    @Bean
    public ContainerCustomizer<SimpleMessageListenerContainer> trazaAlConsumir() {
        return container -> container.addAfterReceivePostProcessors(TOMAR_TRAZA);
    }

    static Message ponerTraza(Message mensaje) {
        if (mensaje.getMessageProperties().getHeader(HEADER_TRACE_ID) == null) {
            String actual = MDC.get(MDC_TRACE_ID);
            mensaje.getMessageProperties().setHeader(HEADER_TRACE_ID,
                    actual != null && !actual.isBlank() ? actual : nuevaTraza());
        }
        return mensaje;
    }

    static Message tomarTraza(Message mensaje) {
        Object traceId = mensaje.getMessageProperties().getHeader(HEADER_TRACE_ID);
        MDC.put(MDC_TRACE_ID, traceId != null ? traceId.toString() : nuevaTraza());
        return mensaje;
    }

    static String nuevaTraza() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
