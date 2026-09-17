package io.github.projectunified.faststats.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Interface to execute HTTP POST requests sending the JSON telemetry body to the server.
 */
public interface Submitter {
    /**
     * The default base URL for metrics collection.
     */
    String DEFAULT_BASE_URL = "https://metrics.faststats.dev";

    /**
     * Executes the HTTP request and returns the response context.
     *
     * @param path       the target path or URL
     * @param json       the JSON payload
     * @param compressed whether to compress the payload using GZIP
     * @return the response context
     * @throws Exception if a non-recoverable error occurs
     */
    Response execute(String path, String json, boolean compressed) throws Exception;

    /**
     * Represents the response received from executing a request.
     */
    interface Response {
        /**
         * Creates a new {@link Response} instance.
         * <p>
         * The body of the created response is cached on the first read, so both
         * {@link #readString()} and {@link #getInputStream()} may be called multiple
         * times without losing data.
         *
         * @param statusCode the status code
         * @param supplier   the input stream supplier (can be null)
         * @param exception  the exception (can be null)
         * @return the response instance
         */
        static Response create(int statusCode, InputStreamSupplier supplier, Exception exception) {
            return new Response() {
                private InputStream in;
                private boolean retrieved;
                private byte[] body;

                @Override
                public int getStatusCode() {
                    return statusCode;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (body != null) {
                        return new ByteArrayInputStream(body);
                    }
                    if (!retrieved) {
                        if (supplier != null) {
                            in = supplier.get();
                        }
                        retrieved = true;
                    }
                    return in != null ? in : new ByteArrayInputStream(new byte[0]);
                }

                @Override
                public Optional<Exception> getException() {
                    return Optional.ofNullable(exception);
                }

                @Override
                public String readString() throws Exception {
                    if (body == null) {
                        try (InputStream stream = getInputStream()) {
                            body = readAll(stream);
                        }
                    }
                    return new String(body, StandardCharsets.UTF_8).trim();
                }
            };
        }

        /**
         * Reads the given stream completely.
         *
         * @param stream the stream to read
         * @return the read bytes
         * @throws IOException if an I/O error occurs
         */
        static byte[] readAll(InputStream stream) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }

        /**
         * Gets the HTTP response status code, or 0 / -1 if a client-side exception occurred.
         *
         * @return the status code
         */
        int getStatusCode();

        /**
         * Gets the response body input stream.
         *
         * @return the input stream
         * @throws IOException if an I/O error occurs
         */
        InputStream getInputStream() throws IOException;

        /**
         * Gets the exception that occurred during execution, if any.
         *
         * @return the exception, or empty if successful
         */
        Optional<Exception> getException();

        /**
         * Checks if the request was accepted by the server.
         *
         * @return true if the status code is in the 2xx range, false otherwise
         */
        default boolean isSuccessful() {
            int statusCode = getStatusCode();
            return statusCode >= 200 && statusCode < 300;
        }

        /**
         * Reads the entire response body stream as a UTF-8 String.
         *
         * @return the response body as a String
         * @throws Exception if an I/O or conversion error occurs
         */
        default String readString() throws Exception {
            try (InputStream stream = getInputStream()) {
                if (stream == null) {
                    return "";
                }
                return new String(readAll(stream), StandardCharsets.UTF_8).trim();
            }
        }

        /**
         * A supplier for {@link InputStream} that can throw an {@link IOException}.
         */
        @FunctionalInterface
        interface InputStreamSupplier {
            /**
             * Gets the input stream.
             *
             * @return the input stream
             * @throws IOException if an I/O error occurs
             */
            InputStream get() throws IOException;
        }
    }
}
