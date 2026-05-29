package io.github.projectunified.faststats.net;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class NetSubmitterTest {

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

            exchange.sendResponseHeaders(responseStatus, 0);
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
        URL url = new URL("http://localhost:" + port + "/collect");
        NetSubmitter executor = new NetSubmitter(url, "my-secret-token", "MyAgent/1.0");

        String jsonPayload = "{\"test\":true,\"value\":123}";
        executor.execute(jsonPayload);

        assertEquals("gzip", receivedContentEncoding);
        assertEquals("application/octet-stream", receivedContentType);
        assertEquals("Bearer my-secret-token", receivedAuthorization);
        assertEquals("MyAgent/1.0", receivedUserAgent);

        assertNotNull(receivedBody);

        // Decompress the received body
        String decompressed;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPInputStream gis = new GZIPInputStream(new java.io.ByteArrayInputStream(receivedBody))) {
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
        assertThrows(Exception.class, () -> {
            URL url = new URL("http://localhost:" + port + "/collect");
            NetSubmitter executor = new NetSubmitter(url, "token", "Agent");
            executor.execute("{}");
        });
    }
}
