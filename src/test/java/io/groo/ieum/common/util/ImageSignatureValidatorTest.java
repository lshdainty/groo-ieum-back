package io.groo.ieum.common.util;

import io.groo.core.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("이미지 매직 바이트 검증 테스트")
class ImageSignatureValidatorTest {
    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("profile", name, "image/png", content);
    }

    @Test
    @DisplayName("거절 - 이미지 확장자지만 내용이 텍스트 · HTML 이다")
    void rejectsNonImageContent() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ImageSignatureValidator.validate(file("a.png", html)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("거절 - 너무 짧거나 RIFF 인데 WebP 가 아니다")
    void rejectsShortAndRiffNonWebp() {
        assertThatThrownBy(() -> ImageSignatureValidator.validate(file("a.png", new byte[]{1, 2})))
                .isInstanceOf(InvalidValueException.class);
        byte[] wav = "RIFF....WAVEfmt ".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> ImageSignatureValidator.validate(file("a.webp", wav)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("통과 - JPEG · PNG · GIF · WebP 시그니처")
    void acceptsAllowedImages() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
        byte[] gif = "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);
        byte[] webp = "RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThatCode(() -> {
            ImageSignatureValidator.validate(file("a.jpg", jpeg));
            ImageSignatureValidator.validate(file("a.png", png));
            ImageSignatureValidator.validate(file("a.gif", gif));
            ImageSignatureValidator.validate(file("a.webp", webp));
        }).doesNotThrowAnyException();
    }
}
