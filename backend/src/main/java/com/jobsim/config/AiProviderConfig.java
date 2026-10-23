package com.jobsim.config;

import com.jobsim.service.ai.AiProvider;
import com.jobsim.service.ai.MockAiProvider;
import com.jobsim.service.ai.RemoteAiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI Provider 装配：
 * - MockAiProvider 始终存在（作为默认实现与远程失败后的降级兜底）
 * - 仅当 provider=remote 且配置了 API Key 时，主 Provider 才是 RemoteAiProvider
 * 这样"没有 API Key 系统也能完整跑通"这一目标在装配层就被保证。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiProviderConfig {

    private final AiProperties properties;

    @Bean
    public MockAiProvider mockAiProvider() {
        return new MockAiProvider();
    }

    @Bean
    @Primary
    public AiProvider aiProvider(MockAiProvider mock) {
        if ("remote".equalsIgnoreCase(properties.getProvider())) {
            if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
                log.warn("jobsim.ai.provider=remote，但未配置 AI_API_KEY，已自动降级为本地模拟评估");
                return mock;
            }
            log.info("AI 评估使用远程模型: {}", properties.getModel());
            return new RemoteAiProvider(properties);
        }
        log.info("AI 评估使用本地模拟实现（MockAiProvider）");
        return mock;
    }
}
