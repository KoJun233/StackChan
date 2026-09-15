package com.kj.stackchan.speech;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VoiceDialogueControlTest {
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
