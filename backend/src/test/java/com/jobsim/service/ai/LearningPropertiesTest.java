package com.jobsim.service.ai;

import com.jobsim.config.LearningProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GitHub 认证头处理规则（401 修复的核心守护）：
 * 未配置 Token 时必须返回 null（完全不发送 Authorization 头），
 * 绝不返回 "Bearer "（空凭据会被 GitHub 判 Bad Credentials → 401，真实事故）。
 */
class LearningPropertiesTest {

    private LearningProperties props(String token) {
        LearningProperties p = new LearningProperties();
        p.setGithubToken(token);
        return p;
    }

    @Test
    @DisplayName("未配置 Token：返回 null（不发送 Authorization 头，走匿名 60 次/时）")
    void noTokenReturnsNull() {
        assertThat(props(null).githubAuthHeader()).isNull();
        assertThat(props("").githubAuthHeader()).isNull();
        assertThat(props("   ").githubAuthHeader()).isNull();
        assertThat(props(null).hasToken()).isFalse();
    }

    @Test
    @DisplayName("配置了 Token：返回 Bearer + trim 后的值（防前后空格导致的 401）")
    void tokenReturnsBearer() {
        assertThat(props("ghp_valid").githubAuthHeader()).isEqualTo("Bearer ghp_valid");
        assertThat(props(" ghp_with_spaces ").githubAuthHeader()).isEqualTo("Bearer ghp_with_spaces");
        assertThat(props("ghp_valid").hasToken()).isTrue();
    }

    @Test
    @DisplayName("绝不产生空 Bearer（'Bearer ' 即 401 根因，历史 bug 守护）")
    void neverProducesEmptyBearer() {
        for (String token : new String[]{null, "", " ", "\t"}) {
            String header = props(token).githubAuthHeader();
            assertThat(header == null || !header.isBlank() || !"Bearer ".equals(header))
                    .as("token=%s 不得产生空 Bearer 头", token).isTrue();
        }
    }
}
