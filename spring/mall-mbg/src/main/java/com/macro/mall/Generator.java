package com.macro.mall;

import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.internal.DefaultShellCallback;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * MBG코드 생성 도구
 * Created by macro on 2018/4/26.
 */
public class Generator {
    public static void main(String[] args) throws Exception {
        //MBG 실행 중 경고 메시지
        List<String> warnings = new ArrayList<String>();
        //생성된 코드가 중복되면 기존 코드를 덮어씀
        boolean overwrite = true;
        //MBG 설정 파일 읽기
        InputStream is = Generator.class.getResourceAsStream("/generatorConfig.xml");
        ConfigurationParser cp = new ConfigurationParser(warnings);
        Configuration config = cp.parseConfiguration(is);
        is.close();

        DefaultShellCallback callback = new DefaultShellCallback(overwrite);
        //생성 MBG
        MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config, callback, warnings);
        //코드 생성 실행
        myBatisGenerator.generate(null);
        //경고 정보 출력
        for (String warning : warnings) {
            System.out.println(warning);
        }
    }
}
