package com.kj.stackchan.memory;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded lexical recall; the aliases below do not claim general semantic understanding. */
record MemorySearchQuery(String text, String relevancePattern, boolean inventory) {
    private static final Pattern WORDS = Pattern.compile("\\p{IsHan}+|[a-z0-9]+");
    private static final Pattern INVENTORY = Pattern.compile(
            "^(?:你)?(?:还)?(?:(?:记得|记住|了解|知道)了?(?:关于)?我(?:的)?(?:哪些(?:事情|事|信息)?|什么|多少|吗)?|记住了?什么)[？?。！!]*$");
    private static final List<String> FILLERS = List.of(
            "你还记得", "你记得", "你知道", "你了解", "我喜欢", "我不喜欢", "我想知道", "我想问",
            "我问的是", "告诉我", "帮我", "请问", "说说", "聊聊", "详细", "一下", "什么", "哪些",
            "怎么样", "怎么", "为什么", "有没有", "是不是", "能不能", "记忆", "用户", "喜欢", "偏好",
            "我的", "你的", "之前", "最近", "现在", "今天", "明天", "昨天");
    private static final List<List<String>> ALIASES = List.of(
            List.of("咖啡", "coffee"), List.of("生日", "birthday"),
            List.of("称呼", "昵称", "nickname"), List.of("饮品", "饮料"),
            List.of("早饭", "早餐"), List.of("午饭", "午餐"), List.of("晚饭", "晚餐"),
            List.of("散步", "遛弯"), List.of("宠物", "pet"));
    private static final Set<String> ENGLISH_FILLERS = Set.of("the", "and", "or", "is", "are", "was", "were",
            "am", "do", "does", "did", "me", "my", "you", "your", "we", "our", "it", "its", "to", "of",
            "in", "on", "at", "for", "about", "what", "which", "why", "how", "please", "tell", "remember",
            "know", "like", "likes", "prefer", "preference", "preferences");

    static MemorySearchQuery parse(String input) {
        String text = Normalizer.normalize(input == null ? "" : input.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (text.codePointCount(0, text.length()) > 500) text = text.substring(0, text.offsetByCodePoints(0, 500));
        boolean inventory = text.isBlank() || INVENTORY.matcher(text).matches();
        String meaningful = text;
        for (String filler : FILLERS) meaningful = meaningful.replace(filler, " ");
        Set<String> terms = new LinkedHashSet<>();
        var words = WORDS.matcher(meaningful);
        while (words.find() && terms.size() < 64) {
            String word = words.group();
            int[] points = word.codePoints().toArray();
            if (Character.UnicodeScript.of(points[0]) == Character.UnicodeScript.HAN) {
                for (int i = 0; i + 1 < points.length && terms.size() < 64; i++) {
                    terms.add(new String(points, i, 2));
                }
            } else if (word.length() >= 2 && !ENGLISH_FILLERS.contains(word)) {
                terms.add(word);
            }
        }
        for (List<String> aliases : ALIASES) {
            if (aliases.stream().anyMatch(terms::contains)) terms.addAll(aliases);
        }
        // Every term originates from the restricted WORDS alphabet, never raw regex input.
        String pattern = terms.stream().map(term -> term.matches("[a-z0-9]+")
                        ? "(^|[^a-z0-9])" + term + "([^a-z0-9]|$)" : term)
                .collect(java.util.stream.Collectors.joining("|"));
        return new MemorySearchQuery(text, pattern.isEmpty() ? "a^" : pattern, inventory);
    }
}
