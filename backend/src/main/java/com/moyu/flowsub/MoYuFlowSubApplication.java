package com.moyu.flowsub;

import com.moyu.flowsub.asr.AsrProperties;
import com.moyu.flowsub.qiniu.QiniuProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({QiniuProperties.class, AsrProperties.class})
public class MoYuFlowSubApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoYuFlowSubApplication.class, args);
    }
}
