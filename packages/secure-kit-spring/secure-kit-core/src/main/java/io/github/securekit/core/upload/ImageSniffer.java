package io.github.securekit.core.upload;

/**
 * 파일 이름·Content-Type은 보낸 사람이 정한다. 그래서 내용 앞 바이트(매직 바이트)로 종류를 정한다.
 * 받는 것: PNG·JPEG·GIF·WebP. SVG(스크립트를 품을 수 있다)·HTML·실행 파일은 여기서 걸린다.
 */
public final class ImageSniffer {
    /** 알아낸 종류. 저장 확장자도 여기서 정한다(원래 이름은 쓰지 않는다) */
    public static final class Kind {
        private final String mime;
        private final String ext;

        Kind(String mime, String ext) {
            this.mime = mime;
            this.ext = ext;
        }

        public String mime() {
            return mime;
        }

        public String ext() {
            return ext;
        }
    }

    private ImageSniffer() {
    }

    /** 모르는 내용이면 null */
    public static Kind detect(byte[] b, int len) {
        if (starts(b, len, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return new Kind("image/png", "png");
        if (starts(b, len, 0xFF, 0xD8, 0xFF)) return new Kind("image/jpeg", "jpg");
        if (starts(b, len, 'G', 'I', 'F', '8', '7', 'a') || starts(b, len, 'G', 'I', 'F', '8', '9', 'a')) {
            return new Kind("image/gif", "gif");
        }
        if (len >= 12 && starts(b, len, 'R', 'I', 'F', 'F')
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new Kind("image/webp", "webp");
        }
        return null;
    }

    private static boolean starts(byte[] b, int len, int... magic) {
        if (len < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((b[i] & 0xff) != magic[i]) return false;
        }
        return true;
    }
}
