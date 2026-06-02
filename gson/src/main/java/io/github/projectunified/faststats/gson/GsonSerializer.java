package io.github.projectunified.faststats.gson;

import com.google.gson.Gson;
import io.github.projectunified.faststats.core.Serializer;

import java.util.Map;

/**
 * An implementation of {@link Serializer} that uses Google Gson.
 */
public class GsonSerializer implements Serializer {
    private final Gson gson;

    /**
     * Constructs a new {@link GsonSerializer} with a default {@link Gson} instance.
     */
    public GsonSerializer() {
        this(new Gson());
    }

    /**
     * Constructs a new {@link GsonSerializer} with the specified {@link Gson} instance.
     *
     * @param gson the Gson instance to use
     */
    public GsonSerializer(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String serialize(Map<String, Object> value) throws Exception {
        return gson.toJson(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> deserialize(String json) throws Exception {
        return gson.fromJson(json, Map.class);
    }
}
