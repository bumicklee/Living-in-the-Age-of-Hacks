package com.macro.mall.portal.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.component.CancelOrderSender;
import com.macro.mall.portal.dao.DaonMoneyDao;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.dao.PortalOrderItemDao;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.*;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 프런트 주문 관리Service
 * Created by macro on 2018/8/30.
 */
@Service
public class OmsPortalOrderServiceImpl implements OmsPortalOrderService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsCartItemService cartItemService;
    @Autowired
    private UmsMemberReceiveAddressService memberReceiveAddressService;
    @Autowired
    private UmsMemberCouponService memberCouponService;
    @Autowired
    private UmsIntegrationConsumeSettingMapper integrationConsumeSettingMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private SmsCouponHistoryDao couponHistoryDao;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private PortalOrderItemDao orderItemDao;
    @Autowired
    private SmsCouponHistoryMapper couponHistoryMapper;
    @Autowired
    private RedisService redisService;
    @Value("${redis.key.orderId}")
    private String REDIS_KEY_ORDER_ID;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Autowired
    private PortalOrderDao portalOrderDao;
    @Autowired
    private OmsOrderSettingMapper orderSettingMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private CancelOrderSender cancelOrderSender;
    @Autowired
    private DaonMoneyDao moneyDao;
    @Autowired
    private UmsMemberCacheService memberCacheService;

    @Override
    public ConfirmOrderResult generateConfirmOrder(List<Long> cartIds) {
        ConfirmOrderResult result = new ConfirmOrderResult();
        //장바구니 정보 조회
        UmsMember currentMember = memberService.getCurrentMember();
        List<CartPromotionItem> cartPromotionItemList = cartItemService.listPromotion(currentMember.getId(),cartIds);
        result.setCartPromotionItemList(cartPromotionItemList);
        //사용자 배송지 목록 조회
        List<UmsMemberReceiveAddress> memberReceiveAddressList = memberReceiveAddressService.list();
        result.setMemberReceiveAddressList(memberReceiveAddressList);
        //사용자 사용 가능 쿠폰 목록 조회
        List<SmsCouponHistoryDetail> couponHistoryDetailList = memberCouponService.listCart(cartPromotionItemList, 1);
        result.setCouponHistoryDetailList(couponHistoryDetailList);
        //사용자 포인트 조회
        result.setMemberIntegration(currentMember.getIntegration());
        //포인트 사용 규칙 조회
        UmsIntegrationConsumeSetting integrationConsumeSetting = integrationConsumeSettingMapper.selectByPrimaryKey(1L);
        result.setIntegrationConsumeSetting(integrationConsumeSetting);
        //총 금액, 행사 할인, 결제 금액 계산
        ConfirmOrderResult.CalcAmount calcAmount = calcCartAmount(cartPromotionItemList);
        result.setCalcAmount(calcAmount);
        return result;
    }

    @Override
    public Map<String, Object> generateOrder(OrderParam orderParam) {
        List<OmsOrderItem> orderItemList = new ArrayList<>();
        //배송지 검증
        if(orderParam.getMemberReceiveAddressId()==null){
            Asserts.fail("배송지를 선택하세요!");
        }
        //장바구니 및 할인 정보 조회
        UmsMember currentMember = memberService.getCurrentMember();
        List<CartPromotionItem> cartPromotionItemList = cartItemService.listPromotion(currentMember.getId(), orderParam.getCartIds());
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            //주문 상품 정보 생성
            OmsOrderItem orderItem = new OmsOrderItem();
            orderItem.setProductId(cartPromotionItem.getProductId());
            orderItem.setProductName(cartPromotionItem.getProductName());
            orderItem.setProductPic(cartPromotionItem.getProductPic());
            orderItem.setProductAttr(cartPromotionItem.getProductAttr());
            orderItem.setProductBrand(cartPromotionItem.getProductBrand());
            orderItem.setProductSn(cartPromotionItem.getProductSn());
            orderItem.setProductPrice(cartPromotionItem.getPrice());
            orderItem.setProductQuantity(cartPromotionItem.getQuantity());
            orderItem.setProductSkuId(cartPromotionItem.getProductSkuId());
            orderItem.setProductSkuCode(cartPromotionItem.getProductSkuCode());
            orderItem.setProductCategoryId(cartPromotionItem.getProductCategoryId());
            orderItem.setPromotionAmount(cartPromotionItem.getReduceAmount());
            orderItem.setPromotionName(cartPromotionItem.getPromotionMessage());
            orderItem.setGiftIntegration(cartPromotionItem.getIntegration());
            orderItem.setGiftGrowth(cartPromotionItem.getGrowth());
            orderItemList.add(orderItem);
        }
        //장바구니 상품에 모두 재고가 있는지 판단
        if (!hasStock(cartPromotionItemList)) {
            Asserts.fail("재고가 부족하여 주문할 수 없습니다");
        }
        //쿠폰을 사용했는지 판단
        if (orderParam.getCouponId() == null) {
            //쿠폰 사용 안 함
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setCouponAmount(new BigDecimal(0));
            }
        } else {
            //쿠폰 사용
            SmsCouponHistoryDetail couponHistoryDetail = getUseCoupon(cartPromotionItemList, orderParam.getCouponId());
            if (couponHistoryDetail == null) {
                Asserts.fail("이 쿠폰은 사용할 수 없습니다");
            }
            //주문 상품에 대한 쿠폰 처리
            handleCouponAmount(orderItemList, couponHistoryDetail);
        }
        //포인트 사용 여부 판단
        if (orderParam.getUseIntegration() == null||orderParam.getUseIntegration().equals(0)) {
            //포인트 사용 안 함
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setIntegrationAmount(new BigDecimal(0));
            }
        } else {
            //포인트 사용
            BigDecimal totalAmount = calcTotalAmount(orderItemList);
            BigDecimal integrationAmount = getUseIntegrationAmount(orderParam.getUseIntegration(), totalAmount, currentMember, orderParam.getCouponId() != null);
            if (integrationAmount.compareTo(new BigDecimal(0)) == 0) {
                Asserts.fail("포인트 사용 불가");
            } else {
                //사용 가능한 경우 사용 가능 상품에 분배
                for (OmsOrderItem orderItem : orderItemList) {
                    BigDecimal perAmount = orderItem.getProductPrice().divide(totalAmount, 3, RoundingMode.HALF_EVEN).multiply(integrationAmount);
                    orderItem.setIntegrationAmount(perAmount);
                }
            }
        }
        //order_item의 실제 결제 금액 계산
        handleRealAmount(orderItemList);
        //재고 잠금 처리(다온마켓 10-2: 조건부 UPDATE, 남은 재고가 모자라면 주문 전체를 되돌린다)
        lockStock(cartPromotionItemList);
        //상품 합계, 배송비, 이벤트 할인, 쿠폰, 포인트로 결제 금액 계산
        OmsOrder order = new OmsOrder();
        order.setDiscountAmount(new BigDecimal(0));
        order.setTotalAmount(calcTotalAmount(orderItemList));
        order.setFreightAmount(new BigDecimal(0));
        order.setPromotionAmount(calcPromotionAmount(orderItemList));
        order.setPromotionInfo(getOrderPromotionInfo(orderItemList));
        if (orderParam.getCouponId() == null) {
            order.setCouponAmount(new BigDecimal(0));
        } else {
            order.setCouponId(orderParam.getCouponId());
            order.setCouponAmount(calcCouponAmount(orderItemList));
        }
        if (orderParam.getUseIntegration() == null) {
            order.setIntegration(0);
            order.setIntegrationAmount(new BigDecimal(0));
        } else {
            order.setIntegration(orderParam.getUseIntegration());
            order.setIntegrationAmount(calcIntegrationAmount(orderItemList));
        }
        order.setPayAmount(calcPayAmount(order));
        //주문 정보로 변환하여 데이터베이스에 삽입
        order.setMemberId(currentMember.getId());
        order.setCreateTime(new Date());
        order.setMemberUsername(currentMember.getUsername());
        //결제 수단:0->미결제;1->알리페이;2->위챗
        order.setPayType(orderParam.getPayType());
        //주문 출처:0->PC주문;1->app주문
        order.setSourceType(1);
        //주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문
        order.setStatus(0);
        //주문 유형:0->정상 주문;1->타임 세일 주문
        order.setOrderType(0);
        //수령인 정보: 이름, 전화번호, 우편번호, 주소
        UmsMemberReceiveAddress address = memberReceiveAddressService.getItem(orderParam.getMemberReceiveAddressId());
        order.setReceiverName(address.getName());
        order.setReceiverPhone(address.getPhoneNumber());
        order.setReceiverPostCode(address.getPostCode());
        order.setReceiverProvince(address.getProvince());
        order.setReceiverCity(address.getCity());
        order.setReceiverRegion(address.getRegion());
        order.setReceiverDetailAddress(address.getDetailAddress());
        //0->미확인;1->확인됨
        order.setConfirmStatus(0);
        order.setDeleteStatus(0);
        //지급할 포인트 계산
        order.setIntegration(calcGifIntegration(orderItemList));
        //지급할 성장 포인트 계산
        order.setGrowth(calcGiftGrowth(orderItemList));
        //주문 번호 생성
        order.setOrderSn(generateOrderSn(order));
        //자동 수령 확정 일수 설정
        List<OmsOrderSetting> orderSettings = orderSettingMapper.selectByExample(new OmsOrderSettingExample());
        if(CollUtil.isNotEmpty(orderSettings)){
            order.setAutoConfirmDay(orderSettings.get(0).getConfirmOvertime());
        }
        // TODO: 2018/9/3 bill_*,delivery_*
        // 다온마켓 10-2: 쓴 포인트를 주문 행에 남긴다(원본은 insert 뒤에 넣어 DB에 남지 않았고, 취소 때 돌려줄 수 없었다)
        if (SecureKitSwitch.on("money-race") && orderParam.getUseIntegration() != null) {
            order.setUseIntegration(orderParam.getUseIntegration());
        }
        //order 테이블과 order_item 테이블에 삽입
        orderMapper.insert(order);
        for (OmsOrderItem orderItem : orderItemList) {
            orderItem.setOrderId(order.getId());
            orderItem.setOrderSn(order.getOrderSn());
        }
        orderItemDao.insertList(orderItemList);
        //쿠폰을 사용한 경우 쿠폰 사용 상태 갱신
        if (orderParam.getCouponId() != null) {
            updateCouponStatus(orderParam.getCouponId(), currentMember.getId(), 1);
        }
        //포인트를 사용한 경우 포인트 차감 필요
        if (orderParam.getUseIntegration() != null && SecureKitSwitch.on("money-race")) {
            useIntegration(currentMember, orderParam.getUseIntegration());
        } else if (orderParam.getUseIntegration() != null) {
            // [랩 스위치] 고치기 전 모습: 로그인할 때 읽어 둔 회원 객체의 포인트로 계산해 덮어쓴다(동시 주문이 서로 지운다, F38)
            order.setUseIntegration(orderParam.getUseIntegration());
            if(currentMember.getIntegration()==null){
                currentMember.setIntegration(0);
            }
            memberService.updateIntegration(currentMember.getId(), currentMember.getIntegration() - orderParam.getUseIntegration());
        }
        //장바구니에서 주문한 상품 삭제
        deleteCartItemList(cartPromotionItemList, currentMember);
        //지연 메시지를 전송해 주문 취소
        sendDelayMessageCancelOrder(order.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("orderItemList", orderItemList);
        return result;
    }

    @Override
    public Integer paySuccess(Long orderId, Integer payType) {
        //주문 결제 상태 수정
        OmsOrder order = new OmsOrder();
        order.setId(orderId);
        order.setStatus(1);
        order.setPaymentTime(new Date());
        order.setPayType(payType);
        orderMapper.updateByPrimaryKeySelective(order);
        //주문한 모든 상품의 잠금 재고를 복구하고 실제 재고를 차감
        OmsOrderDetail orderDetail = portalOrderDao.getDetail(orderId);
        int count = portalOrderDao.updateSkuStock(orderDetail.getOrderItemList());
        return count;
    }

    @Override
    public Integer cancelTimeOutOrder() {
        Integer count=0;
        OmsOrderSetting orderSetting = orderSettingMapper.selectByPrimaryKey(1L);
        //시간 초과, 미결제 주문 및 주문 상세 조회
        List<OmsOrderDetail> timeOutOrders = portalOrderDao.getTimeOutOrders(orderSetting.getNormalOrderOvertime());
        if (CollectionUtils.isEmpty(timeOutOrders)) {
            return count;
        }
        //주문 상태를 거래 취소로 수정
        List<Long> ids = new ArrayList<>();
        for (OmsOrderDetail timeOutOrder : timeOutOrders) {
            ids.add(timeOutOrder.getId());
        }
        portalOrderDao.updateOrderStatus(ids, 4);
        for (OmsOrderDetail timeOutOrder : timeOutOrders) {
            //주문 상품 재고 잠금 해제
            portalOrderDao.releaseSkuStockLock(timeOutOrder.getOrderItemList());
            //쿠폰 사용 상태 수정
            updateCouponStatus(timeOutOrder.getCouponId(), timeOutOrder.getMemberId(), 0);
            //사용한 포인트 반환(다온마켓 10-2: DB 값에 더한다)
            if (timeOutOrder.getUseIntegration() != null) {
                refundIntegration(timeOutOrder.getMemberId(), timeOutOrder.getUseIntegration());
            }
        }
        return timeOutOrders.size();
    }

    @Override
    public void cancelOrder(Long orderId) {
        //미결제로 취소된 주문 조회
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria().andIdEqualTo(orderId).andStatusEqualTo(0).andDeleteStatusEqualTo(0);
        List<OmsOrder> cancelOrderList = orderMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(cancelOrderList)) {
            return;
        }
        OmsOrder cancelOrder = cancelOrderList.get(0);
        if (cancelOrder != null) {
            //주문 상태를 취소로 수정
            cancelOrder.setStatus(4);
            orderMapper.updateByPrimaryKeySelective(cancelOrder);
            OmsOrderItemExample orderItemExample = new OmsOrderItemExample();
            orderItemExample.createCriteria().andOrderIdEqualTo(orderId);
            List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(orderItemExample);
            //주문 상품 재고 잠금 해제
            if (!CollectionUtils.isEmpty(orderItemList)) {
                portalOrderDao.releaseSkuStockLock(orderItemList);
            }
            //쿠폰 사용 상태 수정
            updateCouponStatus(cancelOrder.getCouponId(), cancelOrder.getMemberId(), 0);
            //사용한 포인트 반환(다온마켓 10-2: DB 값에 더한다)
            if (cancelOrder.getUseIntegration() != null) {
                refundIntegration(cancelOrder.getMemberId(), cancelOrder.getUseIntegration());
            }
        }
    }

    @Override
    public void sendDelayMessageCancelOrder(Long orderId) {
        //주문 만료 시간 조회
        OmsOrderSetting orderSetting = orderSettingMapper.selectByPrimaryKey(1L);
        long delayTimes = orderSetting.getNormalOrderOvertime() * 60 * 1000;
        //지연 메시지 전송
        cancelOrderSender.sendMessage(orderId, delayTimes);
    }

    @Override
    public void confirmReceiveOrder(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);
        if(!member.getId().equals(order.getMemberId())){
            Asserts.fail("다른 사람의 주문은 확인할 수 없습니다!");
        }
        if(order.getStatus()!=2){
            Asserts.fail("이 주문은 아직 발송되지 않았습니다!");
        }
        order.setStatus(3);
        order.setConfirmStatus(1);
        order.setReceiveTime(new Date());
        orderMapper.updateByPrimaryKey(order);
    }

    @Override
    public CommonPage<OmsOrderDetail> list(Integer status, Integer pageNum, Integer pageSize) {
        if(status==-1){
            status = null;
        }
        UmsMember member = memberService.getCurrentMember();
        PageHelper.startPage(pageNum,pageSize);
        OmsOrderExample orderExample = new OmsOrderExample();
        OmsOrderExample.Criteria criteria = orderExample.createCriteria();
        criteria.andDeleteStatusEqualTo(0)
                .andMemberIdEqualTo(member.getId());
        if(status!=null){
            criteria.andStatusEqualTo(status);
        }
        orderExample.setOrderByClause("create_time desc");
        List<OmsOrder> orderList = orderMapper.selectByExample(orderExample);
        CommonPage<OmsOrder> orderPage = CommonPage.restPage(orderList);
        //페이징 정보 설정
        CommonPage<OmsOrderDetail> resultPage = new CommonPage<>();
        resultPage.setPageNum(orderPage.getPageNum());
        resultPage.setPageSize(orderPage.getPageSize());
        resultPage.setTotal(orderPage.getTotal());
        resultPage.setTotalPage(orderPage.getTotalPage());
        if(CollUtil.isEmpty(orderList)){
            return resultPage;
        }
        //데이터 정보 설정
        List<Long> orderIds = orderList.stream().map(OmsOrder::getId).collect(Collectors.toList());
        OmsOrderItemExample orderItemExample = new OmsOrderItemExample();
        orderItemExample.createCriteria().andOrderIdIn(orderIds);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(orderItemExample);
        List<OmsOrderDetail> orderDetailList = new ArrayList<>();
        for (OmsOrder omsOrder : orderList) {
            OmsOrderDetail orderDetail = new OmsOrderDetail();
            BeanUtil.copyProperties(omsOrder,orderDetail);
            List<OmsOrderItem> relatedItemList = orderItemList.stream().filter(item -> item.getOrderId().equals(orderDetail.getId())).collect(Collectors.toList());
            orderDetail.setOrderItemList(relatedItemList);
            orderDetailList.add(orderDetail);
        }
        resultPage.setList(orderDetailList);
        return resultPage;
    }

    @Override
    public OmsOrderDetail detail(Long orderId) {
        // [랩 스위치] secure-kit.ownership.enabled(기본 켜짐). 영상에서 make switch K=ownership V=off로 끄면 고치기 전처럼 번호만으로 읽는다
        OmsOrder omsOrder = SecureKitSwitch.on("ownership") ? findOwnOrder(orderId) : orderMapper.selectByPrimaryKey(orderId);
        OmsOrderItemExample example = new OmsOrderItemExample();
        example.createCriteria().andOrderIdEqualTo(orderId);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(example);
        OmsOrderDetail orderDetail = new OmsOrderDetail();
        BeanUtil.copyProperties(omsOrder,orderDetail);
        orderDetail.setOrderItemList(orderItemList);
        return orderDetail;
    }

    // 다온마켓 2-2: 소유권은 조회 조건에 넣는다. 남의 주문과 없는 주문은 같은 답을 받는다
    private OmsOrder findOwnOrder(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrderExample orderExample = new OmsOrderExample();
        orderExample.createCriteria().andIdEqualTo(orderId).andMemberIdEqualTo(member.getId()).andDeleteStatusEqualTo(0);
        List<OmsOrder> orders = orderMapper.selectByExample(orderExample);
        if (CollUtil.isEmpty(orders)) {
            // 다온마켓 2-2 ⑤ 탐지: 거부를 보안 이벤트로 남긴다(주문 번호만, 개인정보는 넣지 않는다)
            SecurityEventLogger.denied("access.denied.not_owner", "2-2")
                    .actor("member:" + member.getId())
                    .target("order:" + orderId)
                    .reason("본인 주문이 아니거나 없는 주문")
                    .log();
            Asserts.fail("주문을 찾을 수 없습니다");
        }
        return orders.get(0);
    }

    @Override
    public void deleteOrder(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);
        if(!member.getId().equals(order.getMemberId())){
            Asserts.fail("다른 사람의 주문은 삭제할 수 없습니다!");
        }
        if(order.getStatus()==3||order.getStatus()==4){
            order.setDeleteStatus(1);
            orderMapper.updateByPrimaryKey(order);
        }else{
            Asserts.fail("완료되었거나 닫힌 주문만 삭제할 수 있습니다!");
        }
    }

    @Override
    public void paySuccessByOrderSn(String orderSn, Integer payType) {
        OmsOrder order = getOrderByOrderSn(orderSn);
        if(order!=null){
            this.paySuccess(order.getId(),payType);
        }
    }

    @Override
    public OmsOrder getOrderByOrderSn(String orderSn) {
        OmsOrderExample example =  new OmsOrderExample();
        example.createCriteria()
                .andOrderSnEqualTo(orderSn)
                .andStatusEqualTo(0)
                .andDeleteStatusEqualTo(0);
        List<OmsOrder> orderList = orderMapper.selectByExample(example);
        if(CollUtil.isNotEmpty(orderList)){
            return orderList.get(0);
        }else return null;
    }

    /**
     * 18자리 주문 번호 생성: 8자리 날짜+2자리 플랫폼 번호+2자리 결제 수단+6자리 이상 자동 증가id
     */
    private String generateOrderSn(OmsOrder order) {
        StringBuilder sb = new StringBuilder();
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = REDIS_DATABASE+":"+ REDIS_KEY_ORDER_ID + date;
        Long increment = redisService.incr(key, 1);
        sb.append(date);
        sb.append(String.format("%02d", order.getSourceType()));
        sb.append(String.format("%02d", order.getPayType()));
        String incrementStr = increment.toString();
        if (incrementStr.length() <= 6) {
            sb.append(String.format("%06d", increment));
        } else {
            sb.append(incrementStr);
        }
        return sb.toString();
    }

    /**
     * 주문한 상품의 장바구니 정보 삭제
     */
    private void deleteCartItemList(List<CartPromotionItem> cartPromotionItemList, UmsMember currentMember) {
        List<Long> ids = new ArrayList<>();
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            ids.add(cartPromotionItem.getId());
        }
        cartItemService.delete(currentMember.getId(), ids);
    }

    /**
     * 해당 주문으로 지급할 성장 포인트 계산
     */
    private Integer calcGiftGrowth(List<OmsOrderItem> orderItemList) {
        Integer sum = 0;
        for (OmsOrderItem orderItem : orderItemList) {
            sum = sum + orderItem.getGiftGrowth() * orderItem.getProductQuantity();
        }
        return sum;
    }

    /**
     * 해당 주문으로 지급할 포인트 계산
     */
    private Integer calcGifIntegration(List<OmsOrderItem> orderItemList) {
        int sum = 0;
        for (OmsOrderItem orderItem : orderItemList) {
            sum += orderItem.getGiftIntegration() * orderItem.getProductQuantity();
        }
        return sum;
    }

    /**
     * 쿠폰 정보를 지정한 상태로 변경
     *
     * @param couponId  쿠폰id
     * @param memberId  회원id
     * @param useStatus 0->미사용;1->사용 완료
     */
    private void updateCouponStatus(Long couponId, Long memberId, Integer useStatus) {
        if (couponId == null) return;
        //첫 번째 쿠폰 조회
        SmsCouponHistoryExample example = new SmsCouponHistoryExample();
        example.createCriteria().andMemberIdEqualTo(memberId)
                .andCouponIdEqualTo(couponId).andUseStatusEqualTo(useStatus == 0 ? 1 : 0);
        List<SmsCouponHistory> couponHistoryList = couponHistoryMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(couponHistoryList)) {
            SmsCouponHistory couponHistory = couponHistoryList.get(0);
            couponHistory.setUseTime(new Date());
            couponHistory.setUseStatus(useStatus);
            couponHistoryMapper.updateByPrimaryKeySelective(couponHistory);
        }
    }

    private void handleRealAmount(List<OmsOrderItem> orderItemList) {
        for (OmsOrderItem orderItem : orderItemList) {
            //정가-프로모션 할인-쿠폰 차감-포인트 차감
            BigDecimal realAmount = orderItem.getProductPrice()
                    .subtract(orderItem.getPromotionAmount())
                    .subtract(orderItem.getCouponAmount())
                    .subtract(orderItem.getIntegrationAmount());
            orderItem.setRealAmount(realAmount);
        }
    }

    /**
     * 주문 프로모션 정보 조회
     */
    private String getOrderPromotionInfo(List<OmsOrderItem> orderItemList) {
        StringBuilder sb = new StringBuilder();
        for (OmsOrderItem orderItem : orderItemList) {
            sb.append(orderItem.getPromotionName());
            sb.append(";");
        }
        String result = sb.toString();
        if (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 주문 결제 금액 계산
     */
    private BigDecimal calcPayAmount(OmsOrder order) {
        //총 금액 + 배송비 - 프로모션 할인 - 쿠폰 할인 - 포인트 차감
        BigDecimal payAmount = order.getTotalAmount()
                .add(order.getFreightAmount())
                .subtract(order.getPromotionAmount())
                .subtract(order.getCouponAmount())
                .subtract(order.getIntegrationAmount());
        return payAmount;
    }

    /**
     * 주문 쿠폰 금액 계산
     */
    private BigDecimal calcIntegrationAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal integrationAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getIntegrationAmount() != null) {
                integrationAmount = integrationAmount.add(orderItem.getIntegrationAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return integrationAmount;
    }

    /**
     * 주문 쿠폰 금액 계산
     */
    private BigDecimal calcCouponAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal couponAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getCouponAmount() != null) {
                couponAmount = couponAmount.add(orderItem.getCouponAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return couponAmount;
    }

    /**
     * 주문 행사 할인 계산
     */
    private BigDecimal calcPromotionAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal promotionAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getPromotionAmount() != null) {
                promotionAmount = promotionAmount.add(orderItem.getPromotionAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return promotionAmount;
    }

    /**
     * 사용 가능한 포인트 차감 금액 조회
     *
     * @param useIntegration 사용한 포인트 수량
     * @param totalAmount    주문 총 금액
     * @param currentMember  사용한 사용자
     * @param hasCoupon      쿠폰 사용 여부
     */
    private BigDecimal getUseIntegrationAmount(Integer useIntegration, BigDecimal totalAmount, UmsMember currentMember, boolean hasCoupon) {
        BigDecimal zeroAmount = new BigDecimal(0);
        //사용자가 그만큼의 포인트를 보유하고 있는지 판단
        if (useIntegration.compareTo(currentMember.getIntegration()) > 0) {
            return zeroAmount;
        }
        //포인트 사용 규칙으로 사용 가능 여부 판단
        //쿠폰과 함께 사용 가능 여부
        UmsIntegrationConsumeSetting integrationConsumeSetting = integrationConsumeSettingMapper.selectByPrimaryKey(1L);
        if (hasCoupon && integrationConsumeSetting.getCouponStatus().equals(0)) {
            //쿠폰과 함께 사용 불가
            return zeroAmount;
        }
        //포인트 최소 사용 기준 도달 여부
        if (useIntegration.compareTo(integrationConsumeSetting.getUseUnit()) < 0) {
            return zeroAmount;
        }
        //주문 포인트 사용 최대 비율 초과 여부
        BigDecimal integrationAmount = new BigDecimal(useIntegration).divide(new BigDecimal(integrationConsumeSetting.getUseUnit()), 2, RoundingMode.HALF_EVEN);
        BigDecimal maxPercent = new BigDecimal(integrationConsumeSetting.getMaxPercentPerOrder()).divide(new BigDecimal(100), 2, RoundingMode.HALF_EVEN);
        if (integrationAmount.compareTo(totalAmount.multiply(maxPercent)) > 0) {
            return zeroAmount;
        }
        return integrationAmount;
    }

    /**
     * 쿠폰 할인 처리
     *
     * @param orderItemList       order_item목록
     * @param couponHistoryDetail 사용 가능 쿠폰 상세
     */
    private void handleCouponAmount(List<OmsOrderItem> orderItemList, SmsCouponHistoryDetail couponHistoryDetail) {
        SmsCoupon coupon = couponHistoryDetail.getCoupon();
        if (coupon.getUseType().equals(0)) {
            //전 상품 공용
            calcPerCouponAmount(orderItemList, coupon);
        } else if (coupon.getUseType().equals(1)) {
            //지정 카테고리
            List<OmsOrderItem> couponOrderItemList = getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 0);
            calcPerCouponAmount(couponOrderItemList, coupon);
        } else if (coupon.getUseType().equals(2)) {
            //지정 상품
            List<OmsOrderItem> couponOrderItemList = getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 1);
            calcPerCouponAmount(couponOrderItemList, coupon);
        }
    }

    /**
     * 주문 상품별 쿠폰 금액 분배 계산
     *
     * @param orderItemList 쿠폰 사용 가능한 주문 상품
     */
    private void calcPerCouponAmount(List<OmsOrderItem> orderItemList, SmsCoupon coupon) {
        BigDecimal totalAmount = calcTotalAmount(orderItemList);
        for (OmsOrderItem orderItem : orderItemList) {
            //(상품 가격/사용 가능 상품 총액)*쿠폰 액면가
            BigDecimal couponAmount = orderItem.getProductPrice().divide(totalAmount, 3, RoundingMode.HALF_EVEN).multiply(coupon.getAmount());
            orderItem.setCouponAmount(couponAmount);
        }
    }

    /**
     * 쿠폰과 관련된 주문 상품 조회
     *
     * @param couponHistoryDetail 쿠폰 상세
     * @param orderItemList       주문 상품
     * @param type                사용 관계 유형:0->관련 카테고리;1->지정 상품
     */
    private List<OmsOrderItem> getCouponOrderItemByRelation(SmsCouponHistoryDetail couponHistoryDetail, List<OmsOrderItem> orderItemList, int type) {
        List<OmsOrderItem> result = new ArrayList<>();
        if (type == 0) {
            List<Long> categoryIdList = new ArrayList<>();
            for (SmsCouponProductCategoryRelation productCategoryRelation : couponHistoryDetail.getCategoryRelationList()) {
                categoryIdList.add(productCategoryRelation.getProductCategoryId());
            }
            for (OmsOrderItem orderItem : orderItemList) {
                if (categoryIdList.contains(orderItem.getProductCategoryId())) {
                    result.add(orderItem);
                } else {
                    orderItem.setCouponAmount(new BigDecimal(0));
                }
            }
        } else if (type == 1) {
            List<Long> productIdList = new ArrayList<>();
            for (SmsCouponProductRelation productRelation : couponHistoryDetail.getProductRelationList()) {
                productIdList.add(productRelation.getProductId());
            }
            for (OmsOrderItem orderItem : orderItemList) {
                if (productIdList.contains(orderItem.getProductId())) {
                    result.add(orderItem);
                } else {
                    orderItem.setCouponAmount(new BigDecimal(0));
                }
            }
        }
        return result;
    }

    /**
     * 해당 사용자가 사용할 수 있는 쿠폰 조회
     *
     * @param cartPromotionItemList 장바구니 할인 목록
     * @param couponId              쿠폰 사용id
     */
    private SmsCouponHistoryDetail getUseCoupon(List<CartPromotionItem> cartPromotionItemList, Long couponId) {
        List<SmsCouponHistoryDetail> couponHistoryDetailList = memberCouponService.listCart(cartPromotionItemList, 1);
        for (SmsCouponHistoryDetail couponHistoryDetail : couponHistoryDetailList) {
            if (couponHistoryDetail.getCoupon().getId().equals(couponId)) {
                return couponHistoryDetail;
            }
        }
        return null;
    }

    /**
     * 총 금액 계산
     */
    private BigDecimal calcTotalAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal totalAmount = new BigDecimal("0");
        for (OmsOrderItem item : orderItemList) {
            totalAmount = totalAmount.add(item.getProductPrice().multiply(new BigDecimal(item.getProductQuantity())));
        }
        return totalAmount;
    }

    /**
     * 다온마켓 10-2: 포인트 차감은 조건부 UPDATE 한 줄(잔액 &gt;= 쓸 포인트). 0행이면 주문 전체를 되돌린다(@Transactional).
     * 캐시된 회원 객체의 포인트로 계산하지 않는다. 바꾼 뒤 회원 캐시를 지운다(다음 요청은 DB 값을 읽는다).
     */
    private void useIntegration(UmsMember member, Integer use) {
        if (use <= 0) {
            return;
        }
        if (moneyDao.useIntegration(member.getId(), use) == 0) {
            SecurityEventLogger.denied("points.denied.insufficient", "10-2")
                    .actor("member:" + member.getId())
                    .target("member:" + member.getId())
                    .reason("포인트 조건부 차감 0행(잔액 부족 또는 동시 사용)")
                    .log();
            Asserts.fail("포인트가 부족합니다");
        }
        memberCacheService.delMember(member.getId());
    }

    /** 다온마켓 10-2: 취소 환원도 DB 값에 더한다(읽고-더하고-쓰기를 하지 않는다) */
    private void refundIntegration(Long memberId, Integer amount) {
        if (!SecureKitSwitch.on("money-race")) {
            UmsMember member = memberService.getById(memberId);
            memberService.updateIntegration(memberId, member.getIntegration() + amount);
            return;
        }
        if (amount > 0) {
            moneyDao.refundIntegration(memberId, amount);
            memberCacheService.delMember(memberId);
        }
    }

    /**
     * 주문 상품의 전체 재고 잠금
     * 다온마켓 10-2: 원본은 읽고-더해-쓰기라 동시 주문이 잠금 수량을 서로 지웠다(점검 후보). 조건부 UPDATE로 바꾼다
     */
    private void lockStock(List<CartPromotionItem> cartPromotionItemList) {
        if (SecureKitSwitch.on("money-race")) {
            for (CartPromotionItem item : cartPromotionItemList) {
                if (moneyDao.lockStock(item.getProductSkuId(), item.getQuantity()) == 0) {
                    Asserts.fail("재고가 부족하여 주문할 수 없습니다");
                }
            }
            return;
        }
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(cartPromotionItem.getProductSkuId());
            skuStock.setLockStock(skuStock.getLockStock() + cartPromotionItem.getQuantity());
            skuStockMapper.updateByPrimaryKeySelective(skuStock);
        }
    }

    /**
     * 주문 상품에 모두 재고가 있는지 판단
     */
    private boolean hasStock(List<CartPromotionItem> cartPromotionItemList) {
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            if (cartPromotionItem.getRealStock()==null //실제 재고가 비어 있는지 판단
                    ||cartPromotionItem.getRealStock() <= 0 //실제 재고가 다음보다 작은지 판단0
                    || cartPromotionItem.getRealStock() < cartPromotionItem.getQuantity()) //실제 재고가 주문 수량보다 작은지 판단
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 장바구니 상품 가격 계산
     */
    private ConfirmOrderResult.CalcAmount calcCartAmount(List<CartPromotionItem> cartPromotionItemList) {
        ConfirmOrderResult.CalcAmount calcAmount = new ConfirmOrderResult.CalcAmount();
        calcAmount.setFreightAmount(new BigDecimal(0));
        BigDecimal totalAmount = new BigDecimal("0");
        BigDecimal promotionAmount = new BigDecimal("0");
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            totalAmount = totalAmount.add(cartPromotionItem.getPrice().multiply(new BigDecimal(cartPromotionItem.getQuantity())));
            promotionAmount = promotionAmount.add(cartPromotionItem.getReduceAmount().multiply(new BigDecimal(cartPromotionItem.getQuantity())));
        }
        calcAmount.setTotalAmount(totalAmount);
        calcAmount.setPromotionAmount(promotionAmount);
        calcAmount.setPayAmount(totalAmount.subtract(promotionAmount));
        return calcAmount;
    }

}
