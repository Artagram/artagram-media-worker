package com.artagram.media.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private final S3Client s3Client;
    private final String bucket;
    private final String localDir;

    public MediaStorageService(
            @Value("${artagram.storage.endpoint:http://localhost:9000}") String endpoint,
            @Value("${artagram.storage.region:us-east-1}") String region,
            @Value("${artagram.storage.bucket:artagram-uploads}") String bucket,
            @Value("${artagram.storage.access-key:minioadmin}") String accessKey,
            @Value("${artagram.storage.secret-key:minioadmin}") String secretKey,
            @Value("${artagram.storage.path-style-access:true}") boolean pathStyle,
            @Value("${artagram.storage.local-dir:uploads}") String localDir) {
        this.bucket = bucket;
        this.localDir = localDir;

        S3Client client = null;
        try {
            AwsCredentialsProvider creds = (accessKey != null && !accessKey.isBlank())
                    ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                    : DefaultCredentialsProvider.create();
            var builder = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(creds)
                    .forcePathStyle(pathStyle);
            if (endpoint != null && !endpoint.isBlank()) {
                builder.endpointOverride(URI.create(endpoint));
            }
            client = builder.build();
        } catch (Exception ex) {
            log.warn("No se pudo iniciar cliente S3 en MediaStorageService: {}", ex.getMessage());
        }
        this.s3Client = client;
    }

    public byte[] descargar(String rutaOUrl) throws IOException {
        String key = limpiarClave(rutaOUrl);
        if (s3Client != null) {
            try {
                ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(bucket).key(key).build());
                return bytes.asByteArray();
            } catch (Exception ex) {
                log.warn("No se pudo descargar de S3 con clave {}: {}", key, ex.getMessage());
            }
        }

        Path localPath = Path.of(localDir, key).normalize();
        if (Files.exists(localPath)) {
            return Files.readAllBytes(localPath);
        }

        throw new IOException("Archivo no encontrado ni en S3 ni en disco local: " + key);
    }

    public String guardarMiniatura(String rutaDestino, byte[] bytes) throws IOException {
        String key = limpiarClave(rutaDestino);
        if (s3Client != null) {
            try {
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType("image/jpeg")
                                .build(),
                        RequestBody.fromInputStream(new ByteArrayInputStream(bytes), bytes.length));
                return key;
            } catch (Exception ex) {
                log.warn("Fallo guardado en S3, guardando en disco local: {}", ex.getMessage());
            }
        }

        Path localPath = Path.of(localDir, key).normalize();
        Files.createDirectories(localPath.getParent());
        Files.write(localPath, bytes);
        return key;
    }

    private String limpiarClave(String ruta) {
        if (ruta == null) return "";
        if (ruta.contains("/" + bucket + "/")) {
            ruta = ruta.substring(ruta.indexOf("/" + bucket + "/") + bucket.length() + 2);
        } else if (ruta.startsWith("/media/")) {
            ruta = ruta.substring("/media/".length());
        }
        return ruta.replace('\\', '/').replaceAll("^/+", "");
    }
}
