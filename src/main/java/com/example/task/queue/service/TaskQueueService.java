package com.example.task.queue.service;

import com.example.task.queue.model.TaskRecord;
import com.example.task.queue.model.TaskRequest;
import com.example.task.queue.model.TaskStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

@Service
public class TaskQueueService {

    @Value("${worker.count:3}")
    private int workerCount;

    private ThreadPoolExecutor executor;
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        executor = new ThreadPoolExecutor(
                workerCount, workerCount,
                0L, TimeUnit.MILLISECONDS,
                queue,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) executor.shutdownNow();
    }

    public synchronized boolean submitTask(TaskRequest req) {
        Objects.requireNonNull(req.getId());
        if (tasks.containsKey(req.getId())) return false;

        TaskRecord record = new TaskRecord(req.getId(), req.getTime());
        tasks.put(req.getId(), record);

        TaskRunnable runnable = new TaskRunnable(record);
        Future<?> f = executor.submit(runnable);
        record.setFuture(f);
        return true;
    }

    public TaskStatus checkStatus(String id) {
        TaskRecord r = tasks.get(id);
        if (r == null) return TaskStatus.DONE;
        return r.getStatus();
    }

    public boolean stopTask(String id) {
        TaskRecord r = tasks.get(id);
        if (r == null) return false;

        // Attempt to remove from queue if queued
        if (r.getStatus() == TaskStatus.QUEUED) {
            // Find runnable in executor queue and remove
            boolean removed = executor.getQueue().removeIf(obj -> {
                if (obj instanceof FutureTask) {
                    // our submit produced a FutureTask wrapping the Runnable
                    try {
                        java.lang.reflect.Field callableField = FutureTask.class.getDeclaredField("callable");
                        callableField.setAccessible(true);
                        Object callable = callableField.get(obj);
                        if (callable != null && callable.toString().contains(r.getId())) return true;
                    } catch (Throwable ex) {
                        return false;
                    }
                }
                return false;
            });
            r.setStatus(TaskStatus.STOPPED);
            r.getDoneLatch().countDown();
            tasks.remove(id);
            return true;
        }

        if (r.getStatus() == TaskStatus.RUNNING) {
            Future<?> f = r.getFuture();
            if (f != null) {
                f.cancel(true);
            }
            r.setStatus(TaskStatus.STOPPED);
            r.getDoneLatch().countDown();
            tasks.remove(id);
            return true;
        }

        return false;
    }

    public TaskRecord awaitCompletion(String id) throws InterruptedException {
        TaskRecord r = tasks.get(id);
        if (r == null) return null;
        r.getDoneLatch().await();
        return r;
    }

    private class TaskRunnable implements Runnable {
        private final TaskRecord record;

        TaskRunnable(TaskRecord record) {
            this.record = record;
        }

        @Override
        public String toString() {
            return "TaskRunnable:" + record.getId();
        }

        @Override
        public void run() {
            // If already stopped while queued, skip
            if (record.getStatus() == TaskStatus.STOPPED) {
                record.getDoneLatch().countDown();
                tasks.remove(record.getId());
                return;
            }

            record.setStatus(TaskStatus.RUNNING);
            try {
                int secs = record.getTimeSeconds();
                for (int i = 0; i < secs; i++) {
                    TimeUnit.SECONDS.sleep(1);
                }
                record.setStatus(TaskStatus.DONE);
            } catch (InterruptedException e) {
                record.setStatus(TaskStatus.STOPPED);
                Thread.currentThread().interrupt();
            } finally {
                record.getDoneLatch().countDown();
                tasks.remove(record.getId());
            }
        }
    }
}
