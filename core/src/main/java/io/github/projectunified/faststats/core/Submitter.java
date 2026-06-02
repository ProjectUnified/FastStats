package io.github.projectunified.faststats.core;

/**
 * Interface to execute HTTP POST requests sending the JSON telemetry body to the server.
 */
public interface Submitter {
    /**
     * The default base URL for metrics collection.
     */
    String DEFAULT_BASE_URL = "https://metrics.faststats.dev";

    /**
     * Executes the HTTP request and returns the response body.
     *
     * @param path       the target path or URL
     * @param json       the JSON payload
     * @param compressed whether to compress the payload using GZIP
     * @return the response body as a String
     * @throws Exception if transmission fails
     */
    String execute(String path, String json, boolean compressed) throws Exception;
}
