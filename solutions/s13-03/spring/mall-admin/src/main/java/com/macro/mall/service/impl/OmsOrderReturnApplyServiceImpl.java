package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.daon.DaonApprovalClient;
import com.macro.mall.daon.DaonRefundExecutor;
import com.macro.mall.dao.OmsOrderReturnApplyDao;
import com.macro.mall.dto.OmsOrderReturnApplyResult;
import com.macro.mall.dto.OmsReturnApplyQueryParam;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.service.OmsOrderReturnApplyService;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 주문 반품 관리 Service 구현 클래스
 * Created by macro on 2018/10/18.
 *
 * [다온마켓 13-3 정답] 반품 확인(status=1)은 관리자 앱이 혼자 승인하지 않는다(F48·T34).
 *   ① 처리자(handleMan)는 요청 본문이 아니라 로그인 정보에서
 *   ② 상태 전이는 대기(0) → 확인(1)만, 조건부 UPDATE(WHERE status = 0, 10-4의 단방향 상태 전이)
 *   ③ 금액(returnAmount)은 「요청 금액」으로만 승인 서비스에 넘긴다. 여기서 저장하지 않는다
 *   ④ 승인 서비스가 돌려준 서명된 승인 토큰을 실행기가 확인해야만 결제사 환불이 나간다(DaonRefundExecutor)
 *   거부되면 확인을 되돌린다(대기로). 고액(13-4)은 PENDING_SECOND로 남고, 지연 실행 뒤 실행기가 가져간다.
 */
@Service
public class OmsOrderReturnApplyServiceImpl implements OmsOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyDao returnApplyDao;
    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;
    @Autowired
    private DaonRefundExecutor refundExecutor;
    @Autowired
    private DaonApprovalClient approvalClient;
    @Autowired
    private JdbcTemplate jdbc;
    @Override
    public List<OmsOrderReturnApply> list(OmsReturnApplyQueryParam queryParam, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum,pageSize);
        return returnApplyDao.getList(queryParam);
    }

    @Override
    public int delete(List<Long> ids) {
        OmsOrderReturnApplyExample example = new OmsOrderReturnApplyExample();
        example.createCriteria().andIdIn(ids).andStatusEqualTo(3);
        return returnApplyMapper.deleteByExample(example);
    }

    @Override
    public int updateStatus(Long id, OmsUpdateStatusParam statusParam) {
        Integer status = statusParam.getStatus();
        boolean guarded = SecureKitSwitch.on("approval");   // [랩 스위치] 끄면 시작 지점 모습(요청 금액·처리자를 믿고 바로 환불)
        if (guarded && Integer.valueOf(1).equals(status)) {
            return confirmWithApproval(id, statusParam);
        }
        if (guarded && Integer.valueOf(3).equals(status)) {
            statusParam.setHandleMan(currentAdmin());        // 거절도 처리자는 로그인 정보에서
        }
        OmsOrderReturnApply returnApply = new OmsOrderReturnApply();
        if(status.equals(1)){
            //반품 확인
            returnApply.setId(id);
            returnApply.setStatus(1);
            returnApply.setReturnAmount(statusParam.getReturnAmount());
            returnApply.setCompanyAddressId(statusParam.getCompanyAddressId());
            returnApply.setHandleTime(new Date());
            returnApply.setHandleMan(statusParam.getHandleMan());
            returnApply.setHandleNote(statusParam.getHandleNote());
        }else if(status.equals(2)){
            //반품 완료
            returnApply.setId(id);
            returnApply.setStatus(2);
            returnApply.setReceiveTime(new Date());
            returnApply.setReceiveMan(statusParam.getReceiveMan());
            returnApply.setReceiveNote(statusParam.getReceiveNote());
        }else if(status.equals(3)){
            //반품 거절
            returnApply.setId(id);
            returnApply.setStatus(3);
            returnApply.setHandleTime(new Date());
            returnApply.setHandleMan(statusParam.getHandleMan());
            returnApply.setHandleNote(statusParam.getHandleNote());
        }else{
            return 0;
        }
        int count = returnApplyMapper.updateByPrimaryKeySelective(returnApply);
        if (count > 0 && status.equals(1)) {
            refundExecutor.refundNow(id, statusParam.getReturnAmount());   // 스위치를 껐을 때만 오는 길(시작 지점 모습)
        }
        return count;
    }

    /** ✏️ 13-3: 확인 = 상태 전이(조건부) + 승인 서비스에 요청 + 승인 토큰이 있을 때만 실행 */
    private int confirmWithApproval(Long id, OmsUpdateStatusParam p) {
        OmsOrderReturnApply returnApply = new OmsOrderReturnApply();
        returnApply.setStatus(1);
        returnApply.setCompanyAddressId(p.getCompanyAddressId());
        returnApply.setHandleTime(new Date());
        returnApply.setHandleMan(currentAdmin());                 // ① 요청 본문의 handleMan은 쓰지 않는다
        returnApply.setHandleNote(p.getHandleNote());
        OmsOrderReturnApplyExample onlyPending = new OmsOrderReturnApplyExample();
        onlyPending.createCriteria().andIdEqualTo(id).andStatusEqualTo(0);
        if (returnApplyMapper.updateByExampleSelective(returnApply, onlyPending) == 0) {
            return 0;                                              // ② 이미 처리한 신청(두 번 눌러도 환불은 한 번)
        }
        DaonApprovalClient.Result a = approvalClient.requestRefund(id, p.getReturnAmount());   // ③ 요청만 한다
        switch (a.status()) {
            case "APPROVED":
                refundExecutor.execute(id, a.token());              // ④ 토큰을 확인해야만 실행
                return 1;
            case "PENDING_SECOND":
            case "HELD_BY_BREAKER":
                return 1;                                          // 13-4: 두 번째 승인·지연 실행 뒤 실행기가 가져간다
            default:
                jdbc.update("UPDATE oms_order_return_apply SET status = 0, handle_time = NULL, handle_man = NULL WHERE id = ? AND status = 1", id);
                return 0;                                          // 거부(남은 금액 초과·한도) 또는 승인 서비스 장애: 확인을 되돌린다
        }
    }

    private static String currentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    @Override
    public OmsOrderReturnApplyResult getItem(Long id) {
        return returnApplyDao.getDetail(id);
    }
}
