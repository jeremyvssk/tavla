// Builds real and deliberately hostile image bytes for upload tests.
package com.iloveshopping.support;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    public static byte[] jpeg(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    /**
     * A valid 1x1 PNG whose header then claims the given size, with the header checksum fixed up.
     * A few hundred bytes on the wire that would need gigabytes of memory if anyone decoded it.
     */
    public static byte[] pngClaimingSize(int width, int height) {
        byte[] bytes = png(1, 1);
        // Signature (8) + IHDR length (4) + "IHDR" (4), then width and height as big-endian ints.
        ByteBuffer.wrap(bytes, 16, 8).putInt(width).putInt(height);
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        ByteBuffer.wrap(bytes, 29, 4).putInt((int) crc.getValue());
        return bytes;
    }

    /** A real PNG with an HTML payload appended after the image data: a classic polyglot. */
    public static byte[] pngWithTrailingScript() {
        byte[] image = png(4, 4);
        byte[] script = "<script>alert(document.cookie)</script>".getBytes();
        byte[] out = new byte[image.length + script.length];
        System.arraycopy(image, 0, out, 0, image.length);
        System.arraycopy(script, 0, out, image.length, script.length);
        return out;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
