package io.github.securekit.examples.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * secure-kit 부품 예제. 컨트롤러는 얇게 두고 일은 부품이 한다.
 * 계약 시험: packages/contract-tests (KIT_APPS=spring-demo)
 */
@SpringBootApplication
@EnableConfigurationProperties(DemoProperties.class)
public class KitDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(KitDemoApplication.class, args);
    }
}
