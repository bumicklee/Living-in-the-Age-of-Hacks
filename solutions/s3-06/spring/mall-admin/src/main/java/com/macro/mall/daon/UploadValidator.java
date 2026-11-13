package com.macro.mall.daon;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 다온마켓 3-6: 업로드 검사기. 누가 올리나(로그인·권한표) 다음의 네 단계를 맡는다.
 * 크기 → 내용(앞 바이트) → 이름·형식은 서버가 정함. 섹션 3 미션에서 secure-kit 부품(UploadValidator)으로 뽑는다.
 */
public final class UploadValidator {
    /** 코드 상한. 멀티파트 설정(application.yml max-file-size 5MB)이 더 큰 요청을 먼저 자른다 */
    public static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private UploadValidator() {
    }

    /** 받는 형식. 파일 앞부분 서명(매직 바이트)으로만 정한다. SVG·HTML은 스크립트를 실행할 수 있어 받지 않는다 */
    public enum ImageType {
        PNG("png", "image/png"), JPEG("jpg", "image/jpeg"), GIF("gif", "image/gif"), WEBP("webp", "image/webp");

        public final String extension;
        public final String mimeType;

        ImageType(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        static ImageType sniff(byte[] h) {
            if (h.length >= 8 && (h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && h[4] == 0x0d && h[5] == 0x0a && h[6] == 0x1a && h[7] == 0x0a) {
                return PNG;
            }
            if (h.length >= 3 && (h[0] & 0xff) == 0xff && (h[1] & 0xff) == 0xd8 && (h[2] & 0xff) == 0xff) {
                return JPEG;
            }
            if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8' && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
                return GIF;
            }
            if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
                return WEBP;
            }
            return null;
        }
    }

    /** 거절: HTTP 상태, 보안 이벤트 이름, 사람이 읽는 이유(올린 파일 이름·내용은 넣지 않는다) */
    public static final class Rejected extends Exception {
        public final int status;
        public final String event;
        public final String reason;

        Rejected(int status, String event, String reason) {
            super(reason);
            this.status = status;
            this.event = event;
            this.reason = reason;
        }
    }

    /** 크기 → 내용 순서로 본다. 통과하면 내용으로 정한 형식을 돌려준다 */
    public static ImageType check(MultipartFile file) throws IOException, Rejected {
        if (file == null || file.isEmpty()) {
            throw new Rejected(400, "upload.rejected.type", "빈 파일");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new Rejected(413, "upload.rejected.size", "크기 상한 초과(" + MAX_BYTES / 1024 / 1024 + "MB)");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(16);
        }
        ImageType type = ImageType.sniff(head);
        if (type == null) {
            throw new Rejected(400, "upload.rejected.type", "내용이 허용 형식이 아님: 감지=" + guess(head));
        }
        return type;
    }

    /** 저장 이름: 날짜 경로 + 무작위 ID + 내용으로 정한 확장자. 올린 사람이 보낸 이름은 쓰지 않는다(덮어쓰기·경로 바꾸기 방지) */
    public static String objectName(ImageType type) {
        return LocalDate.now().format(DAY) + "/" + UUID.randomUUID() + "." + type.extension;
    }

    private static String guess(byte[] h) {
        if (h.length == 0) {
            return "empty";
        }
        for (byte b : h) {
            int c = b & 0xff;
            if (c < 0x09 || (c > 0x0d && c < 0x20)) {
                return "binary";
            }
        }
        return h[0] == '<' ? "markup" : "text";
    }
}
