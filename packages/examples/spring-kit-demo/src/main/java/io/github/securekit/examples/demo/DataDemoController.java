package io.github.securekit.examples.demo;

import io.github.securekit.audit.Audited;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.mask.Masking;
import io.github.securekit.web.KitPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 개인정보 쪽 부품: field-crypto(12) · audit(15) */
@RestController
public class DataDemoController {
    private final PiiStore pii;
    private final DemoMembers members;

    public DataDemoController(PiiStore pii, DemoMembers members) {
        this.pii = pii;
        this.members = members;
    }

    @PostMapping("/kit/pii")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        KitPrincipal.requireUser(request);
        Object phone = body.get("phone");
        if (phone == null) throw new SecureKitException(400, "전화번호가 필요합니다");
        return ResponseEntity.status(201).body(Map.of("id", pii.save(phone.toString())));
    }

    @GetMapping("/kit/pii/search")
    public Map<String, Object> search(@RequestParam("phone") String phone, HttpServletRequest request) {
        KitPrincipal.requireUser(request);
        return Map.of("ids", pii.search(phone));
    }

    @GetMapping("/kit/pii/{id}")
    public Map<String, Object> read(@PathVariable("id") long id, HttpServletRequest request) {
        KitPrincipal.requireUser(request);
        PiiRecord r = pii.find(id);
        if (r == null) throw new SecureKitException(404, "찾을 수 없습니다");
        return Map.of("id", r.getId(), "phone", Masking.phone(r.getPhone()));
    }

    /** 관리자 회원 목록. 감사 기록은 애스펙트가 남기고, 권한 확인은 여기서 한다(거부도 기록된다) */
    @GetMapping("/kit/admin/members")
    @Audited(action = "member.list", resource = "member")
    public Map<String, Object> list(@RequestParam(name = "pageNum", defaultValue = "1") int pageNum,
                                    @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
                                    HttpServletRequest request) {
        KitPrincipal.requireUser(request).requireRole("admin");
        int size = Math.min(Math.max(pageSize, 1), 50);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", members.page(Math.max(pageNum, 1), size));
        out.put("pageNum", pageNum);
        out.put("pageSize", size);
        out.put("total", members.total());
        return out;
    }
}
