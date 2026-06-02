package io.github.projectunified.faststats.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

        /**
         * Creates a new {@link Response} instance.
         *
         * @param statusCode  the status code
         * @param supplier    the input stream supplier (can be null)
         * @param exception   the exception (can be null)
         * @return the response instance
         */
        static Response create(int statusCode, InputStreamSupplier supplier, Exception exception) {
            return new Response() {
                private InputStream in;
                private boolean retrieved;

                @Override
                public int getStatusCode() {
                    return statusCode;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    if (!retrieved) {
                        if (supplier != null) {
                            in = supplier.get();
                        }
                        retrieved = true;
                    }
                    return in != null ? in : new java.io.ByteArrayInputStream(new byte[0]);
                }

                @Override
                public Optional<Exception> getException() {
                    return Optional.ofNullable(exception);
                }
            };
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
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    return sb.toString().trim();
                }
            }
        }
    }
}
