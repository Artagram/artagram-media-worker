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
