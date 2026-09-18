package dev.infrai.game.client;

import dev.infrai.game.config.ErrorTrackingConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiErrorsClient {
    private final ErrorTrackingConfig config;
    private final HttpClient http;

    public InfraiErrorsClient(ErrorTrackingConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiErrorsClient(ErrorTrackingConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Map<String, Object> capture(String exceptionPayload, String idempotencyKey)
            throws IOException, InterruptedException, InfraiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exception", exceptionPayload);
        return call("POST", "/v1/errors/capture", Json.write(body), idempotencyKey);
    }

    private Map<String, Object> call(String method, String path, String json, String idempotencyKey)
            throws IOException, InterruptedException, InfraiException {
        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUri() + path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = Json.object(Json.parse(response.body()));
            if (response.statusCode() == 429 && attempt + 1 < config.maxAttempts()) {
                Thread.sleep(retryDelay(response, attempt).toMillis());
                continue;
            }
            Object ok = envelope.get("ok");
            if (!Boolean.TRUE.equals(ok)) {
                Map<String, Object> error = Json.object(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.get("code")), error, response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport response " + response.statusCode());
            }
            return Json.object(envelope.get("data"));
        }
        throw new IOException("Infrai retry budget exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            return Duration.ofMillis(250L << attempt);
        }
    }

    public static final class InfraiException extends Exception {
        private final String code;
        private final Map<String, Object> detail;
        private final int statusCode;

        public InfraiException(String code, Map<String, Object> detail, int statusCode) {
            super(code + ": " + detail);
            this.code = code;
            this.detail = Map.copyOf(detail);
            this.statusCode = statusCode;
        }

        public String code() { return code; }
        public Map<String, Object> detail() { return detail; }
        public int statusCode() { return statusCode; }
    }

    static final class Json {
        private final String source;
        private int index;

        private Json(String source) { this.source = source; }

        static Object parse(String source) throws IOException {
            Json parser = new Json(source);
            Object value = parser.value();
            parser.space();
            if (parser.index != source.length()) throw new IOException("Trailing JSON content");
            return value;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> object(Object value) throws IOException {
            if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
            if (value == null) return Map.of();
            throw new IOException("Expected JSON object");
        }

        static String write(Map<String, Object> value) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : value.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(quote(entry.getKey())).append(':').append(quote(String.valueOf(entry.getValue())));
            }
            return out.append('}').toString();
        }

        private Object value() throws IOException {
            space();
            if (index >= source.length()) throw new IOException("Empty JSON response");
            char c = source.charAt(index);
            if (c == '{') return objectValue();
            if (c == '[') return arrayValue();
            if (c == '"') return string();
            if (source.startsWith("true", index)) { index += 4; return true; }
            if (source.startsWith("false", index)) { index += 5; return false; }
            if (source.startsWith("null", index)) { index += 4; return null; }
            return number();
        }

        private Map<String, Object> objectValue() throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            index++;
            space();
            if (take('}')) return map;
            do {
                space();
                String key = string();
                space();
                expect(':');
                map.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return map;
        }

        private java.util.List<Object> arrayValue() throws IOException {
            java.util.List<Object> list = new java.util.ArrayList<>();
            index++;
            space();
            if (take(']')) return list;
            do { list.add(value()); space(); } while (take(','));
            expect(']');
            return list;
        }

        private String string() throws IOException {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return out.toString();
                if (c == '\\') {
                    if (index >= source.length()) throw new IOException("Bad JSON escape");
                    char e = source.charAt(index++);
                    if (e == 'u') {
                        if (index + 4 > source.length()) throw new IOException("Bad unicode escape");
                        out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    } else {
                        out.append(switch (e) {
                            case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                            case 'b' -> '\b'; case 'f' -> '\f'; case '"' -> '"';
                            case '\\' -> '\\'; case '/' -> '/';
                            default -> throw new IOException("Bad JSON escape");
                        });
                    }
                } else out.append(c);
            }
            throw new IOException("Unterminated JSON string");
        }

        private Number number() throws IOException {
            int start = index;
            while (index < source.length() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            try { return Double.valueOf(source.substring(start, index)); }
            catch (NumberFormatException e) { throw new IOException("Bad JSON value", e); }
        }

        private void space() { while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++; }
        private boolean take(char c) { if (index < source.length() && source.charAt(index) == c) { index++; return true; } return false; }
        private void expect(char c) throws IOException { if (!take(c)) throw new IOException("Expected '" + c + "'"); }
        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
        }
    }
}
