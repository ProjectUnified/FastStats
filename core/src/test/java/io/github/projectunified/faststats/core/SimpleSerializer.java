package io.github.projectunified.faststats.core;

import java.util.Map;

public class SimpleSerializer implements Serializer {
    @Override
    public String serialize(Map<String, Object> value) throws Exception {
        return String.valueOf(value);
    }

    @Override
    public Map<String, Object> deserialize(String str) throws Exception {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (str.startsWith("{") && str.endsWith("}")) {
            str = str.substring(1, str.length() - 1);
            if (str.trim().isEmpty()) {
                return map;
            }
            String[] parts = str.split(", ");
            for (String part : parts) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    String val = kv[1];
                    if ("true".equals(val)) {
                        map.put(kv[0], true);
                    } else if ("false".equals(val)) {
                        map.put(kv[0], false);
                    } else {
                        try {
                            map.put(kv[0], Double.valueOf(val));
                        } catch (NumberFormatException e) {
                            map.put(kv[0], val);
                        }
                    }
                }
            }
        }
        return map;
    }
}
