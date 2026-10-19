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

    public record ResultadoProcesamiento(byte[] thumbnailBytes, byte[] optimizedBytes, String blurhash) {
    }

    public ResultadoProcesamiento procesar(byte[] imagenOriginal, String autorNombre) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imagenOriginal));
        if (original == null) {
            throw new IOException("No se pudo decodificar la imagen");
        }

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

    private void aplicarMarcaDeAgua(BufferedImage image, String autor) {
        String texto = "ArtaGram • @" + (autor != null && !autor.isBlank() ? autor : "artista");
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));

        int x = image.getWidth() - 220;
        int y = image.getHeight() - 25;
        if (x < 10) x = 10;
        if (y < 25) y = 25;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(Color.BLACK);
        g.drawString(texto, x + 1, y + 1);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
        g.setColor(Color.WHITE);
        g.drawString(texto, x, y);
        g.dispose();
    }
}
