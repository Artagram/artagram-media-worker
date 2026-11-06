package com.artagram.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class MediaEventListenerTest {

    @Test
    @SuppressWarnings("unchecked")
    void guardaLaVersionMarcadaYAvisaSuRuta() throws Exception {
        MediaStorageService storage = mock(MediaStorageService.class);
        ImageProcessingService imagenes = mock(ImageProcessingService.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        byte[] marcada = {9};
        when(storage.descargar("publicaciones/o.png")).thenReturn(new byte[]{1});
        when(imagenes.procesar(any(), eq("lucia")))
                .thenReturn(new ImageProcessingService.ResultadoProcesamiento(new byte[]{2}, marcada, "hash"));
        when(storage.guardarJpg(eq("miniaturas/p1.jpg"), any())).thenReturn("miniaturas/p1.jpg");
        when(storage.guardarJpg("marcadas/p1.jpg", marcada)).thenReturn("marcadas/p1.jpg");

        new MediaEventListener(storage, imagenes, rabbit).onPostPublished(Map.of(
                "publicacionId", "p1", "imagenUrl", "publicaciones/o.png", "autorId", "a1", "autorNombre", "lucia"));

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(MediaRabbitMqConfig.EXCHANGE), eq("media.processed"), evento.capture());
        assertThat((Map<String, Object>) evento.getValue()).containsEntry("marcaAguaRuta", "marcadas/p1.jpg");
    }
}
