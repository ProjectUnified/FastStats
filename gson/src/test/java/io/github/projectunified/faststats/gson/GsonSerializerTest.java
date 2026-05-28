package io.github.projectunified.faststats.gson;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GsonSerializerTest {

    @Test
    public void testSerialize() throws Exception {
        GsonSerializer serializer = new GsonSerializer();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("value", 123);

        String json = serializer.serialize(map);
        assertEquals("{\"name\":\"test\",\"value\":123}", json);
    }
}
