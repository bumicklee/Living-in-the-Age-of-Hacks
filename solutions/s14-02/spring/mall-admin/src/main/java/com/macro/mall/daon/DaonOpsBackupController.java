package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * [다온마켓 14-2 정답] 「백업 목록 보기」를 관리자 앱에서 뺀다. GET /daon/backups → 410
 *
 * 앱은 백업에 닿지 않는다: 백업 위치·백업 계정을 앱이 모르게 한다. 시작 모습은 앱 MinIO 계정으로 앱 버킷 backups/를 직접 읽었다(T36).
 * 백업은 다른 저장소(backup-vault)·다른 계정(backup-writer·backup-restorer)·다른 망(backup)에 있고, 관리자 앱 컨테이너는 그 망에 없다.
 * 백업 상태는 사람이 백업 계정으로 본다: make backup-status(읽기만 되는 backup-restorer). 화면 자리는 남겨 안내 문구만 준다.
 */
@RestController
@Tag(name = "DaonOpsBackupController", description = "[다온마켓 14-2] 백업 목록 보기(앱에서 뺐다)")
@RequestMapping("/daon/backups")
public class DaonOpsBackupController {

    @Operation(summary = "백업 목록(관리자 앱은 백업에 닿지 않는다: 410)")
    @GetMapping
    public ResponseEntity<CommonResult<List<Map<String, Object>>>> list() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(CommonResult.failed("관리자 앱은 백업에 닿지 않습니다. 백업 상태는 백업 계정으로 봅니다(make backup-status)"));
    }
}
