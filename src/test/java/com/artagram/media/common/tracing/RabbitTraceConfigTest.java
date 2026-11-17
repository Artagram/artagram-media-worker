package com.artagram.media.common.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

class RabbitTraceConfigTest {

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    private static Message mensaje() {
        return new Message(new byte[0], new MessageProperties());
    }

    @Test
    void alPublicarUsaLaTrazaDeLaPeticionOUnaNueva() {
        MDC.put("traceId", "traza-http");
        Message conTraza = RabbitTraceConfig.ponerTraza(mensaje());
        assertThat((String) conTraza.getMessageProperties().getHeader("X-Trace-Id")).isEqualTo("traza-http");

        MDC.clear();
        Message sinTraza = RabbitTraceConfig.ponerTraza(mensaje());
        assertThat((String) sinTraza.getMessageProperties().getHeader("X-Trace-Id")).hasSize(16);

        Message yaTraia = mensaje();
        yaTraia.getMessageProperties().setHeader("X-Trace-Id", "original");
        MDC.put("traceId", "otra");
        assertThat((String) RabbitTraceConfig.ponerTraza(yaTraia).getMessageProperties().getHeader("X-Trace-Id"))
                .isEqualTo("original");
    }

    @Test
    void alConsumirRestauraLaTrazaEnElMdc() {
        Message conTraza = mensaje();
        conTraza.getMessageProperties().setHeader("X-Trace-Id", "traza-evento");
        RabbitTraceConfig.tomarTraza(conTraza);
        assertThat(MDC.get("traceId")).isEqualTo("traza-evento");

        RabbitTraceConfig.tomarTraza(mensaje());
        assertThat(MDC.get("traceId")).hasSize(16).isNotEqualTo("traza-evento");
    }

    @Test
    void seAplicaALaPlantillaYAlContenedor() {
        RabbitTraceConfig config = new RabbitTraceConfig();
        RabbitTemplate plantilla = new RabbitTemplate();
        config.trazaAlPublicar().customize(plantilla);
        assertThat(plantilla.removeBeforePublishPostProcessor(RabbitTraceConfig.PONER_TRAZA)).isTrue();

        SimpleMessageListenerContainer contenedor = new SimpleMessageListenerContainer();
        config.trazaAlConsumir().configure(contenedor);
        assertThat(contenedor.removeAfterReceivePostProcessor(RabbitTraceConfig.TOMAR_TRAZA)).isTrue();
    }
}
