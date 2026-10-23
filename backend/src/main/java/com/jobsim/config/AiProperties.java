package com.jobsim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 服务配置（对应 application.yml 的 jobsim.ai）。
 * provider=mock：使用本地模拟评估（默认，无需 API Key）。
 * provider=remote：调用 OpenAI 兼容接口；未配置 Key 时自动降级为 mock。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jobsim.ai")
public class AiProperties {

    private String provider = "mock";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int timeoutSeconds = 30;
    /** 请求是否携带 response_format={"type":"json_object"}（部分兼容服务不支持时可关闭） */
    private boolean jsonMode = true;
}
