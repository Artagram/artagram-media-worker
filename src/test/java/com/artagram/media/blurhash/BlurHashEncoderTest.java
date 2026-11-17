package com.artagram.media.blurhash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BlurHashEncoderTest {

    @Test
    void codificaImagenSimpleCorrectamente() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 50, 100);
        g.setColor(Color.RED);
        g.fillRect(50, 0, 50, 100);
        g.dispose();

        String hash = BlurHashEncoder.encode(img);

        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        assertThat(hash.length()).isGreaterThan(10);
    }

    @Test
    void conUnSoloComponenteSoloCodificaElColorPromedio() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);

        assertThat(BlurHashEncoder.encode(img, 1, 1)).hasSize(6);
    }

    @Test
    void rechazaComponentesFueraDeRango() {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> BlurHashEncoder.encode(img, 0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlurHashEncoder.encode(img, 3, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
