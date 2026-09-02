package com.asher.bugfixer.adk;

import com.asher.bugfixer.AppConfig;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Gemini;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the ADK Gemini client from the same connector configuration OpenHands uses locally. */
public final class OpenCodeGeminiModelFactory {
    private OpenCodeGeminiModelFactory() {
    }

    public static BaseLlm create(AppConfig config) throws Exception {
        String apiKey = required(config.adkApiKey(), "ADK_API_KEY");
        String baseUrl = required(config.adkBaseUrl(), "ADK_BASE_URL");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-goog-api-key", apiKey);
        addHeader(headers, "Authorization", config.adkAuthorization());
        addHeader(headers, "X-IBM-Client-Id", config.adkClientId());
        addHeader(headers, "X-IBM-Client-Secret", config.adkClientSecret());

        Client client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .baseUrl(baseUrl)
                        .apiVersion("")
                        .headers(headers)
                        .build())
                .build();
        return Gemini.builder()
                .modelName(config.adkModel())
                .apiClient(client)
                .build();
    }

    private static String required(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured for ADK.");
        }
        return value;
    }

    private static void addHeader(Map<String, String> headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(name, value);
        }
    }
}
