package io.github.securekit.examples.demo;

import io.github.securekit.core.crypto.EncryptedFields;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 개인정보 저장소(메모리, DB 대신). 넣을 때 seal, 꺼낼 때 open. 검색은 검색용 해시끼리 비교한다 */
@Component
public class PiiStore {
    private final EncryptedFields fields;
    private final Map<Long, PiiRecord> rows = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public PiiStore(EncryptedFields fields) {
        this.fields = fields;
    }

    public long save(String phone) {
        PiiRecord r = fields.seal(new PiiRecord(ids.incrementAndGet(), phone));
        rows.put(r.getId(), r);
        return r.getId();
    }

    /** 복호화한 복사본(저장된 행은 암호문 그대로 둔다) */
    public PiiRecord find(long id) {
        PiiRecord r = rows.get(id);
        return r == null ? null : fields.open(r.copy());
    }

    /** 저장소에 실제로 있는 값(랩 전용 /_kit/pii/raw) */
    public String raw(long id) {
        PiiRecord r = rows.get(id);
        return r == null ? null : r.getPhone();
    }

    public List<Long> search(String phone) {
        String index = fields.cipher().blindIndex(phone);
        List<Long> out = new ArrayList<>();
        for (PiiRecord r : rows.values()) if (index.equals(r.getPhoneIndex())) out.add(r.getId());
        out.sort(Long::compare);
        return out;
    }
}
