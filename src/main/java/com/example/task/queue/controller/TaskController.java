package com.example.task.queue.controller;

import com.example.task.queue.model.TaskRequest;
import com.example.task.queue.model.TaskRecord;
import com.example.task.queue.model.TaskStatus;
import com.example.task.queue.service.TaskQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TaskController {

    private final TaskQueueService service;

    public TaskController(TaskQueueService service) {
        this.service = service;
    }

    @PostMapping(path = "/queueTask", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, String>> queueTask(@RequestBody TaskRequest req) throws InterruptedException {
        if (req.getId() == null || req.getId().isBlank()) return ResponseEntity.badRequest().build();

        boolean ok = service.submitTask(req);
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "duplicate id"));

        TaskRecord r = service.awaitCompletion(req.getId());
        String status = (r == null) ? TaskStatus.DONE.name() : r.getStatus().name();
        Map<String, String> resp = new HashMap<>();
        resp.put("id", req.getId());
        resp.put("status", status);
        return ResponseEntity.ok(resp);
    }

    @PostMapping(path = "/checkStatus", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, String>> checkStatus(@RequestBody Map<String, String> body) {
        String id = body.get("id");
        if (id == null || id.isBlank()) return ResponseEntity.badRequest().build();
        TaskStatus s = service.checkStatus(id);
        Map<String, String> resp = Map.of("id", id, "status", s.name());
        return ResponseEntity.ok(resp);
    }

    @PostMapping(path = "/stopTask", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, String>> stopTask(@RequestBody Map<String, String> body) {
        String id = body.get("id");
        if (id == null || id.isBlank()) return ResponseEntity.badRequest().build();
        service.stopTask(id);
        return ResponseEntity.ok(Map.of("id", id, "status", TaskStatus.STOPPED.name()));
    }
}
