package io.github.securekit.web.part;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.upload.ImageSniffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 파일 업로드(3-6). 바꿔 쓰기: file.transferTo(원래이름) 대신 uploadValidator.store(내용).
 *
 * 1. 크기: 상한(기본 2MB)+1 바이트까지만 읽는다. 넘으면 413 + upload.rejected.size.
 * 2. 종류: 내용 앞 바이트로 PNG·JPEG·GIF·WebP만. 아니면 415 + upload.rejected.type.
 * 3. 이름: 무작위 32자 16진수 + 내용에서 정한 확장자. 원래 이름(../../evil.php)은 쓰지 않는다.
 * 4. 저장 폴더는 정적 파일로 바로 실행·제공되지 않는 곳으로 둔다(설정 secure-kit.upload.dir).
 */
public class UploadValidator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final long maxBytes;
    private final Path dir;

    public UploadValidator(long maxBytes, Path dir) {
        this.maxBytes = maxBytes;
        this.dir = dir;
    }

    /** 저장 결과 */
    public static final class Stored {
        private final String name;
        private final String type;

        Stored(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String name() {
            return name;
        }

        public String type() {
            return type;
        }
    }

    public Stored store(InputStream in) throws IOException {
        byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
        if (data.length > maxBytes) {
            SecurityEvents.warn("upload.rejected.size", "upload", "size:>" + maxBytes, "업로드 크기 상한 초과");
            throw new SecureKitException(413, "파일이 너무 큽니다");
        }
        ImageSniffer.Kind kind = ImageSniffer.detect(data, data.length);
        if (kind == null) {
            SecurityEvents.warn("upload.rejected.type", "upload", "size:" + data.length, "내용이 허용한 이미지가 아니다");
            throw new SecureKitException(415, "지원하지 않는 파일 형식입니다");
        }
        byte[] id = new byte[16];
        RANDOM.nextBytes(id);
        String name = HexFormat.of().formatHex(id) + "." + kind.ext();
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), data);
        return new Stored(name, kind.mime());
    }
}
