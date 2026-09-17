package io.github.projectunified.faststats.core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CapturingSubmitter implements Submitter {
    public final List<String> capturedPaths = new ArrayList<>();
    public final List<String> capturedJsons = new ArrayList<>();
    public String capturedPath;
    public String capturedJson;
    public int callCount = 0;
    public String response;
    public int statusCode = 200;
    public Exception exception;

    @Override
    public Response execute(String path, String json, boolean compressed) throws Exception {
        this.capturedPath = path;
        this.capturedJson = json;
        this.capturedPaths.add(path);
        this.capturedJsons.add(json);
        this.callCount++;
        byte[] bytes = response != null ? response.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return Response.create(statusCode, () -> new ByteArrayInputStream(bytes), exception);
    }
}
