package com.smartjobtracker.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ProfilePhotoProcessorTest {
    private final ProfilePhotoProcessor processor = new ProfilePhotoProcessor();

    public static byte[] image(int w, int h, String format, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(alpha ? new Color(30, 120, 200, 128) : new Color(30, 120, 200));
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void rejectsBytesThatAreNotAnImageEvenWithAPlausibleName() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertNull(ProfilePhotoProcessor.sniff(html));
        var ex = assertThrows(ProfilePhotoProcessor.InvalidPhotoException.class, () -> processor.process(html));
        assertTrue(ex.getMessage().contains("JPEG, PNG or WebP"));
    }

    @Test
    void rejectsOtherImageTypes() throws Exception {
        byte[] gif = image(10, 10, "gif", false);
        assertNull(ProfilePhotoProcessor.sniff(gif));
        assertThrows(ProfilePhotoProcessor.InvalidPhotoException.class, () -> processor.process(gif));
    }

    @Test
    void rejectsOversizeAndEmptyUploads() throws Exception {
        byte[] big = Arrays.copyOf(image(10, 10, "jpg", false), ProfilePhotoProcessor.MAX_BYTES + 1);
        var ex = assertThrows(ProfilePhotoProcessor.InvalidPhotoException.class, () -> processor.process(big));
        assertTrue(ex.getMessage().contains("2 MB"));
        assertThrows(ProfilePhotoProcessor.InvalidPhotoException.class, () -> processor.process(new byte[0]));
    }

    @Test
    void jpegWithTruncatedBodyIsRejectedNotCrashing() throws Exception {
        byte[] jpeg = image(200, 200, "jpg", false);
        byte[] truncated = Arrays.copyOf(jpeg, 40); // valid magic bytes, corrupt content
        assertEquals(ProfilePhotoProcessor.Format.JPEG, ProfilePhotoProcessor.sniff(truncated));
        assertThrows(ProfilePhotoProcessor.InvalidPhotoException.class, () -> processor.process(truncated));
    }

    @Test
    void webpIsDecodedAndReencoded() throws Exception {
        // A real 1×1 lossless WebP.
        byte[] webp = java.util.Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        assertEquals(ProfilePhotoProcessor.Format.WEBP, ProfilePhotoProcessor.sniff(webp));
        var result = processor.process(webp);
        assertTrue(result.contentType().equals("image/png") || result.contentType().equals("image/jpeg"));
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result.data()));
        assertEquals(1, out.getWidth());
    }

    @Test
    void corruptWebpIsRejected() {
        byte[] webpHeader = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(ProfilePhotoProcessor.Format.WEBP, ProfilePhotoProcessor.sniff(webpHeader));
        assertThrows(ProfilePhotoProcessor.InvalidPhotoException.class, () -> processor.process(webpHeader));
    }

    @Test
    void largeJpegIsScaledToFit512AndReencoded() throws Exception {
        var result = processor.process(image(1600, 900, "jpg", false));
        assertEquals("image/jpeg", result.contentType());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result.data()));
        assertEquals(512, out.getWidth());
        assertEquals(288, out.getHeight());
        // Re-encoded from pixels with no metadata: no EXIF (APP1) segment right after SOI.
        assertFalse(result.data()[2] == (byte) 0xFF && result.data()[3] == (byte) 0xE1);
    }

    @Test
    void transparentPngStaysPngAndSmallImagesAreNotUpscaled() throws Exception {
        var result = processor.process(image(100, 60, "png", true));
        assertEquals("image/png", result.contentType());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result.data()));
        assertEquals(100, out.getWidth());
        assertTrue(out.getColorModel().hasAlpha());

        assertEquals("image/jpeg", processor.process(image(100, 60, "png", false)).contentType());
    }
}
