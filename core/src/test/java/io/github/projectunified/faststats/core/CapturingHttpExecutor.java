package io.github.projectunified.faststats.core;

public class CapturingHttpExecutor implements HttpExecutor {
    String capturedJson;
    int callCount = 0;

    @Override
    public void execute(String json) throws Exception {
        this.capturedJson = json;
        this.callCount++;
    }
}
