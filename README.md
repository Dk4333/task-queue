# Concurrent Task Queue

Simple Spring Boot app implementing a fixed-size worker pool with FIFO queueing.

Running

1. Build and run:

```bash
mvn spring-boot:run
```

Endpoints

- `POST /queueTask` — body: `{ "id":"task-1","task":"name","taskParams":{},"time":5 }` — returns after completion or stop with `{id,status}`.
- `POST /checkStatus` — body: `{ "id":"task-1" }` — returns `{id,status}`.
- `POST /stopTask` — body: `{ "id":"task-1" }` — returns `{id,STOPPED}`.

Configuration

- Worker pool size: `src/main/resources/application.properties` `worker.count` (default 3)

Design notes

- Uses a `ThreadPoolExecutor` with a `LinkedBlockingQueue` to preserve FIFO ordering.
- Tasks are tracked in a `ConcurrentHashMap` and expose a `CountDownLatch` so the original HTTP request waits for completion without executing the task on the request thread.
- Stopping a queued task removes it from the executor queue; stopping a running task cancels the Future (interrupts).
