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
     * Executes the HTTP request carrying the serialized JSON payload.
     *
     * @param path the target path (e.g. /v1/collect) or full URL
     * @param json the JSON payload to transmit
     * @throws Exception if transmission fails
     */
    void execute(String path, String json) throws Exception;
}
