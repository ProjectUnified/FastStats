package io.github.projectunified.faststats.net;

import io.github.projectunified.faststats.core.BuildInfo;
import io.github.projectunified.faststats.core.Submitter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of {@link Submitter} that uses {@link HttpURLConnection}
 * to submit GZIP-compressed telemetry payloads.
 */
public class NetSubmitter implements Submitter {
    private final String baseUrl;
    private final String token;
    private final String userAgent;

    /**
     * Constructs a new {@link NetSubmitter} with default settings.
     *
     * @param token the authorization token (bearer)
     */
    public NetSubmitter(String token) {
        this(Submitter.DEFAULT_BASE_URL, token, BuildInfo.getDefaultUserAgent());
    }

    /**
     * Constructs a new {@link NetSubmitter}.
     *
     * @param baseUrl   the base URL
     * @param token     the authorization token (bearer)
     * @param userAgent the user agent header value
     */
    public NetSubmitter(String baseUrl, String token, String userAgent) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.userAgent = userAgent;
    }

    @Override
    public void execute(String path, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(byteOutput)) {
            output.write(bytes);
            output.finish();
        }
        byte[] compressed = byteOutput.toByteArray();

        String fullUrl;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            fullUrl = path;
        } else {
            fullUrl = this.baseUrl + path;
        }

        URL targetUrl = new URL(fullUrl);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);

        connection.setRequestProperty("Content-Encoding", "gzip");
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        if (token != null && !token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (userAgent != null && !userAgent.isEmpty()) {
            connection.setRequestProperty("User-Agent", userAgent);
        }

        try (OutputStream out = connection.getOutputStream()) {
            out.write(compressed);
            out.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            String responseBody = "";
            try (InputStream errorStream = connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream()) {
                if (errorStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        responseBody = sb.toString().trim();
                    }
                }
            } catch (Exception ignored) {
            }
            if (!responseBody.isEmpty()) {
                throw new Exception("HTTP request failed with status code: " + responseCode + " (" + responseBody + ")");
            } else {
                throw new Exception("HTTP request failed with status code: " + responseCode);
            }
        }
    }
}
