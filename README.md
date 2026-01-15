

A Spring Boot application implementing a **fixed-size worker thread pool** with **FIFO task queueing**, designed to handle concurrent task submission, execution, and cancellation.



## Tech Stack

- Java 17
- Spring Boot 3.x
- Concurrency utilities: `ThreadPoolExecutor`, `CountDownLatch`, `ConcurrentHashMap`, `AtomicReference`

## Getting Started

* Prerequisites
- Java 17
- Maven 3.6+

    # Build

```bash
./mvnw clean package -DskipTests
```

Or with system Maven:
```bash
mvn clean package -DskipTests
```

### Run

**Using Maven plugin:**
```bash
./mvnw spring-boot:run
```




Default port: 8080

### API Endpoints

** 1. Queue Task (Blocking)
Submits a task and blocks the request until the task completes or is stopped.

```
POST /queueTask
Content-Type: application/json

Request:
{
  "id": "task-123",
  "task": "example-task",
  "taskParams": {
    "key": "value"
  },
  "time": 5
}

Response (200 OK):
{
  "id": "task-123",
  "status": "DONE"
}
or
{
  "id": "task-123",
  "status": "STOPPED"
}
```

Rules:
- Task `id` must be unique; duplicate ids are rejected (400).
- `time` is execution duration in seconds.
- HTTP request blocks until task finishes (state = DONE) or is stopped (state = STOPPED).
- Request thread is not consumed by task execution.

### 2. Check Status (Non-blocking)
Polls the current state of a task without waiting.

```
POST /checkStatus
Content-Type: application/json

Request:
{
  "id": "task-123"
}

Response (200 OK):
{
  "id": "task-123",
  "status": "QUEUED"
}
or "RUNNING", "DONE"
```

**Notes:**
- Returns immediately.
- If task id is unknown or already completed, status is `DONE`.

*** 3. Stop Task (Non-blocking)
Cancels a queued or running task.

```
POST /stopTask
Content-Type: application/json

Request:
{
  "id": "task-123"
}

Response (200 OK):
{
  "id": "task-123",
  "status": "STOPPED"
}
```

**Behavior:**
- If QUEUED: removes from queue; original `/queueTask` returns `STOPPED`.
- If RUNNING: interrupts worker thread; original `/queueTask` returns `STOPPED`.
- If DONE or unknown: no-op.

** Task State Lifecycle

```
QUEUED → RUNNING → DONE
   ↓        ↓
STOPPED ← STOPPED
```

- **QUEUED**: Task accepted, waiting for a free worker.
- **RUNNING**: Worker thread is executing the task.
- **DONE**: Task completed successfully.
- **STOPPED**: Task was cancelled before or during execution.

### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Worker thread pool size (default 2)
worker.count=2
```

## Architecture

### Threading Model

```
HTTP Request Threads (Tomcat, non-blocking)
         ↓
   TaskController (REST layer)
         ↓
   TaskQueueService (core logic)
         ↓
   ThreadPoolExecutor (fixed workers)
   ├── Worker 1
   ├── Worker 2
         ↓
   Task execution (FIFO queue)
```

### Key Components

- **TaskRequest (DTO)**: Incoming JSON payload.
- **TaskStatus (Enum)**: QUEUED, RUNNING, DONE, STOPPED.
- **TaskRecord**: Task state holder with `AtomicReference<TaskStatus>` and `CountDownLatch` for synchronization.
- **TaskQueueService**: Core orchestrator managing the executor, task map, and state transitions.
- **TaskRunnable**: Worker job; sleeps for configured duration or handles interruption.
- **TaskController**: REST endpoints.

### Thread Safety

- Task map: `ConcurrentHashMap<String, TaskRecord>` (atomic operations).
- Task status: `AtomicReference<TaskStatus>` (lock-free state transitions).
- Task submission: `synchronized` method to prevent duplicate ids.
- FIFO ordering: `LinkedBlockingQueue` (built into executor).
- Synchronization: `CountDownLatch` for signaling task completion.

### Design Decisions

1. **Separation of concerns**: Task execution runs on worker threads; HTTP request threads wait via latch, not blocking workers.
2. **FIFO via LinkedBlockingQueue**: Preserves order, inherently thread-safe, part of standard executor.
3. **CountDownLatch**: Allows `/queueTask` HTTP request to block until done without consuming a worker thread.
4. **Executor.cancel(true)**: Interrupts running tasks via `Thread.interrupt()`; tasks handle `InterruptedException`.
5. **Configurable pool size**: Set via properties; supports scaling based on load.

## Usage Examples

### Example 1: Submit and wait for completion

```bash
curl -X POST http://localhost:8080/queueTask \
  -H "Content-Type: application/json" \
  -d '{
    "id": "job-1",
    "task": "process-data",
    "taskParams": {"input": "data.csv"},
    "time": 10
  }'
# Returns after ~10 seconds with { "id": "job-1", "status": "DONE" }
```

### Example 2: Check status without waiting

```bash
curl -X POST http://localhost:8080/checkStatus \
  -H "Content-Type: application/json" \
  -d '{ "id": "job-1" }'
# Immediate response: { "id": "job-1", "status": "RUNNING" }
```

### Example 3: Cancel a task

```bash
curl -X POST http://localhost:8080/stopTask \
  -H "Content-Type: application/json" \
  -d '{ "id": "job-1" }'
# Returns: { "id": "job-1", "status": "STOPPED" }
# The original /queueTask request (if still waiting) also returns STOPPED
```

## Testing with Postman

1. Import the endpoints into Postman or create requests manually.
2. Set `Content-Type: application/json` header.
3. Increase request timeout (Settings → General → Request timeout in ms) for long-running tasks.
4. Test scenarios:
   - Submit task with long duration, then stop it midway.
   - Submit multiple tasks to test FIFO ordering and worker pool limits.
   - Call `/checkStatus` while task is running.

## Assumptions and Trade-offs

### Assumptions
- Task execution is CPU-bound (simulated by sleep); I/O-bound tasks may need thread pool tuning.
- Single instance deployment (no distributed task queue).
- Task state is ephemeral; tasks are removed from map after completion.

### Trade-offs
- **Queue removal by reflection**: Uses reflection to identify and remove queued tasks by id. Fragile but avoids custom queue wrapper.
- **No persistence**: Tasks lost on shutdown; no audit trail.
- **No task retry**: Failed (interrupted) tasks are not automatically retried.
- **Simple validation**: Minimal input validation; consider adding `@Valid` annotations for production.


## Project Structure

```
src/main/java/com/example/task/queue/
├── TaskQueueApplication.java         # Spring Boot entry point
├── controller/
│   └── TaskController.java           # REST endpoints
├── service/
│   └── TaskQueueService.java         # Core logic
└── model/
    ├── TaskRequest.java              # Request DTO
    ├── TaskRecord.java               # Internal state holder
    └── TaskStatus.java               # Enum for task states

src/main/resources/
└── application.properties             # Configuration
```

## License

MIT
EOF

