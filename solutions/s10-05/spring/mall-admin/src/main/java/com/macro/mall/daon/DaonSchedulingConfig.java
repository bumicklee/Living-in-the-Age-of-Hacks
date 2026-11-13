package com.macro.mall.daon;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** [다온마켓 10-5] 관리자 앱에서 예약 작업(매일 04:00 대사)을 켠다. 관리자 앱의 첫 @Scheduled다 */
@Configuration
@EnableScheduling
public class DaonSchedulingConfig {
}
