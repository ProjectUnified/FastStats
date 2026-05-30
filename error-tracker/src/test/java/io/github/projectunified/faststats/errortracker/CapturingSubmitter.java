package io.github.projectunified.faststats.errortracker;

import io.github.projectunified.faststats.core.Submitter;

class CapturingSubmitter implements Submitter {
    String capturedJson;
    int callCount = 0;

    @Override
    public void execute(String json) {
        this.capturedJson = json;
        this.callCount++;
    }
}
