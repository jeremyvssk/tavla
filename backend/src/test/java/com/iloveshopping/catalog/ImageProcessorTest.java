// Unit tests for upload validation and re-encoding: magic bytes, header dimension checks, stripped payloads.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.exception.InvalidImageException;
import com.iloveshopping.support.TestImages;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void png_isReencodedAsPng_withSameDimensions() throws Exception {
        ImageProcessor.ProcessedImage out = processor.process(TestImages.png(12, 7));
        assertThat(out.extension()).isEqualTo("png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(out.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(12);
        assertThat(decoded.getHeight()).isEqualTo(7);
    }

    @Test
    void jpeg_isReencodedAsJpeg() {
        ImageProcessor.ProcessedImage out = processor.process(TestImages.jpeg(10, 10));
        assertThat(out.extension()).isEqualTo("jpg");
        assertThat(out.bytes()[0]).isEqualTo((byte) 0xFF);
        assertThat(out.bytes()[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void theTypeComesFromTheBytes_notFromAnyName() {
        assertThatThrownBy(() -> processor.process("GIF89a not supported".getBytes()))
                .isInstanceOf(InvalidImageException.class).hasMessage("unsupported_image_type");
        assertThatThrownBy(() -> processor.process("<svg/>".getBytes()))
                .isInstanceOf(InvalidImageException.class).hasMessage("unsupported_image_type");
        assertThatThrownBy(() -> processor.process(new byte[0]))
                .isInstanceOf(InvalidImageException.class).hasMessage("unsupported_image_type");
    }

    @Test
    void validMagicBytes_withGarbageAfter_isInvalid() {
        byte[] truncated = Arrays.copyOf(TestImages.png(20, 20), 30);
        assertThatThrownBy(() -> processor.process(truncated))
                .isInstanceOf(InvalidImageException.class).hasMessage("invalid_image");
    }

    @Test
    void hugeClaimedDimensions_areRejectedBeforeDecoding() {
        assertThatThrownBy(() -> processor.process(TestImages.pngClaimingSize(50_000, 50_000)))
                .isInstanceOf(InvalidImageException.class).hasMessage("image_dimensions_too_large");
        assertThatThrownBy(() -> processor.process(TestImages.pngClaimingSize(ImageProcessor.MAX_SIDE + 1, 1)))
                .isInstanceOf(InvalidImageException.class).hasMessage("image_dimensions_too_large");
    }

    @Test
    void bytesAppendedAfterTheImage_doNotSurviveReencoding() {
        ImageProcessor.ProcessedImage out = processor.process(TestImages.pngWithTrailingScript());
        assertThat(new String(out.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
    }
}
