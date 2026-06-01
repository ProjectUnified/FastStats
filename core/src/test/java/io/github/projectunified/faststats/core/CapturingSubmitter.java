package io.github.projectunified.faststats.core;

public class CapturingSubmitter implements Submitter {
    String capturedPath;
    String capturedJson;
    int callCount = 0;

    @Override
    public void execute(String path, String json) throws Exception {
        this.capturedPath = path;
        this.capturedJson = json;
        this.callCount++;
    }
}
