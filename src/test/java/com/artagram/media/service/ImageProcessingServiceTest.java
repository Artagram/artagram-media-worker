package com.artagram.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageProcessingServiceTest {

    private final ImageProcessingService service = new ImageProcessingService();

    private byte[] png(int ancho, int alto, int tipo, Color color) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, tipo);
        if (color != null) {
            Graphics2D g = imagen.createGraphics();
            g.setColor(color);
            g.fillRect(0, 0, ancho, alto);
            g.dispose();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private BufferedImage leer(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private int pixelesClaros(BufferedImage imagen, int desdeX, int desdeY) {
        int claros = 0;
        for (int x = desdeX; x < imagen.getWidth(); x++) {
            for (int y = desdeY; y < imagen.getHeight(); y++) {
                if (new Color(imagen.getRGB(x, y)).getRed() > 60) claros++;
            }
        }
        return claros;
    }

    @Test
    void unPngConTransparenciaProduceImagenesValidasSobreBlanco() throws IOException {
        var r = service.procesar(png(800, 600, BufferedImage.TYPE_INT_ARGB, null), "lucia");

        assertThat(leer(r.thumbnailBytes())).isNotNull();
        BufferedImage marcada = leer(r.optimizedBytes());
        assertThat(new Color(marcada.getRGB(5, 5)).getRed()).isGreaterThan(240);
    }

    @Test
    void laMarcaQuedaEnLaEsquinaInferiorDerecha() throws IOException {
        var r = service.procesar(png(1200, 900, BufferedImage.TYPE_INT_RGB, Color.BLACK), "lucia");

        BufferedImage marcada = leer(r.optimizedBytes());
        assertThat(pixelesClaros(marcada, marcada.getWidth() / 2, marcada.getHeight() * 9 / 10)).isGreaterThan(100);
        assertThat(new Color(marcada.getRGB(10, 10)).getRed()).isLessThan(30);
    }

    @Test
    void unNombreLargoCabeEnUnaImagenAngosta() throws IOException {
        var r = service.procesar(png(300, 900, BufferedImage.TYPE_INT_RGB, Color.BLACK), "artista_con_un_nombre_bastante_largo");

        BufferedImage marcada = leer(r.optimizedBytes());
        assertThat(pixelesClaros(marcada, 0, marcada.getHeight() * 9 / 10)).isGreaterThan(100);
    }

    @Test
    void leeWebp() throws IOException {
        var r = service.procesar(Files.readAllBytes(Path.of("src/test/resources/muestra.webp")), "lucia");

        assertThat(leer(r.optimizedBytes())).isNotNull();
    }

    @Test
    void bytesQueNoSonImagenLanzanIOException() {
        assertThatThrownBy(() -> service.procesar(new byte[]{1, 2, 3}, "lucia"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("decodificar");
    }

    @Test
    void conUnNombreLargoLaMarcaDeAguaReduceLaFuenteYTerminaElProceso() throws IOException {
        byte[] original = png(300, 200, BufferedImage.TYPE_INT_RGB, Color.DARK_GRAY);

        var resultado = service.procesar(original, "un_nombre_de_artista_muy_largo_que_no_cabe_en_la_imagen_".repeat(4));

        assertThat(leer(resultado.optimizedBytes())).isNotNull();
        assertThat(resultado.blurhash()).isNotBlank();
    }

    @Test
    void resultadoComparaLosArraysPorContenido() {
        var a = new ImageProcessingService.ResultadoProcesamiento(new byte[]{1}, new byte[]{2, 3}, "h");
        var b = new ImageProcessingService.ResultadoProcesamiento(new byte[]{1}, new byte[]{2, 3}, "h");
        var distinto = new ImageProcessingService.ResultadoProcesamiento(new byte[]{9}, new byte[]{2, 3}, "h");
        var vacio = new ImageProcessingService.ResultadoProcesamiento(null, null, null);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(distinto).isNotEqualTo("otro");
        assertThat(a.toString()).contains("thumbnailBytes=1 bytes", "optimizedBytes=2 bytes", "blurhash=h");
        assertThat(vacio.toString()).contains("thumbnailBytes=0 bytes");
        assertThat(vacio).isEqualTo(new ImageProcessingService.ResultadoProcesamiento(null, null, null));
    }
}
