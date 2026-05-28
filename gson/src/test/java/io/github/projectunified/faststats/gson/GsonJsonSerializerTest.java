package io.github.projectunified.faststats.gson;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GsonJsonSerializerTest {

    @Test
    public void testSerialize() throws Exception {
        GsonJsonSerializer serializer = new GsonJsonSerializer();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "test");
        map.put("value", 123);

        String json = serializer.serialize(map);
        assertEquals("{\"name\":\"test\",\"value\":123}", json);
    }
}
