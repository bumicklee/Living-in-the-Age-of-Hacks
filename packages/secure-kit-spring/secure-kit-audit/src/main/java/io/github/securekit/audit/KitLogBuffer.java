package io.github.securekit.audit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 랩 전용: 실제로 쓴 로그 줄(가리기를 거친 뒤) 최근 200줄을 메모리에 둔다(GET /_kit/logs).
 * logback-spring.xml에 다른 출력과 같은 패턴(%kitmsg)으로 붙인다. 회사 서비스에는 붙이지 않는다.
 */
public class KitLogBuffer extends UnsynchronizedAppenderBase<ILoggingEvent> {
    public static final int KEEP = 200;
    private static final Deque<String> LINES = new ArrayDeque<>();
    private Encoder<ILoggingEvent> encoder;

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    @Override
    public void start() {
        if (encoder == null) {
            addError("KitLogBuffer에 encoder가 없다");
            return;
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String line = new String(encoder.encode(event), StandardCharsets.UTF_8).stripTrailing();
        synchronized (LINES) {
            LINES.addLast(line);
            while (LINES.size() > KEEP) LINES.removeFirst();
        }
    }

    public static List<String> lines() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }
}
