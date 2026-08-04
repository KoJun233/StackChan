package com.kj.stackchan.memory;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class MemorySuggestionSafetyPolicy {

    private static final Pattern CHINESE_ID = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");
    private static final Pattern EXACT_ADDRESS = Pattern.compile(
            "(?:省|市|区|县|镇|乡|街道|路|街|巷|弄|小区|大厦|楼|栋|单元).{0,16}\\d+(?:号|室|层)"
    );
    private static final List<String> CREDENTIAL_TERMS = List.of(
            "密码", "口令", "验证码", "密钥", "api key", "apikey", "access token", "refresh token", "令牌", "secret"
    );
    private static final List<String> FINANCIAL_TERMS = List.of(
            "收入", "工资", "资产", "负债", "存款", "欠款", "信用评分", "财务状况", "有钱", "贫穷", "银行卡"
    );
    private static final List<String> MEDICAL_TERMS = List.of(
            "诊断", "病史", "疾病", "抑郁", "焦虑症", "精神疾病", "处方", "用药", "过敏史", "医疗状况"
    );

    public boolean isAllowed(String title, String content, String reason) {
        String combined = String.join(" ", safe(title), safe(content), safe(reason)).toLowerCase(Locale.ROOT);
        return !CHINESE_ID.matcher(combined).find()
                && !BANK_CARD.matcher(combined).find()
                && !EXACT_ADDRESS.matcher(combined).find()
                && !containsAny(combined, CREDENTIAL_TERMS)
                && !containsAny(combined, FINANCIAL_TERMS)
                && !containsAny(combined, MEDICAL_TERMS);
    }

    private boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
