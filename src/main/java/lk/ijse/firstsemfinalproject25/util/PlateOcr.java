package lk.ijse.firstsemfinalproject25.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Cleans a picture up and reads its text with the Tesseract program installed on the computer. */
public final class PlateOcr {

    public enum Variant { GRAY, BINARY, INVERTED }

    public static final String SETTING_PATH = "ocr.tesseract";

    private static final String WHITELIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";

    private PlateOcr() { }

    /* ======================= find tesseract.exe ======================= */
    public static File findTesseract() {
        String saved = AppSettings.get(SETTING_PATH, "");
        if (!saved.isEmpty() && new File(saved).isFile()) {
            return new File(saved);
        }

        String[] common = {
                "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe",
                System.getProperty("user.home") + "\\AppData\\Local\\Programs\\Tesseract-OCR\\tesseract.exe",
                "/usr/bin/tesseract",
                "/usr/local/bin/tesseract",
                "/opt/homebrew/bin/tesseract"
        };
        for (String path : common) {
            File file = new File(path);
            if (file.isFile()) {
                return file;
            }
        }

        String systemPath = System.getenv("PATH");
        if (systemPath != null) {
            for (String dir : systemPath.split(File.pathSeparator)) {
                String clean = dir.trim().replace("\"", "");
                if (clean.isEmpty()) {
                    continue;
                }
                File exe = new File(clean, "tesseract.exe");
                if (exe.isFile()) {
                    return exe;
                }
                File unix = new File(clean, "tesseract");
                if (unix.isFile()) {
                    return unix;
                }
            }
        }
        return null;
    }

    /* ======================= read the characters ======================= */
    /**
     * psm 7 = one line of text, 6 = a block of text, 11 = scattered text (whole photos).
     */
    public static String read(BufferedImage image, int psm) throws Exception {
        File exe = findTesseract();
        if (exe == null) {
            throw new IllegalStateException("Tesseract OCR was not found on this computer.");
        }

        File png = File.createTempFile("plate-", ".png");
        try {
            ImageIO.write(image, "png", png);

            ProcessBuilder builder = new ProcessBuilder(
                    exe.getAbsolutePath(), png.getAbsolutePath(), "stdout",
                    "--psm", String.valueOf(psm),
                    "-l", "eng",
                    "-c", "tessedit_char_whitelist=" + WHITELIST);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process = builder.start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Tesseract took too long to answer.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Tesseract stopped with error code " + process.exitValue()
                        + ". Check that the English language data is installed.");
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            png.delete();
        }
    }

    /* ======================= clean the picture up ======================= */
    /** Scales the picture, turns it grey, stretches the contrast and (optionally) makes it black and white. */
    public static BufferedImage prepare(BufferedImage source, Variant variant, int targetWidth) {
        BufferedImage scaled = scale(source, targetWidth);
        int w = scaled.getWidth();
        int h = scaled.getHeight();

        int[] gray = new int[w * h];
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int v = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                gray[y * w + x] = v;
                hist[v]++;
            }
        }

        // stretch the contrast between the 2nd and the 98th percentile
        int total = w * h;
        int low = percentile(hist, total, 0.02);
        int high = percentile(hist, total, 0.98);
        if (high - low < 20) {
            low = 0;
            high = 255;
        }
        int[] hist2 = new int[256];
        for (int i = 0; i < gray.length; i++) {
            int v = (gray[i] - low) * 255 / (high - low);
            v = Math.max(0, Math.min(255, v));
            gray[i] = v;
            hist2[v]++;
        }
        int threshold = variant == Variant.BINARY ? otsu(hist2, total) : -1;

        // white margin around the picture helps Tesseract
        int margin = 24;
        BufferedImage out = new BufferedImage(w + 2 * margin, h + 2 * margin, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = out.getRaster();
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                raster.setSample(x, y, 0, 255);
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = gray[y * w + x];
                if (variant == Variant.BINARY) {
                    v = v > threshold ? 255 : 0;
                } else if (variant == Variant.INVERTED) {
                    v = 255 - v;
                }
                raster.setSample(x + margin, y + margin, 0, v);
            }
        }
        return out;
    }

    private static BufferedImage scale(BufferedImage src, int targetWidth) {
        int w = src.getWidth();
        int h = src.getHeight();
        double factor = Math.min((double) targetWidth / w, 4.0);
        int nw = Math.max(1, (int) Math.round(w * factor));
        int nh = Math.max(1, (int) Math.round(h * factor));

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static int percentile(int[] hist, int total, double p) {
        long target = (long) (total * p);
        long acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc >= target) {
                return i;
            }
        }
        return 255;
    }

    /** Otsu's method: the grey level that best separates dark text from the light background. */
    private static int otsu(int[] hist, int total) {
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += (double) i * hist[i];
        }
        double sumB = 0;
        double weightB = 0;
        double best = 0;
        int threshold = 127;
        for (int t = 0; t < 256; t++) {
            weightB += hist[t];
            if (weightB == 0) {
                continue;
            }
            double weightF = total - weightB;
            if (weightF == 0) {
                break;
            }
            sumB += (double) t * hist[t];
            double meanB = sumB / weightB;
            double meanF = (sum - sumB) / weightF;
            double between = weightB * weightF * (meanB - meanF) * (meanB - meanF);
            if (between > best) {
                best = between;
                threshold = t;
            }
        }
        return threshold;
    }
}