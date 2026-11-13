package com.macro.mall.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.api.IErrorCode;
import com.macro.mall.daon.UploadValidator;
import com.macro.mall.dto.BucketPolicyConfigDto;
import com.macro.mall.dto.MinioUploadDto;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import io.minio.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * MinIO오브젝트 스토리지 관리Controller
 * Created by macro on 2019/12/25.
 */
@Controller
@Tag(name = "MinioController", description = "MinIO오브젝트 스토리지 관리")
@RequestMapping("/minio")
public class MinioController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinioController.class);
    @Value("${minio.endpoint}")
    private String ENDPOINT;
    // 내부 저장 주소와 브라우저가 읽을 공개 주소는 다르다.
    @Value("${minio.public-endpoint:}")
    private String PUBLIC_ENDPOINT;
    @Value("${minio.bucketName}")
    private String BUCKET_NAME;
    @Value("${minio.accessKey}")
    private String ACCESS_KEY;
    @Value("${minio.secretKey}")
    private String SECRET_KEY;

    @Operation(summary = "파일 업로드")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<CommonResult> upload(@RequestPart("file") MultipartFile file) {
        // 다온마켓 3-6: 형식은 내용(앞 바이트)으로 정한다. 이름·Content-Type은 서버가 새로 만든다
        String objectName;
        String contentType;
        if (SecureKitSwitch.on("upload")) {
            UploadValidator.ImageType type;
            try {
                type = UploadValidator.check(file);
            } catch (UploadValidator.Rejected r) {
                return reject(r);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(CommonResult.failed("파일을 읽지 못했습니다"));
            }
            objectName = UploadValidator.objectName(type);
            contentType = type.mimeType;
        } else {
            // [랩 스위치] secure-kit.upload.enabled=false: 고치기 전처럼 올린 이름·Content-Type을 그대로 쓴다(영상용)
            objectName = new SimpleDateFormat("yyyyMMdd").format(new Date()) + "/" + file.getOriginalFilename();
            contentType = file.getContentType();
        }
        try {
            //MinIO Java 클라이언트 생성
            MinioClient minioClient =MinioClient.builder()
                    .endpoint(ENDPOINT)
                    .credentials(ACCESS_KEY,SECRET_KEY)
                    .build();
            boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
            if (isExist) {
                LOGGER.info("버킷이 이미 존재합니다!");
            } else {
                //버킷을 생성하고 읽기 전용 권한 설정
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
                BucketPolicyConfigDto bucketPolicyConfigDto = createBucketPolicyConfigDto(BUCKET_NAME);
                SetBucketPolicyArgs setBucketPolicyArgs = SetBucketPolicyArgs.builder()
                        .bucket(BUCKET_NAME)
                        .config(JSONUtil.toJsonStr(bucketPolicyConfigDto))
                        .build();
                minioClient.setBucketPolicy(setBucketPolicyArgs);
            }
            // 원래 파일 이름은 응답의 표시용으로만 쓴다(저장 이름에는 쓰지 않는다)
            String filename = file.getOriginalFilename();
            // putObject로 버킷에 파일 하나 업로드
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(BUCKET_NAME)
                    .object(objectName)
                    .contentType(contentType)
                    .stream(file.getInputStream(), file.getSize(), ObjectWriteArgs.MIN_MULTIPART_SIZE).build();
            minioClient.putObject(putObjectArgs);
            LOGGER.info("파일 업로드 성공!");
            MinioUploadDto minioUploadDto = new MinioUploadDto();
            minioUploadDto.setName(filename);
            String publicBase = PUBLIC_ENDPOINT == null || PUBLIC_ENDPOINT.isBlank() ? ENDPOINT : PUBLIC_ENDPOINT;
            minioUploadDto.setUrl(publicBase.replaceAll("/+$", "") + "/" + BUCKET_NAME + "/" + objectName);
            return ResponseEntity.ok(CommonResult.success(minioUploadDto));
        } catch (Exception e) {
            e.printStackTrace(); // ✏️ 6-5: 화면·표준 출력으로 새는 스택 추적 대신 로거로(원인은 서버 로그에만)
            LOGGER.info("업로드 중 오류 발생: {}!", e.getMessage());
        }
        return ResponseEntity.ok(CommonResult.failed());
    }

    // 다온마켓 3-6 ⑤ 탐지: 거절 지점에 남긴다. 올린 사람이 정한 파일 이름은 넣지 않는다(로그 위조 통로)
    private static ResponseEntity<CommonResult> reject(UploadValidator.Rejected r) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityEventLogger.denied(r.event, "3-6")
                .actor(auth == null ? "anonymous" : "admin:" + auth.getName())
                .target("upload:-")
                .reason(r.reason)
                .log();
        IErrorCode code = new IErrorCode() {
            public long getCode() { return r.status; }
            public String getMessage() { return r.reason; }
        };
        return ResponseEntity.status(r.status).body(CommonResult.failed(code));
    }

    /**
     * 버킷 접근 정책을 생성하고 읽기 전용 권한으로 설정
     */
    private BucketPolicyConfigDto createBucketPolicyConfigDto(String bucketName) {
        BucketPolicyConfigDto.Statement statement = BucketPolicyConfigDto.Statement.builder()
                .Effect("Allow")
                .Principal("*")
                .Action("s3:GetObject")
                .Resource("arn:aws:s3:::"+bucketName+"/*.**").build();
        return BucketPolicyConfigDto.builder()
                .Version("2012-10-17")
                .Statement(CollUtil.toList(statement))
                .build();
    }

    @Operation(summary = "파일 삭제")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult delete(@RequestParam("objectName") String objectName) {
        try {
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(ENDPOINT)
                    .credentials(ACCESS_KEY,SECRET_KEY)
                    .build();
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_NAME).object(objectName).build());
            return CommonResult.success(null);
        } catch (Exception e) {
            e.printStackTrace(); // ✏️ 6-5: 화면·표준 출력으로 새는 스택 추적 대신 로거로(원인은 서버 로그에만)
        }
        return CommonResult.failed();
    }
}
