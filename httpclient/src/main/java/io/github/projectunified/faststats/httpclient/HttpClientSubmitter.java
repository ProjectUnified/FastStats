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
    private final URI uri;
    private final String token;
    private final String userAgent;

    /**
     * Constructs a new {@link HttpClientSubmitter} with the default metrics URI
     * ({@code https://metrics.faststats.dev/v1/collect}), standard {@link HttpClient},
     * and default user agent.
     *
     * @param token the authorization token (bearer)
     */
    public HttpClientSubmitter(String token) {
        this(URI.create(Submitter.DEFAULT_URL), token);
    }

    /**
     * Constructs a new {@link HttpClientSubmitter} with standard {@link HttpClient}
     * and default user agent.
     *
     * @param uri   the target metrics URI
     * @param token the authorization token (bearer)
     */
    public HttpClientSubmitter(URI uri, String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), uri, token, BuildInfo.getDefaultUserAgent());
    }

    /**
     * Constructs a new {@link HttpClientSubmitter}.
     *
     * @param httpClient the HttpClient instance to use
     * @param uri        the target metrics URI
     * @param token      the authorization token (bearer)
     * @param userAgent  the user agent header value
     */
    public HttpClientSubmitter(HttpClient httpClient, URI uri, String token, String userAgent) {
        this.httpClient = httpClient;
        this.uri = uri;
        this.token = token;
        this.userAgent = userAgent;
    }

    @Override
    public void execute(String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(byteOutput)) {
            output.write(bytes);
            output.finish();
        }
        byte[] compressed = byteOutput.toByteArray();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofByteArray(compressed))
                .header("Content-Encoding", "gzip")
                .header("Content-Type", "application/octet-stream")
                .timeout(Duration.ofSeconds(3))
                .uri(uri);

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (userAgent != null && !userAgent.isEmpty()) {
            builder.header("User-Agent", userAgent);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int responseCode = response.statusCode();
        if (responseCode < 200 || responseCode >= 300) {
            String responseBody = response.body();
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                throw new Exception("HTTP request failed with status code: " + responseCode + " (" + responseBody.trim() + ")");
            } else {
                throw new Exception("HTTP request failed with status code: " + responseCode);
            }
        }
    }
}
