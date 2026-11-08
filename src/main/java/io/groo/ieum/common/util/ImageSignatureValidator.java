package io.groo.ieum.common.util;

import io.groo.core.exception.ErrorCode;
import io.groo.core.exception.InvalidValueException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 업로드 파일의 앞부분(매직 바이트)이 허용한 이미지(JPEG · PNG · GIF · WebP)인지 확인한다.
 * 확장자만 보면 이름만 바꾼 임의 파일이 이미지로 저장된다.
 */
@Slf4j
public final class ImageSignatureValidator {
    private static final int HEADER_LENGTH = 12;

    private ImageSignatureValidator() {
    }

    public static void validate(MultipartFile file) {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(HEADER_LENGTH);
        } catch (IOException e) {
            log.warn("이미지 헤더를 읽지 못했습니다: filename={}", file.getOriginalFilename(), e);
            throw new InvalidValueException(ErrorCode.FILE_INVALID_TYPE);
        }

        if (!isAllowedImage(header)) {
            log.warn("이미지 내용이 아닌 파일 업로드 거절: filename={}", file.getOriginalFilename());
            throw new InvalidValueException(ErrorCode.FILE_INVALID_TYPE);
        }
    }

    static boolean isAllowedImage(byte[] h) {
        return isJpeg(h) || isPng(h) || isGif(h) || isWebp(h);
    }

    private static boolean isJpeg(byte[] h) {
        return h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] h) {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        return h.length >= sig.length && Arrays.equals(Arrays.copyOf(h, sig.length), sig);
    }

    private static boolean isGif(byte[] h) {
        return h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a';
    }

    private static boolean isWebp(byte[] h) {
        return h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }
}
