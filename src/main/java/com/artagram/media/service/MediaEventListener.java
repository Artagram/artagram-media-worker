package com.artagram.media.service;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class MediaEventListener {

    private static final Logger log = LoggerFactory.getLogger(MediaEventListener.class);

    private final MediaStorageService storageService;
    private final ImageProcessingService imageProcessingService;
    private final RabbitTemplate rabbitTemplate;

    public MediaEventListener(MediaStorageService storageService,
                              ImageProcessingService imageProcessingService,
                              RabbitTemplate rabbitTemplate) {
        this.storageService = storageService;
        this.imageProcessingService = imageProcessingService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = MediaRabbitMqConfig.QUEUE_POST_PUBLISHED)
    public void onPostPublished(Map<String, Object> payload) {
        String publicacionId = (String) payload.get("publicacionId");
        String imagenUrl = (String) payload.get("imagenUrl");
        String autor = (String) payload.get("autorId");
        log.info("Procesando imagen para publicacionId={}, imagenUrl={}", publicacionId, imagenUrl);

        try {
            byte[] bytes = storageService.descargar(imagenUrl);
            var res = imageProcessingService.procesar(bytes, autor);

            String miniaturaRuta = "miniaturas/" + publicacionId + ".jpg";
            String miniaturaUrl = storageService.guardarMiniatura(miniaturaRuta, res.thumbnailBytes());

            Map<String, Object> eventoProcesado = Map.of(
                    "tipo", "MEDIA_PROCESADA",
                    "publicacionId", publicacionId,
                    "miniaturaUrl", miniaturaUrl,
                    "blurhash", res.blurhash(),
                    "timestamp", Instant.now().toString()
            );

            log.info("Publicando media.processed para publicacionId={}", publicacionId);
            rabbitTemplate.convertAndSend(MediaRabbitMqConfig.EXCHANGE, "media.processed", eventoProcesado);
        } catch (Exception ex) {
            log.error("Error al procesar medios de la publicacion {}: {}", publicacionId, ex.getMessage(), ex);
            throw new IllegalStateException("Fallo al procesar post.published", ex);
        }
    }
}
