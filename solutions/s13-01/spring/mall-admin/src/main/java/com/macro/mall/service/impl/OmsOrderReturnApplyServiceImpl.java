package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.daon.DaonRefundExecutor;
import com.macro.mall.dao.OmsOrderReturnApplyDao;
import com.macro.mall.dto.OmsOrderReturnApplyResult;
import com.macro.mall.dto.OmsReturnApplyQueryParam;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.service.OmsOrderReturnApplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 주문 반품 관리 Service 구현 클래스
 * Created by macro on 2018/10/18.
 *
 * [다온마켓 13장 시작 모습] updateStatus()는 원본 그대로다(F48: 환불 금액 returnAmount·처리자 handleMan을 요청 본문에서 받고,
 * 상태 전이(대기 → 확인)와 금액 상한을 확인하지 않는다). 원본은 반품을 확인한 뒤 돈을 돌려주는 코드가 없어서,
 * 교육용으로 「확인하면 그 자리에서 결제사(가짜)에 환불을 보내는」 실행기 한 줄을 더했다([교육용 추가] DaonRefundExecutor, T34).
 * ✏️ 13-3에서 고친다.
 */
@Service
public class OmsOrderReturnApplyServiceImpl implements OmsOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyDao returnApplyDao;
    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;
    @Autowired
    private DaonRefundExecutor refundExecutor;   // [교육용 추가] 13장 시작 모습
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
            // [교육용 추가] 확인하면 바로 환불: 금액은 받은 요청의 returnAmount 그대로(T34)
            refundExecutor.refundNow(id, statusParam.getReturnAmount());
        }
        return count;
    }

    @Override
    public OmsOrderReturnApplyResult getItem(Long id) {
        return returnApplyDao.getDetail(id);
    }
}
