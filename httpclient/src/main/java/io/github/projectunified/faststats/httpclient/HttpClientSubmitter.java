package io.github.projectunified.faststats.httpclient;

import io.github.projectunified.faststats.core.BuildInfo;
import io.github.projectunified.faststats.core.Submitter;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of {@link Submitter} that uses {@link HttpClient}
 * to submit GZIP-compressed telemetry payloads.
 */
public class HttpClientSubmitter implements Submitter {
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String token;
    private final String userAgent;

    /**
     * Constructs a new {@link HttpClientSubmitter} with standard {@link HttpClient}.
     *
     * @param token the authorization token (bearer)
     */
    public HttpClientSubmitter(String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), token);
    }

    /**
     * Constructs a new {@link HttpClientSubmitter} with standard {@link HttpClient}.
     *
     * @param httpClient the HttpClient instance to use
     * @param token      the authorization token (bearer)
     */
    public HttpClientSubmitter(HttpClient httpClient, String token) {
        this(httpClient, Submitter.DEFAULT_BASE_URL, token, BuildInfo.getDefaultUserAgent());
    }

    /**
     * Constructs a new {@link HttpClientSubmitter}.
     *
     * @param httpClient the HttpClient instance to use
     * @param baseUrl    the base URL
     * @param token      the authorization token (bearer)
     * @param userAgent  the user agent header value
     */
    public HttpClientSubmitter(HttpClient httpClient, String baseUrl, String token, String userAgent) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.token = token;
        this.userAgent = userAgent;
    }


    @Override
    public String execute(String path, String json, boolean compressed) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] payload;

        if (compressed) {
            ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
            try (GZIPOutputStream output = new GZIPOutputStream(byteOutput)) {
                output.write(bytes);
                output.finish();
            }
            payload = byteOutput.toByteArray();
        } else {
            payload = bytes;
        }

        String fullUrl;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            fullUrl = path;
        } else {
            fullUrl = this.baseUrl + path;
        }

        URI targetUri = URI.create(fullUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .timeout(Duration.ofSeconds(3))
                .uri(targetUri);

        if (compressed) {
            builder.header("Content-Encoding", "gzip");
            builder.header("Content-Type", "application/octet-stream");
        } else {
            builder.header("Content-Type", "application/json");
        }

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (userAgent != null && !userAgent.isEmpty()) {
            builder.header("User-Agent", userAgent);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int responseCode = response.statusCode();
        String responseBody = response.body();
        if (responseCode < 200 || responseCode >= 300) {
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                throw new Exception("HTTP request failed with status code: " + responseCode + " (" + responseBody.trim() + ")");
            } else {
                throw new Exception("HTTP request failed with status code: " + responseCode);
            }
        }
        return responseBody;
    }
}
