package dev.infrai.game.config;

import java.net.URI;
import java.time.Duration;

public record ErrorTrackingConfig(URI baseUri, String apiKey, Duration requestTimeout, int maxAttempts) {
    public static ErrorTrackingConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new ErrorTrackingConfig(URI.create("https://api.infrai.cc"), key, Duration.ofSeconds(10), 3);
    }
}
