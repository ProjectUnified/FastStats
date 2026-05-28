package io.github.projectunified.faststats.core;

import java.util.Map;

public class SimpleSerializer implements JsonSerializer {
    @Override
    public String serialize(Map<String, Object> value) throws Exception {
        return String.valueOf(value);
    }
}
