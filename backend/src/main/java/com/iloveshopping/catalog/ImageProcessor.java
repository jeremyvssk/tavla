// Validates an uploaded image by its real bytes and re-encodes it, so the uploaded bytes never reach disk.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.exception.InvalidImageException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * The declared Content-Type and the filename are ignored: both are client-controlled. The type
 * comes from the file's magic bytes, the dimensions are checked from the header before any pixel
 * is decoded (a 50000x50000 PNG is a few KB on the wire and gigabytes in memory), and the output
 * is a freshly encoded file. Anything hidden in the original, including EXIF location data,
 * does not survive.
 */
@Component
public class ImageProcessor {

    static final int MAX_SIDE = 8_000;
    static final long MAX_PIXELS = 40_000_000L;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    public record ProcessedImage(byte[] bytes, String extension) {
    }

    private enum Format {
        JPEG("jpeg", "jpg"), PNG("png", "png");

        final String imageIoName;
        final String extension;

        Format(String imageIoName, String extension) {
            this.imageIoName = imageIoName;
            this.extension = extension;
        }
    }

    public ProcessedImage process(byte[] data) {
        Format format = sniff(data);
        BufferedImage image = decode(data, format);
        return new ProcessedImage(encode(image, format), format.extension);
    }

    private Format sniff(byte[] data) {
        if (startsWith(data, PNG_MAGIC)) {
            return Format.PNG;
        }
        if (startsWith(data, JPEG_MAGIC)) {
            return Format.JPEG;
        }
        throw new InvalidImageException("unsupported_image_type");
    }

    private BufferedImage decode(byte[] data, Format format) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.imageIoName);
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                        || (long) width * height > MAX_PIXELS) {
                    throw new InvalidImageException("image_dimensions_too_large");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new InvalidImageException("invalid_image");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Decoders throw all sorts of unchecked exceptions on hostile input; all mean "not an image".
            throw new InvalidImageException("invalid_image");
        }
    }

    private byte[] encode(BufferedImage image, Format format) {
        BufferedImage output = format == Format.JPEG ? toRgb(image) : image;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(output, format.imageIoName, out)) {
                throw new InvalidImageException("invalid_image");
            }
        } catch (IOException e) {
            throw new InvalidImageException("invalid_image");
        }
        return out.toByteArray();
    }

    // The JPEG writer rejects alpha channels and unusual colour models; flatten onto white first.
    private BufferedImage toRgb(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data != null && data.length >= prefix.length
                && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
