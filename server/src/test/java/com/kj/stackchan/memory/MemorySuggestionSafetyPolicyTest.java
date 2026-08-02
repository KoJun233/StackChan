package com.kj.stackchan.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySuggestionSafetyPolicyTest {

    private final MemorySuggestionSafetyPolicy policy = new MemorySuggestionSafetyPolicy();

    @Test
    void rejectsCredentialsExactAddressesIdentityFinancialAndMedicalContent() {
        assertThat(policy.isAllowed("登录", "API Key 是 abc", "偏好")).isFalse();
        assertThat(policy.isAllowed("住址", "上海市浦东新区世纪大道100号", "事件")).isFalse();
        assertThat(policy.isAllowed("身份", "身份证 110101199001011234", "资料")).isFalse();
        assertThat(policy.isAllowed("收入", "用户工资很高", "推断")).isFalse();
        assertThat(policy.isAllowed("健康", "用户可能有焦虑症", "推断")).isFalse();
    }

    @Test
    void allowsOrdinaryExplicitPreference() {
        assertThat(policy.isAllowed("称呼偏好", "用户喜欢被称为阿俊", "用户在本轮明确表达")).isTrue();
    }
}
