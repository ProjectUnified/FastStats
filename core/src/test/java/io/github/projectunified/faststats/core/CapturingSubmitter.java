package io.github.projectunified.faststats.core;

public class CapturingSubmitter implements Submitter {
    String capturedJson;
    int callCount = 0;

    @Override
    public void execute(String json) throws Exception {
        this.capturedJson = json;
        this.callCount++;
    }
}
