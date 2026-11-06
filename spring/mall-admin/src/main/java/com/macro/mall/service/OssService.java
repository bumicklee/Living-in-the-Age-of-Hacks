package com.macro.mall.service;

import com.macro.mall.dto.OssCallbackResult;
import com.macro.mall.dto.OssPolicyResult;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Oss오브젝트 스토리지 관리Service
 * Created by macro on 2018/5/17.
 */
public interface OssService {
    /**
     * Oss업로드 정책 생성
     */
    OssPolicyResult policy();
    /**
     * Oss업로드 성공 콜백
     */
    OssCallbackResult callback(HttpServletRequest request);
}
