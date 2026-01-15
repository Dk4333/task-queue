package com.example.task.queue.model;

import java.util.Map;

public class TaskRequest {
    private String id;
    private String task;
    private Map<String, Object> taskParams;
    private int time;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }
    public Map<String, Object> getTaskParams() { return taskParams; }
    public void setTaskParams(Map<String, Object> taskParams) { this.taskParams = taskParams; }
    public int getTime() { return time; }
    public void setTime(int time) { this.time = time; }
}
