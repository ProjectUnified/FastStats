package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.Submitter;

class CapturingSubmitter implements Submitter {
    String capturedPath;
    String capturedJson;
    int callCount = 0;

    @Override
    public void execute(String path, String json) {
        this.capturedPath = path;
        this.capturedJson = json;
        this.callCount++;
    }
}
