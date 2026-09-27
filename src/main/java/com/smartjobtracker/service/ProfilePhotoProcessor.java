package com.smartjobtracker.service;

import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Validates an uploaded profile photo by its file signature (not the client's Content-Type), then decodes it,
 * scales it to fit 512×512 and re-encodes it — which drops EXIF/GPS and any other embedded metadata.
 * Uses javax.imageio: the JDK reads JPEG and PNG; WebP is read by the TwelveMonkeys imageio-webp plugin.
 * The result is always JPEG or PNG.
 */
@Component
public class ProfilePhotoProcessor {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_SIDE = 512;
    /** Refuse absurd dimensions before decoding (decompression-bomb guard). */
    static final int MAX_SOURCE_PIXELS = 40_000_000;

    public enum Format { JPEG, PNG, WEBP }

    public ProfilePhotoProcessor() {
        // Plugins inside a Spring Boot jar may be missed by ImageIO's first automatic scan; register them explicitly.
        ImageIO.scanForPlugins();
    }

    public record Processed(byte[] data, String contentType) {}

    public static class InvalidPhotoException extends RuntimeException {
        public InvalidPhotoException(String message) { super(message); }
    }

    /** Detects the real format from magic bytes; null when it's none of JPEG/PNG/WebP. */
    public static Format sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return null;
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return Format.JPEG;
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) return Format.PNG;
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return Format.WEBP;
        return null;
    }

    public Processed process(byte[] upload) {
        if (upload == null || upload.length == 0) throw new InvalidPhotoException("The file is empty.");
        if (upload.length > MAX_BYTES) throw new InvalidPhotoException("Photos must be 2 MB or smaller.");
        Format format = sniff(upload);
        if (format == null) throw new InvalidPhotoException("Only JPEG, PNG or WebP images are accepted.");

        BufferedImage source = decode(upload, format);
        BufferedImage scaled = scale(source, format == Format.PNG && source.getColorModel().hasAlpha());
        try {
            // Keep PNG for images with transparency; everything else becomes JPEG.
            if (scaled.getColorModel().hasAlpha()) return new Processed(writePng(scaled), "image/png");
            return new Processed(writeJpeg(scaled), "image/jpeg");
        } catch (IOException e) {
            throw new InvalidPhotoException("The image could not be processed.");
        }
    }

    private BufferedImage decode(byte[] upload, Format format) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new InvalidPhotoException(format == Format.WEBP
                        ? "WebP photos aren't supported on this server yet; please upload a JPEG or PNG."
                        : "The image could not be read.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true); // ignore metadata while reading
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_SOURCE_PIXELS) throw new InvalidPhotoException("The image dimensions are too large.");
                BufferedImage image = reader.read(0);
                if (image == null) throw new InvalidPhotoException("The image could not be read.");
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidPhotoException ipe) throw ipe;
            throw new InvalidPhotoException("The image could not be read.");
        }
    }

    private BufferedImage scale(BufferedImage src, boolean keepAlpha) {
        int w = src.getWidth(), h = src.getHeight();
        double ratio = Math.min(1.0, Math.min((double) MAX_SIDE / w, (double) MAX_SIDE / h));
        int tw = Math.max(1, (int) Math.round(w * ratio)), th = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage out = new BufferedImage(tw, th, keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!keepAlpha) { g.setColor(java.awt.Color.WHITE); g.fillRect(0, 0, tw, th); }
            g.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), param); // null metadata: nothing carried over
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
