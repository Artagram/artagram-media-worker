package com.artagram.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El endpoint apunta a un servidor local que rechaza todo: S3 falla y el
 * servicio cae al disco local, que es el comportamiento de respaldo que se
 * quiere probar.
 */
class MediaStorageServiceTest {

    private static final ServidorQueRechaza S3_CAIDO = new ServidorQueRechaza();

    @org.junit.jupiter.api.AfterAll
    static void apagarS3() {
        S3_CAIDO.close();
    }

    @TempDir
    Path dir;

    private MediaStorageService servicio(String accessKey) {
        return new MediaStorageService(S3_CAIDO.url(), "us-east-1", "artagram-uploads",
                accessKey, "secreto", true, dir.toString());
    }

    @Test
    void siS3FallaGuardaYLeeDesdeElDiscoLocal() throws IOException {
        try (MediaStorageService storage = servicio("minioadmin")) {
            String clave = storage.guardarJpg("/miniaturas/p1.jpg", new byte[]{1, 2, 3});

            assertThat(clave).isEqualTo("miniaturas/p1.jpg");
            assertThat(Files.readAllBytes(dir.resolve("miniaturas/p1.jpg"))).containsExactly(1, 2, 3);
            assertThat(storage.descargar("miniaturas/p1.jpg")).containsExactly(1, 2, 3);
        }
    }

    @Test
    void normalizaUrlsDelBucketYDeMedia() throws IOException {
        Files.createDirectories(dir.resolve("publicaciones"));
        Files.write(dir.resolve("publicaciones/o.png"), new byte[]{7});

        try (MediaStorageService storage = servicio("minioadmin")) {
            assertThat(storage.descargar("http://localhost:9000/artagram-uploads/publicaciones/o.png"))
                    .containsExactly(7);
            assertThat(storage.descargar("/media/publicaciones/o.png")).containsExactly(7);
            assertThat(storage.descargar("publicaciones\\o.png")).containsExactly(7);
        }
    }

    @Test
    void siNoEstaEnNingunLadoLanzaIOException() {
        try (MediaStorageService storage = servicio("minioadmin")) {
            assertThatThrownBy(() -> storage.descargar("no/existe.jpg"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("no/existe.jpg");
            assertThatThrownBy(() -> storage.descargar(null)).isInstanceOf(IOException.class);
        }
    }

    @Test
    void sinAccessKeyUsaLasCredencialesPorDefectoYCierraSinErrores() {
        MediaStorageService storage = new MediaStorageService("", "us-east-1", "artagram-uploads",
                "", "", false, dir.toString());
        assertThatCode(storage::close).doesNotThrowAnyException();
    }

    @Test
    void conUnaRegionInvalidaNoHayClienteS3YSeUsaSoloElDisco() throws IOException {
        try (MediaStorageService storage = new MediaStorageService("::no-es-uri::", "us-east-1",
                "artagram-uploads", "minioadmin", "secreto", true, dir.toString())) {
            storage.guardarJpg("marcadas/p2.jpg", new byte[]{4});
            assertThat(storage.descargar("marcadas/p2.jpg")).containsExactly(4);
        }
    }
}
