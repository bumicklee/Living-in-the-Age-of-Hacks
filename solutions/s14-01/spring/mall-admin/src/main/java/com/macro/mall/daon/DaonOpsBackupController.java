package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [교육용 추가] 다온마켓 14장 시작 모습: 운영 화면의 「백업 목록 보기」. GET /daon/backups
 *
 * ✏️ 14-2: 관리자 앱이 앱 MinIO 계정으로 앱 버킷 안 backups/mysql/을 직접 읽는다.
 *   앱이 백업 위치를 알고, 백업에 닿는 자격증명을 들고 있다는 뜻이다(T36). 앱이 뚫리면 백업도 같이 닿는다.
 *   14-2에서는 이 코드를 지운다: 앱은 백업에 닿지 않는다(백업 상태는 백업 계정으로 make backup-status).
 * 권한표에는 관리자 계정 관리(/admin/**)와 같은 권한으로 올린다(MallSecurityConfig). 결과에는 파일 이름·크기·시각만.
 */
@RestController
@Tag(name = "DaonOpsBackupController", description = "[교육용 추가 14장] 백업 목록 보기")
@RequestMapping("/daon/backups")
public class DaonOpsBackupController {
    @Value("${minio.endpoint}")
    private String endpoint;
    @Value("${minio.bucketName}")
    private String bucket;
    @Value("${minio.accessKey}")
    private String accessKey;
    @Value("${minio.secretKey}")
    private String secretKey;

    @Operation(summary = "백업 목록(앱 버킷 backups/mysql/)")
    @GetMapping
    public ResponseEntity<CommonResult<List<Map<String, Object>>>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            MinioClient client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
            for (Result<Item> r : client.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix("backups/mysql/").recursive(true).build())) {
                Item i = r.get();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", i.objectName());
                row.put("size", i.size());
                row.put("lastModified", i.lastModified() == null ? null : i.lastModified().toString());
                out.add(row);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(CommonResult.failed("백업 목록을 읽지 못했습니다"));
        }
        return ResponseEntity.ok(CommonResult.success(out));
    }
}
