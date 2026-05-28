package io.github.projectunified.faststats.core;

/**
 * Interface to execute HTTP POST requests sending the JSON telemetry body to the server.
 */
public interface HttpExecutor {
    /**
     * Executes the HTTP request carrying the serialized JSON payload.
     *
     * @param json the JSON payload to transmit
     * @throws Exception if transmission fails
     */
    void execute(String json) throws Exception;
}
