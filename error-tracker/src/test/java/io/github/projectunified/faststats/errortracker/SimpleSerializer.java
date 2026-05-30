package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.Serializer;

import java.util.Map;

class SimpleSerializer implements Serializer {
    @Override
    public String serialize(Map<String, Object> obj) {
        return obj.toString();
    }
}
