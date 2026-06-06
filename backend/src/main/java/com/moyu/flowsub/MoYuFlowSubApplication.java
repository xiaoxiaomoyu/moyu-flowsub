package com.moyu.flowsub;

import com.moyu.flowsub.asr.AsrProperties;
import com.moyu.flowsub.qiniu.QiniuAiProperties;
import com.moyu.flowsub.qiniu.QiniuProperties;
import com.moyu.flowsub.translation.DeepSeekProperties;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@SpringBootApplication
@EnableConfigurationProperties({QiniuProperties.class, QiniuAiProperties.class, AsrProperties.class, DeepSeekProperties.class})
public class MoYuFlowSubApplication {

    public static void main(String[] args) {
        loadLocalEnvFiles();

        SpringApplication.run(MoYuFlowSubApplication.class, args);
    }

    private static void loadLocalEnvFiles() {
        Path workDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Set<Path> candidateDirs = new LinkedHashSet<>();
        candidateDirs.add(workDir);
        if (workDir.getParent() != null) {
            candidateDirs.add(workDir.getParent());
        }

        for (Path dir : candidateDirs) {
            // 本地开发可能从项目根目录或 backend 目录启动，两处都尝试读取 .env。
            Dotenv dotenv = Dotenv.configure()
                    .directory(dir.toString())
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(entry -> {
                // 系统环境变量优先，避免 .env 覆盖用户在终端里临时注入的真实密钥。
                if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        }
    }
}
