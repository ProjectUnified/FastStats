package io.github.projectunified.faststats.core;

import java.util.Map;

/**
 * Interface for serializing raw Java telemetry structures into standard JSON strings.
 */
public interface Serializer {
    /**
     * Serializes the given telemetry map into its JSON representation.
     *
     * @param value the telemetry map to serialize
     * @return the serialized JSON string
     * @throws Exception if serialization fails
     */
    String serialize(Map<String, Object> value) throws Exception;
}
