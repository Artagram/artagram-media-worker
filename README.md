# artagram-media-worker

Worker asíncrono para el procesamiento de medios (imágenes) en ArtaGram.

## Funcionalidades
- Escucha eventos `post.published` desde RabbitMQ (`artagram.events`).
- Descarga la imagen desde S3/MinIO.
- Genera miniatura de 400x400 px con Thumbnailator.
- Calcula el hash visual BlurHash (algoritmo puro Java).
- Genera versión optimizada de 1200x1200 px con marca de agua artística (`ArtaGram • @autor`).
- Publica evento `media.processed` para que `artagram-feed` actualice la publicación.

## Variables de Entorno
Ver `.env.example`.

## Diseño y Arquitectura

Worker sin superficie HTTP: una única cadena `RabbitMQ → descarga → procesamiento → subida → evento de salida`, dividida en clases con responsabilidad única.

```mermaid
classDiagram
    class MediaEventListener {
        -MediaStorageService storageService
        -ImageProcessingService imageProcessingService
        -RabbitTemplate rabbitTemplate
        +onPostPublished(Map~String,Object~) void
    }
    class ImageProcessingService {
        +procesar(byte[], String) ResultadoProcesamiento
        -aplicarMarcaDeAgua(BufferedImage, String) void
    }
    class ResultadoProcesamiento {
        <<record>>
        +byte[] thumbnailBytes
        +byte[] optimizedBytes
        +String blurhash
    }
    class MediaStorageService {
        -S3Client s3Client
        -String bucket
        -String localDir
        +descargar(String) byte[]
        +guardarMiniatura(String, byte[]) String
        -limpiarClave(String) String
    }
    class BlurHashEncoder {
        <<utility>>
        +encode(BufferedImage) String
        +encode(BufferedImage, int, int) String
    }
    class MediaRabbitMqConfig {
        <<configuration>>
        +QUEUE_POST_PUBLISHED : String
        +eventsExchange() TopicExchange
        +mediaPostPublishedQueue() Queue
        +mediaPostPublishedBinding(Queue, TopicExchange) Binding
    }
    class S3Client {
        <<AWS SDK>>
    }

    MediaEventListener --> MediaStorageService : usa
    MediaEventListener --> ImageProcessingService : usa
    MediaEventListener ..> MediaRabbitMqConfig : lee nombre de cola
    ImageProcessingService --> BlurHashEncoder : llama (estático)
    ImageProcessingService ..> ResultadoProcesamiento : retorna
    MediaStorageService --> S3Client : delega I/O
```

### Análisis SOLID

- **SRP — bien aplicado.** Cada clase tiene un único motivo de cambio: `MediaEventListener` orquesta, `ImageProcessingService` transforma píxeles, `MediaStorageService` resuelve I/O, `BlurHashEncoder` es un algoritmo puro, `MediaRabbitMqConfig` es infraestructura de mensajería. Ninguna mezcla reglas de negocio con I/O.
- **OCP — el punto más débil del servicio.** `ImageProcessingService.procesar()` es una secuencia fija de pasos (miniatura → blurhash → optimizada → marca de agua) escrita inline. Agregar un paso nuevo (ej. detección NSFW, conversión a WebP, un tamaño adicional) exige editar ese método directamente; no hay un pipeline de `ImageTransform` sobre el que extender.
- **Inconsistencia de arquitectura con el resto del sistema.** `MediaStorageService` reimplementa el acceso a almacenamiento con un fallback S3→disco local escrito a mano, mientras que otros servicios del mismo sistema (`ARTAGRAM-backv1`, `artagram-feed`) ya usan una interfaz `StorageProvider` con implementaciones intercambiables (Disk/S3/Azure). Este worker no sigue esa convención — es la oportunidad de extensibilidad más clara.
- **ISP/DIP — no se practica realmente.** No existen interfaces en el módulo: `MediaEventListener` depende de clases concretas (`MediaStorageService`, `ImageProcessingService`). Para un worker de este tamaño no es grave, pero limita el testeo con dobles de prueba y cualquier intercambio de implementación futuro.
- **Bug de diseño real encontrado:** la cola `media.post.published` se declara con `deadLetterExchange`/`deadLetterRoutingKey` (`MediaRabbitMqConfig`), pero `MediaEventListener.onPostPublished` atrapa **toda** excepción con `catch (Exception ex) { log.error(...) }` sin relanzarla. Como el mensaje se ACK-ea igual, nunca llega a activarse el dead-lettering configurado — un fallo real de procesamiento (imagen corrupta, S3 caído) se pierde en silencio en vez de ir a la DLQ. Vale la pena relanzar la excepción (o usar un `AmqpRejectAndDontRequeueException` tras N reintentos) para que la DLQ configurada cumpla su propósito.

### Extensibilidad recomendada

1. Introducir una interfaz `ImageTransform { BufferedImage aplicar(BufferedImage, Contexto) }` y convertir `procesar()` en un pipeline de pasos inyectados — nuevas transformaciones se agregan como un bean nuevo, no editando el método.
2. Alinear `MediaStorageService` con la interfaz `StorageProvider` que ya usa el resto del sistema, en vez de mantener su propio fallback S3/disco.
3. Extraer los parámetros de la marca de agua (texto, fuente, opacidad, posición) a una `@ConfigurationProperties` (`WatermarkProperties`), siguiendo el mismo patrón que `CanvasProperties`/`PartyProperties` en `artagram-canvas`.

### Preparación para AWS

- **Cómputo:** candidato ideal para **ECS Fargate** (o un *Service* de Kubernetes) con *auto-scaling basado en profundidad de cola* (CloudWatch metric sobre `ApproximateNumberOfMessagesVisible` de la cola), no en CPU/memoria — es un worker 100% pull-based, sin tráfico HTTP entrante que escalar por latencia.
- **Mensajería:** `RabbitMQ` → **Amazon MQ (RabbitMQ)** es el camino de menor cambio (mismo protocolo AMQP 0-9-1, solo cambia el host). Migrar a SQS es una alternativa a futuro, pero exige reemplazar `@RabbitListener`/`RabbitTemplate` por un cliente SQS y renunciar al *topic exchange* compartido con el resto de los servicios.
- **Almacenamiento:** `S3Client` ya apunta a un *endpoint* configurable (`artagram.storage.endpoint`) — contra AWS real basta con quitar el `endpointOverride` (o dejarlo vacío) y usar credenciales de un **IAM Role de la tarea ECS** en vez de access/secret key estáticas (`AwsBasicCredentials` → `DefaultCredentialsProvider`/`InstanceProfileCredentialsProvider`).
- **Secretos:** no hay contraseñas de base de datos aquí, pero las credenciales S3 (si no se usa IAM Role) y las credenciales de Amazon MQ deben ir en **Secrets Manager** o **SSM Parameter Store**, nunca en variables de entorno planas en la definición de la tarea.
- **Sin estado:** el worker no mantiene estado entre mensajes — cualquier número de réplicas puede consumir la misma cola sin coordinación adicional, lo que lo hace trivialmente horizontal-escalable.
