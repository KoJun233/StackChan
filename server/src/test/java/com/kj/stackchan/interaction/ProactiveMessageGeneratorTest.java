package com.kj.stackchan.interaction;

import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.reminder.ProactiveGenerationStatus;
import com.kj.stackchan.role.CompanionRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProactiveMessageGeneratorTest {

    @Test
    void usesFixedContentWithoutCreatingAProviderClientWhenNoMemoryIsEligible() {
        LlmRuntimeClientFactory factory = mock(LlmRuntimeClientFactory.class);
        ProactiveMessageGenerator generator = new ProactiveMessageGenerator(Runnable::run, factory);

        var result = generator.generate("固定问候", null);

        assertThat(result.content()).isEqualTo("固定问候");
        assertThat(result.status()).isEqualTo(ProactiveGenerationStatus.FIXED);
        verifyNoInteractions(factory);
    }

    @Test
    void acceptsOneBoundedPlainChineseSentence() {
        var fixture = fixture("今天也可以来一杯美式，记得适量喝水。", false);

        var result = fixture.generator.generate("固定问候", fixture.memory);

        assertThat(result.content()).isEqualTo("今天也可以来一杯美式，记得适量喝水。");
        assertThat(result.status()).isEqualTo(ProactiveGenerationStatus.GENERATED);
    }

    @Test
    void generatesFromTheCurrentPersonaEvenWithoutAnInterestMemory() {
        LlmRuntimeClientFactory factory = mock(LlmRuntimeClientFactory.class);
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        CompanionRoleService.RoleSnapshot role = mock(CompanionRoleService.RoleSnapshot.class);
        when(role.name()).thenReturn("小栈");
        when(role.tone()).thenReturn(com.kj.stackchan.persona.PersonaTone.LIVELY);
        when(role.replyLength()).thenReturn(com.kj.stackchan.persona.PersonaReplyLength.SHORT);
        when(role.proactivity()).thenReturn(com.kj.stackchan.persona.PersonaProactivity.BALANCED);
        when(role.backgroundInstructions()).thenReturn("像熟悉游戏的桌面伙伴");
        when(role.topicBoundaries()).thenReturn("");
        when(role.taboos()).thenReturn("不要说教");
        when(factory.createChatClient()).thenReturn(client);
        when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("我来冒个泡，想聊两句就叫我呀。");

        var result = new ProactiveMessageGenerator(Runnable::run, factory)
                .generate("固定问候", null, role);

        assertThat(result.content()).isEqualTo("我来冒个泡，想聊两句就叫我呀。");
        assertThat(result.status()).isEqualTo(ProactiveGenerationStatus.GENERATED);
    }

    @Test
    void fallsBackWhenProviderOutputViolatesPolicy() {
        var fixture = fixture("你可能有抑郁症，请访问 https://example.com", false);

        var result = fixture.generator.generate("固定问候", fixture.memory);

        assertThat(result.content()).isEqualTo("固定问候");
        assertThat(result.status()).isEqualTo(ProactiveGenerationStatus.FALLBACK);
    }

    @Test
    void fallsBackForMarkdownEvenWithoutAUrl() {
        var fixture = fixture("**今天记得喝水**", false);

        var result = fixture.generator.generate("固定问候", fixture.memory);

        assertThat(result.content()).isEqualTo("固定问候");
        assertThat(result.status()).isEqualTo(ProactiveGenerationStatus.FALLBACK);
    }

    @Test
    void rejectsCurrentEventClaimsWhenNoExternalSourceWasProvided() {
        var fixture = fixture("最近又发布了一个最新 AI 模型，我们来聊聊吧。", false);

        var result = fixture.generator.generate("固定问候", fixture.memory);

        assertThat(result.content()).isEqualTo("固定问候");
        assertThat(result.status()).isEqualTo(ProactiveGenerationStatus.FALLBACK);
    }

    @Test
    void fallsBackWithoutLeakingProviderFailure() {
        var fixture = fixture(null, true);

        var result = fixture.generator.generate("固定问候", fixture.memory);

        assertThat(result.content()).isEqualTo("固定问候");
        assertThat(result.status()).isEqualTo(ProactiveGenerationStatus.FALLBACK);
    }

    private Fixture fixture(String output, boolean fail) {
        LlmRuntimeClientFactory factory = mock(LlmRuntimeClientFactory.class);
        LongTermMemoryService.MemorySnapshot memory = mock(LongTermMemoryService.MemorySnapshot.class);
        when(memory.title()).thenReturn("饮品偏好");
        when(memory.content()).thenReturn("用户喜欢喝美式咖啡");
        if (fail) {
            when(factory.createChatClient()).thenThrow(new IllegalStateException("provider secret payload"));
        }
        else {
            ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
            when(factory.createChatClient()).thenReturn(client);
            when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(output);
        }
        return new Fixture(new ProactiveMessageGenerator(Runnable::run, factory), memory);
    }

    private record Fixture(ProactiveMessageGenerator generator, LongTermMemoryService.MemorySnapshot memory) {
    }
}
