package com.kj.stackchan.speech;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VoiceDialogueControlTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "不是日历而是天气|CORRECTION|天气",
            "不是咖啡，而是茶|CORRECTION|茶",
            "不对，不是周一，是周二|CORRECTION|周二",
            "我问的是天气|CORRECTION|天气",
            "换个话题，聊音乐|NEW_TOPIC|聊音乐",
            "咱们换一个话题：说说月亮|NEW_TOPIC|说说月亮",
            "我们聊点别的，聊旅行|NEW_TOPIC|聊旅行",
            "接着说！|CONTINUE|接着说！",
            "展开说说|CONTINUE|展开说说",
            "先不聊。|DECLINE|先不聊。",
            "现在不聊了|DECLINE|现在不聊了",
            "结束聊天，|END|结束聊天，"
    })
    void controlScenariosPreserveOnlyTheExplicitCurrentObject(String transcript,
            VoiceDialogueControl.Kind kind, String object) {
        var control = VoiceDialogueControl.parse(transcript);
        assertThat(control.kind()).isEqualTo(kind);
        assertThat(control.routingText()).isEqualTo(object);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "“现在忙”这句话是什么意思", "他说先不聊", "不要结束聊天",
            "继续教育是什么", "我不想换个话题", "“不是A而是B”是什么意思？",
            "‘我问的是天气’是引用", "现在忙吗？", "结束聊天以后做什么"
    })
    void mentionsQuestionsAndNegationsDoNotCloseOrContinueTheTopic(String transcript) {
        assertThat(VoiceDialogueControl.parse(transcript).kind())
                .isEqualTo(VoiceDialogueControl.Kind.NONE);
    }
    @Test
    void oneTimeDeclineClosesTopicWithoutChangingLongTermPreferences() {
        for (String text : new String[]{"不聊这个", "先不聊了。", "现在忙", "我现在很忙"}) {
            assertThat(VoiceDialogueControl.parse(text).kind()).isEqualTo(VoiceDialogueControl.Kind.DECLINE);
            assertThat(VoiceDialogueControl.parse(text).closesTopic()).isTrue();
        }
        for (String text : new String[]{"他说现在忙", "不是不聊这个", "现在忙是什么意思", "不聊这个，聊音乐"}) {
            assertThat(VoiceDialogueControl.parse(text).closesTopic()).isFalse();
        }
    }
    @Test
    void endPhraseMatchesTheExistingFirmwareCommandWithoutEndingQuotedConversation() {
        for (String text : new String[]{"结束聊天", " 结束聊天？！ ", "结束聊天。", "结束聊天．"}) {
            assertThat(VoiceDialogueControl.parse(text).kind()).isEqualTo(VoiceDialogueControl.Kind.END);
        }
        for (String text : new String[]{"不要结束聊天", "我们聊聊怎么结束聊天", "‘结束聊天’是什么意思", "结束聊天以后怎么办", "　结束聊天"}) {
            assertThat(VoiceDialogueControl.parse(text).kind()).isEqualTo(VoiceDialogueControl.Kind.NONE);
        }
    }
    @Test
    void recognizesExplicitControlsWithoutTreatingMentionsAsCommands() {
        for (String text : new String[] {"换个话题", "我们换个话题。", "换个话题，聊聊音乐", "聊点别的"}) {
            assertThat(VoiceDialogueControl.parse(text).kind()).isEqualTo(VoiceDialogueControl.Kind.NEW_TOPIC);
        }
        for (String text : new String[] {"不要换个话题", "换个话题是什么意思", "他说换个话题", "“换个话题”", "继续教育"}) {
            assertThat(VoiceDialogueControl.parse(text).kind()).isEqualTo(VoiceDialogueControl.Kind.NONE);
        }
        assertThat(VoiceDialogueControl.parse("不是日程，是明天的天气").routingText()).isEqualTo("明天的天气");
        assertThat(VoiceDialogueControl.parse("我问的是待办").routingText()).isEqualTo("待办");
        assertThat(VoiceDialogueControl.parse("接着讲。").kind()).isEqualTo(VoiceDialogueControl.Kind.CONTINUE);
        assertThat(VoiceDialogueControl.parse("继续").guidance()).contains("不重新复述", "动态事实查询");
    }
}
