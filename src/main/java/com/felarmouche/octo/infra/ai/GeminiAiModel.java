package com.felarmouche.octo.infra.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

/** Ruft ein Gemini-Modell über Spring AI auf, mit strukturierter Ausgabe des Providers. */
@Component
public class GeminiAiModel implements AiModel {

    private final ChatClient chatClient;

    public GeminiAiModel(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public <T> T call(GeminiFallbackProperties.Model model, AiRequest<T> request) {
        return chatClient.prompt()
                .system(request.systemPrompt())
                .user(u -> u.text(request.userTemplate())
                        .params(request.params())
                        .media(request.images().toArray(Media[]::new)))
                .options(GoogleGenAiChatOptions.builder()
                        .model(model.name())
                        .thinkingLevel(model.thinkingLevel()))
                .call()
                .entity(request.responseType(), ChatClient.EntityParamSpec::useProviderStructuredOutput);
    }
}
