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

    @Test
    public void testDeserialize() throws Exception {
        GsonSerializer serializer = new GsonSerializer();

        String json = "{\"name\":\"test\",\"value\":123.0,\"flag\":true}";
        Map<String, Object> map = serializer.deserialize(json);

        assertEquals("test", map.get("name"));
        assertEquals(123.0, ((Number) map.get("value")).doubleValue());
        assertEquals(true, map.get("flag"));
    }
}
