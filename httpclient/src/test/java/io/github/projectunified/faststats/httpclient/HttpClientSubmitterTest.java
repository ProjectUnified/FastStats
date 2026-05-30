package io.github.projectunified.faststats.httpclient;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class HttpClientSubmitterTest {

    private HttpServer server;
    private int port;
    private volatile byte[] receivedBody;
    private volatile String receivedContentEncoding;
    private volatile String receivedContentType;
    private volatile String receivedAuthorization;
    private volatile String receivedUserAgent;
    private volatile int responseStatus = 200;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/collect", exchange -> {
            receivedContentEncoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
            receivedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            receivedUserAgent = exchange.getRequestHeaders().getFirst("User-Agent");

            try (InputStream is = exchange.getRequestBody();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    bos.write(buffer, 0, read);
                }
                receivedBody = bos.toByteArray();
            }

            if (responseStatus >= 200 && responseStatus < 300) {
                exchange.sendResponseHeaders(responseStatus, 0);
            } else {
                byte[] responseBytes = "Error Details Here".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(responseStatus, responseBytes.length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testSuccessfulExecution() throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/collect");
        HttpClientSubmitter executor = new HttpClientSubmitter(uri, "my-secret-token");

        String jsonPayload = "{\"test\":true,\"value\":123}";
        executor.execute(jsonPayload);

        assertEquals("gzip", receivedContentEncoding);
        assertEquals("application/octet-stream", receivedContentType);
        assertEquals("Bearer my-secret-token", receivedAuthorization);
        assertNotNull(receivedUserAgent);

        assertNotNull(receivedBody);

        // Decompress the received body
        String decompressed;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(receivedBody))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            decompressed = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        assertEquals(jsonPayload, decompressed);
    }

    @Test
    public void testFailureExecution() {
        responseStatus = 500;
        Exception exception = assertThrows(Exception.class, () -> {
            URI uri = URI.create("http://localhost:" + port + "/collect");
            HttpClientSubmitter executor = new HttpClientSubmitter(uri, "token");
            executor.execute("{}");
        });
        assertTrue(exception.getMessage().contains("500"));
        assertTrue(exception.getMessage().contains("Error Details Here"));
    }
}
