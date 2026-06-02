package io.github.projectunified.faststats.core;

import java.io.ByteArrayInputStream;

public class CapturingSubmitter implements Submitter {
    public String capturedPath;
    public String capturedJson;
    public int callCount = 0;
    public String response;


    @Override
    public Response execute(String path, String json, boolean compressed) throws Exception {
        this.capturedPath = path;
        this.capturedJson = json;
        this.callCount++;
        byte[] bytes = response != null ? response.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        return Response.create(200, () -> new ByteArrayInputStream(bytes), null);
    }
}
