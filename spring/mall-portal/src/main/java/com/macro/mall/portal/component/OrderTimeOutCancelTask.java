package com.macro.mall.portal.component;

import com.macro.mall.portal.service.OmsPortalOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기한 초과 주문을 취소하고 재고 잠금을 해제하는 타이머
 * Created by macro on 2018/8/24.
 */
//@Component
public class OrderTimeOutCancelTask {
    private final Logger LOGGER = LoggerFactory.getLogger(OrderTimeOutCancelTask.class);
    @Autowired
    private OmsPortalOrderService portalOrderService;

    /**
     * cron표현식:Seconds Minutes Hours DayofMonth Month DayofWeek [Year]
     * 10분마다 스캔하여 결제 만료된 미결제 주문을 취소 처리
     */
    @Scheduled(cron = "0 0/10 * ? * ?")
    private void cancelTimeOutOrder(){
        Integer count = portalOrderService.cancelTimeOutOrder();
        LOGGER.info("주문을 취소하고 sku 번호 기준으로 잠긴 재고를 해제함, 취소한 주문 수:{}",count);
    }
}
