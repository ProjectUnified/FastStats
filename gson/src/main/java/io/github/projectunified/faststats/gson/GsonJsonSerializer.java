package io.github.projectunified.faststats.gson;

import com.google.gson.Gson;
import io.github.projectunified.faststats.core.JsonSerializer;

import java.util.Map;

/**
 * An implementation of {@link JsonSerializer} that uses Google Gson.
 */
public class GsonJsonSerializer implements JsonSerializer {
    private final Gson gson;

    /**
     * Constructs a new {@link GsonJsonSerializer} with a default {@link Gson} instance.
     */
    public GsonJsonSerializer() {
        this(new Gson());
    }

    /**
     * Constructs a new {@link GsonJsonSerializer} with the specified {@link Gson} instance.
     *
     * @param gson the Gson instance to use
     */
    public GsonJsonSerializer(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String serialize(Map<String, Object> value) throws Exception {
        return gson.toJson(value);
    }
}
