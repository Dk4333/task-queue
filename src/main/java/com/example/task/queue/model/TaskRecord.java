package com.example.task.queue.model;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class TaskRecord {
    private final String id;
    private final int timeSeconds;
    private final CountDownLatch doneLatch = new CountDownLatch(1);
    private final AtomicReference<TaskStatus> status = new AtomicReference<>(TaskStatus.QUEUED);
    private volatile Future<?> future;

    public TaskRecord(String id, int timeSeconds) {
        this.id = id;
        this.timeSeconds = timeSeconds;
    }

    public String getId() { return id; }
    public int getTimeSeconds() { return timeSeconds; }
    public TaskStatus getStatus() { return status.get(); }
    public void setStatus(TaskStatus s) { status.set(s); }
    public CountDownLatch getDoneLatch() { return doneLatch; }
    public void setFuture(Future<?> f) { this.future = f; }
    public Future<?> getFuture() { return future; }
}
