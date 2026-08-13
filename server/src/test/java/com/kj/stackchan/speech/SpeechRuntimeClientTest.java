package com.kj.stackchan.speech;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.role.CompanionRoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeechRuntimeClientTest {

    @Mock
    private SpeechSettingsService settingsService;

    @Mock
    private OpenAiCompatibleSpeechProviderAdapter openAiCompatible;

    @Mock
    private DashScopeSpeechProviderAdapter dashScope;

    @Mock
    private CompanionRoleService roleService;

    @Test
    void usesTheRoleVoiceOverrideForSynthesis() {
        UUID roleId = UUID.randomUUID();
        ResolvedSpeechSettings settings = settings("global-voice");
        ResolvedSpeechSettings overridden = settings.withTtsVoice("role-voice");
        byte[] audio = new byte[] {1, 2, 3};
        when(settingsService.resolveForInvocation()).thenReturn(settings);
        when(roleService.get(roleId)).thenReturn(role(roleId, "role-voice"));
        when(openAiCompatible.synthesize(overridden, "你好")).thenReturn(audio);

        assertThat(clientWithRoles().synthesize("你好", roleId)).isSameAs(audio);

        verify(openAiCompatible).synthesize(overridden, "你好");
    }

    @Test
    void retriesTheGlobalVoiceOnceWhenTheRoleVoiceFails() {
        UUID roleId = UUID.randomUUID();
        ResolvedSpeechSettings settings = settings("global-voice");
        ResolvedSpeechSettings overridden = settings.withTtsVoice("role-voice");
        byte[] audio = new byte[] {4, 5, 6};
        when(settingsService.resolveForInvocation()).thenReturn(settings);
        when(roleService.get(roleId)).thenReturn(role(roleId, "role-voice"));
        when(openAiCompatible.synthesize(overridden, "你好"))
                .thenThrow(new SpeechProviderUnavailableException());
        when(openAiCompatible.synthesize(settings, "你好")).thenReturn(audio);

        assertThat(clientWithRoles().synthesize("你好", roleId)).isSameAs(audio);

        verify(openAiCompatible).synthesize(overridden, "你好");
        verify(openAiCompatible).synthesize(settings, "你好");
    }

    @Test
    void routesDashScopeCallsThroughTheNativeAdapter() {
        ResolvedSpeechSettings settings = new ResolvedSpeechSettings(
                SpeechProviderType.DASHSCOPE,
                "",
                "llm-workspace123",
                "fun-asr-realtime",
                SpeechAccessMode.REALTIME,
                "qwen-audio-3.0-tts-flash",
                SpeechAccessMode.NON_REALTIME,
                "longanhuan_v3.6",
                "secret"
        );
        byte[] wav = new byte[] {1, 2, 3};
        when(settingsService.resolveForInvocation()).thenReturn(settings);
        when(dashScope.transcribe(settings, wav)).thenReturn("测试语音");

        assertThat(client().transcribe(wav)).isEqualTo("测试语音");
        verify(dashScope).transcribe(settings, wav);
    }

    @Test
    void connectionTestExercisesSynthesisAndRecognition() {
        ResolvedSpeechSettings settings = new ResolvedSpeechSettings(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1",
                "",
                "asr",
                SpeechAccessMode.NON_REALTIME,
                "tts",
                SpeechAccessMode.NON_REALTIME,
                "voice",
                "secret"
        );
        byte[] wav = new byte[] {1, 2, 3};
        when(settingsService.resolveForInvocation()).thenReturn(settings);
        when(openAiCompatible.synthesize(settings, "你好，我是 StackChan。语音服务连接正常。"))
                .thenReturn(wav);
        when(openAiCompatible.transcribeSynthesized(settings, wav)).thenReturn("你好");

        client().testConnection();

        verify(openAiCompatible).synthesize(settings, "你好，我是 StackChan。语音服务连接正常。");
        verify(openAiCompatible).transcribeSynthesized(settings, wav);
    }

    @Test
    void preservesSafeProviderStageForConnectionDiagnostics() {
        ResolvedSpeechSettings settings = new ResolvedSpeechSettings(
                SpeechProviderType.DASHSCOPE,
                "",
                "llm-workspace123",
                "fun-asr-realtime",
                SpeechAccessMode.REALTIME,
                "qwen-audio-3.0-tts-flash",
                SpeechAccessMode.NON_REALTIME,
                "longanhuan_v3.6",
                "secret"
        );
        when(settingsService.resolveForInvocation()).thenReturn(settings);
        when(dashScope.synthesize(settings, "你好，我是 StackChan。语音服务连接正常。"))
                .thenThrow(new SpeechProviderUnavailableException("dashscope_tts_request_http_401"));

        assertThatThrownBy(() -> client().testConnection())
                .isInstanceOf(SpeechProviderUnavailableException.class)
                .extracting(exception -> ((SpeechProviderUnavailableException) exception).diagnosticCode())
                .isEqualTo("dashscope_tts_request_http_401");
    }

    private SpeechRuntimeClient client() {
        return new SpeechRuntimeClient(settingsService, openAiCompatible, dashScope);
    }

    private SpeechRuntimeClient clientWithRoles() {
        return new SpeechRuntimeClient(settingsService, openAiCompatible, dashScope, roleService);
    }

    private ResolvedSpeechSettings settings(String voice) {
        return new ResolvedSpeechSettings(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1",
                "",
                "asr",
                SpeechAccessMode.NON_REALTIME,
                "tts",
                SpeechAccessMode.NON_REALTIME,
                voice,
                "secret"
        );
    }

    private CompanionRoleService.RoleSnapshot role(UUID roleId, String voice) {
        Instant now = Instant.parse("2026-08-13T10:00:00Z");
        return new CompanionRoleService.RoleSnapshot(
                roleId, "测试角色", PersonaTone.WARM, PersonaReplyLength.BALANCED,
                PersonaProactivity.BALANCED, "", "", "", false, voice, null, now, now
        );
    }
}
