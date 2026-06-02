package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.Submitter;

class CapturingSubmitter implements Submitter {
    String capturedPath;
    String capturedJson;
    int callCount = 0;

    @Override
    public Response execute(String path, String json, boolean compressed) {
        this.capturedPath = path;
        this.capturedJson = json;
        this.callCount++;
        return Response.create(200, null, null);
    }
}
