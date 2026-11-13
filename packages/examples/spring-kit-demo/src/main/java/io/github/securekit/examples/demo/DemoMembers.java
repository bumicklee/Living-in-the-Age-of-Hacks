package io.github.securekit.examples.demo;

import io.github.securekit.core.mask.Masking;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 가짜 회원 25명. 전화번호는 010-0000-NNNN 모양만 쓴다(실제 번호처럼 보이는 값을 두지 않는다) */
@Component
public class DemoMembers {
    private final List<Map<String, Object>> members = new ArrayList<>();

    public DemoMembers() {
        for (int i = 1; i <= 25; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i);
            m.put("name", String.format("회원%02d", i));
            m.put("phone", String.format("010-0000-%04d", i));
            members.add(m);
        }
    }

    /** 화면용: 전화번호를 가린 복사본 */
    public List<Map<String, Object>> page(int pageNum, int pageSize) {
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(members.size(), from + pageSize);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Map<String, Object> m = new LinkedHashMap<>(members.get(i));
            m.put("phone", Masking.phone((String) m.get("phone")));
            out.add(m);
        }
        return out;
    }

    public int total() {
        return members.size();
    }
}
