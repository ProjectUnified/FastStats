package io.github.projectunified.faststats.net;

import io.github.projectunified.faststats.core.BuildInfo;
import io.github.projectunified.faststats.core.Submitter;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of {@link Submitter} that uses {@link HttpURLConnection}
 * to submit GZIP-compressed telemetry payloads.
 */
public class NetSubmitter implements Submitter {
    private final URL url;
    private final String token;
    private final String userAgent;

    /**
     * Constructs a new {@link NetSubmitter} with the default metrics URL
     * ({@code https://metrics.faststats.dev/v1/collect}) and default user agent.
     *
     * @param token the authorization token (bearer)
     */
    public NetSubmitter(String token) {
        this(getDefaultURL(), token, BuildInfo.getDefaultUserAgent());
    }

    /**
     * Constructs a new {@link NetSubmitter}.
     *
     * @param url       the target metrics URL
     * @param token     the authorization token (bearer)
     * @param userAgent the user agent header value
     */
    public NetSubmitter(URL url, String token, String userAgent) {
        this.url = url;
        this.token = token;
        this.userAgent = userAgent;
    }

    private static URL getDefaultURL() {
        try {
            return new URL(Submitter.DEFAULT_URL);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid URL: " + Submitter.DEFAULT_URL, e);
        }
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

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
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
            throw new Exception("HTTP request failed with status code: " + responseCode);
        }
    }
}
