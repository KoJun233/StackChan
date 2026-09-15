package com.kj.stackchan.memory;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MemorySearchQueryTest {
    @Test
    void distinguishesMemoryInventoryFromSpecificRecallAndQuotedQuestions() {
        for (String query : new String[]{"你记得我什么", "你记得关于我的哪些事？", "你还了解我多少", "你记住了什么"}) {
            assertThat(MemorySearchQuery.parse(query).inventory()).as(query).isTrue();
        }
        for (String query : new String[]{"你记得我的咖啡偏好吗", "不要回答你记得我什么", "‘你记得我什么’是什么意思"}) {
            assertThat(MemorySearchQuery.parse(query).inventory()).as(query).isFalse();
        }
    }

    @Test
    void boundsQueriesWithoutSplittingUnicodeAndNeverTreatsRawPunctuationAsRegex() {
        var query = MemorySearchQuery.parse("😀".repeat(600));
        assertThat(query.text().codePointCount(0, query.text().length())).isEqualTo(500);
        assertThat(query.relevancePattern()).isEqualTo(MemorySearchQuery.parse(".*|[%_]").relevancePattern());
        assertThat(MemorySearchQuery.parse("I like music").relevancePattern()).doesNotContain("like");
    }
}
