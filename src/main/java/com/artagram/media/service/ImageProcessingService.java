package com.artagram.media.service;

import com.artagram.media.blurhash.BlurHashEncoder;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ImageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingService.class);
    private static final int TAMANO_MINIMO = 10;

    public ImageProcessingService() {
        // El lector de WEBP viene en una dependencia: dentro del jar de Spring Boot hay que registrarlo.
        ImageIO.scanForPlugins();
        log.info("Lectura de WEBP disponible: {}", ImageIO.getImageReadersByFormatName("webp").hasNext());
    }

    public record ResultadoProcesamiento(byte[] thumbnailBytes, byte[] optimizedBytes, String blurhash) {
    }

    public ResultadoProcesamiento procesar(byte[] imagenOriginal, String autorNombre) throws IOException {
        BufferedImage leida = ImageIO.read(new ByteArrayInputStream(imagenOriginal));
        if (leida == null) {
            throw new IOException("No se pudo decodificar la imagen");
        }
        BufferedImage original = sobreFondoBlanco(leida);

        BufferedImage thumb = Thumbnails.of(original)
                .size(400, 400)
                .outputFormat("jpg")
                .asBufferedImage();

        ByteArrayOutputStream thumbOs = new ByteArrayOutputStream();
        ImageIO.write(thumb, "jpg", thumbOs);
        byte[] thumbBytes = thumbOs.toByteArray();

        String blurhash = BlurHashEncoder.encode(thumb);

        BufferedImage optimized = Thumbnails.of(original)
                .size(1200, 1200)
                .outputFormat("jpg")
                .asBufferedImage();

        aplicarMarcaDeAgua(optimized, autorNombre);

        ByteArrayOutputStream optOs = new ByteArrayOutputStream();
        ImageIO.write(optimized, "jpg", optOs);
        byte[] optBytes = optOs.toByteArray();

        log.info("Imagen procesada: blurhash={}, thumbSize={}B, optSize={}B", blurhash, thumbBytes.length, optBytes.length);
        return new ResultadoProcesamiento(thumbBytes, optBytes, blurhash);
    }

    /** El JPG no admite transparencia: sin este paso ImageIO escribe un archivo vacio. */
    private BufferedImage sobreFondoBlanco(BufferedImage imagen) {
        BufferedImage plana = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = plana.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, plana.getWidth(), plana.getHeight());
        g.drawImage(imagen, 0, 0, null);
        g.dispose();
        return plana;
    }

    /** Discreta a proposito: esta es la imagen que se ve en el feed, no solo la descarga. */
    private void aplicarMarcaDeAgua(BufferedImage image, String autor) {
        String texto = "© " + (autor != null && !autor.isBlank() ? autor : "artista") + " · Artagram";
        int margen = Math.max(6, image.getWidth() / 100);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(fuenteQueCabe(g, texto, image.getWidth() - 2 * margen));

        int x = image.getWidth() - margen - g.getFontMetrics().stringWidth(texto);
        int y = image.getHeight() - margen;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        g.setColor(Color.BLACK);
        g.drawString(texto, x + 1, y + 1);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g.setColor(Color.WHITE);
        g.drawString(texto, x, y);
        g.dispose();
    }

    private Font fuenteQueCabe(Graphics2D g, String texto, int anchoMaximo) {
        int tamano = Math.max(TAMANO_MINIMO, anchoMaximo / 90);
        Font fuente = new Font(Font.SANS_SERIF, Font.PLAIN, tamano);
        while (tamano > TAMANO_MINIMO && g.getFontMetrics(fuente).stringWidth(texto) > anchoMaximo) {
            tamano--;
            fuente = fuente.deriveFont((float) tamano);
        }
        return fuente;
    }
}
