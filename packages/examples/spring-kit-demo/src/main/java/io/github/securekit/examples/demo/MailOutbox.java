package io.github.securekit.examples.demo;

import io.github.securekit.core.KitResettable;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** 메일 서버 대신: 보낸 메일 최근 50통(랩 전용 /_kit/outbox) */
@Component
public class MailOutbox implements KitResettable {
    private final Deque<Map<String, String>> mails = new ArrayDeque<>();

    public synchronized void send(String to, String subject, String body) {
        mails.addLast(Map.of("to", to, "subject", subject, "body", body));
        while (mails.size() > 50) mails.removeFirst();
    }

    public synchronized List<Map<String, String>> recent() {
        return new ArrayList<>(mails);
    }

    @Override
    public synchronized void reset() {
        mails.clear();
    }
}
