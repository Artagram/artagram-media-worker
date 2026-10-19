package com.artagram.media.blurhash;

import java.awt.image.BufferedImage;

public final class BlurHashEncoder {

    private static final char[] BASE83 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~".toCharArray();

    private BlurHashEncoder() {
    }

    public static String encode(BufferedImage image) {
        return encode(image, 4, 3);
    }

    public static String encode(BufferedImage image, int xComp, int yComp) {
        if (xComp < 1 || xComp > 9 || yComp < 1 || yComp > 9) {
            throw new IllegalArgumentException("Componentes deben estar entre 1 y 9");
        }
        int width = image.getWidth();
        int height = image.getHeight();

        double[][] factors = new double[yComp * xComp][3];

        for (int j = 0; j < yComp; j++) {
            for (int i = 0; i < xComp; i++) {
                double normalisation = (i == 0 && j == 0) ? 1.0 : 2.0;
                double r = 0, g = 0, b = 0;

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int rgb = image.getRGB(x, y);
                        double basis = Math.cos((Math.PI * i * x) / width) * Math.cos((Math.PI * j * y) / height);
                        r += basis * sRgbToLinear((rgb >> 16) & 255);
                        g += basis * sRgbToLinear((rgb >> 8) & 255);
                        b += basis * sRgbToLinear(rgb & 255);
                    }
                }
                double scale = normalisation / (width * height);
                int idx = j * xComp + i;
                factors[idx][0] = r * scale;
                factors[idx][1] = g * scale;
                factors[idx][2] = b * scale;
            }
        }

        StringBuilder hash = new StringBuilder();
        int sizeFlag = (xComp - 1) + (yComp - 1) * 9;
        encodeBase83(sizeFlag, 1, hash);

        double maximumValue;
        if (factors.length > 1) {
            double actualMax = 0;
            for (int i = 1; i < factors.length; i++) {
                actualMax = Math.max(Math.abs(factors[i][0]), actualMax);
                actualMax = Math.max(Math.abs(factors[i][1]), actualMax);
                actualMax = Math.max(Math.abs(factors[i][2]), actualMax);
            }
            int quantisedMax = Math.max(0, Math.min(82, (int) Math.floor(actualMax * 166.0 - 0.5)));
            maximumValue = (quantisedMax + 1) / 166.0;
            encodeBase83(quantisedMax, 1, hash);
        } else {
            maximumValue = 1.0;
            encodeBase83(0, 1, hash);
        }

        double[] dc = factors[0];
        encodeBase83(encodeDc(dc), 4, hash);

        for (int i = 1; i < factors.length; i++) {
            encodeBase83(encodeAc(factors[i], maximumValue), 2, hash);
        }

        return hash.toString();
    }

    private static double sRgbToLinear(int value) {
        double v = value / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static int linearToSRgb(double value) {
        double v = Math.max(0.0, Math.min(1.0, value));
        return (int) (v <= 0.0031308 ? v * 12.92 * 255.0 + 0.5 : (1.055 * Math.pow(v, 1.0 / 2.4) - 0.055) * 255.0 + 0.5);
    }

    private static int encodeDc(double[] value) {
        int r = linearToSRgb(value[0]);
        int g = linearToSRgb(value[1]);
        int b = linearToSRgb(value[2]);
        return (r << 16) + (g << 8) + b;
    }

    private static int encodeAc(double[] value, double maximumValue) {
        int quantR = Math.max(0, Math.min(18, (int) Math.floor(signPow(value[0] / maximumValue, 0.5) * 9.0 + 9.5)));
        int quantG = Math.max(0, Math.min(18, (int) Math.floor(signPow(value[1] / maximumValue, 0.5) * 9.0 + 9.5)));
        int quantB = Math.max(0, Math.min(18, (int) Math.floor(signPow(value[2] / maximumValue, 0.5) * 9.0 + 9.5)));
        return quantR * 19 * 19 + quantG * 19 + quantB;
    }

    private static double signPow(double val, double exp) {
        return Math.copySign(Math.pow(Math.abs(val), exp), val);
    }

    private static void encodeBase83(int value, int length, StringBuilder sb) {
        for (int i = 1; i <= length; i++) {
            int digit = (value / (int) Math.pow(83, length - i)) % 83;
            sb.append(BASE83[digit]);
        }
    }
}
