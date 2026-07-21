package com.kj.stackchan.speech;

import java.net.URI;
import java.net.URISyntaxException;

final class DashScopeEndpoints {

    private static final String WORKSPACE_DOMAIN_SUFFIX = ".cn-beijing.maas.aliyuncs.com";
    private static final String RESULT_AUDIO_HOST = "dashscope-result-bj.oss-cn-beijing.aliyuncs.com";

    private DashScopeEndpoints() {
    }

    static URI webSocket(String workspaceId) {
        return URI.create("wss://" + workspaceHost(workspaceId) + "/api-ws/v1/inference");
    }

    static URI asrHttp(String workspaceId) {
        return URI.create("https://" + workspaceHost(workspaceId)
                + "/api/v1/services/aigc/multimodal-generation/generation");
    }

    static URI ttsHttp(String workspaceId) {
        return URI.create("https://" + workspaceHost(workspaceId)
                + "/api/v1/services/audio/tts/SpeechSynthesizer");
    }

    static URI validatedDownloadUri(String value) {
        try {
            URI uri = new URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || !RESULT_AUDIO_HOST.equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1
                    || uri.getRawFragment() != null) {
                throw new SpeechProviderUnavailableException();
            }
            if ("http".equalsIgnoreCase(uri.getScheme())) {
                return URI.create("https" + value.substring(value.indexOf(':')));
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new SpeechProviderUnavailableException(exception);
        }
    }

    private static String workspaceHost(String workspaceId) {
        return workspaceId + WORKSPACE_DOMAIN_SUFFIX;
    }
}
