package io.github.projectunified.faststats.core;

public class CapturingSubmitter implements Submitter {
    public String capturedPath;
    public String capturedJson;
    public int callCount = 0;
    public String response;


    @Override
    public String execute(String path, String json, boolean compressed) throws Exception {
        this.capturedPath = path;
        this.capturedJson = json;
        this.callCount++;
        return response;
    }
}
