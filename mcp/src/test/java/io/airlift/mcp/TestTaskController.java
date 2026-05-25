package io.airlift.mcp;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpTaskController.SetStatus;
import io.airlift.mcp.internal.InternalTaskController;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteTaskResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.InitializeRequest.Elicitation;
import io.airlift.mcp.model.InitializeRequest.Sampling;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputRequiredTaskResult;
import io.airlift.mcp.model.InputResponses;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.Result;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskHandlerResult.TaskFailed;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.storage.MemoryStorageController;
import io.airlift.mcp.storage.StorageGroupId;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.mcp.McpModule.buildJsonSubType;
import static io.airlift.mcp.McpTaskController.ErrorState.CANCELED;
import static io.airlift.mcp.McpTaskController.ErrorState.CANCELLATION_REQUESTED;
import static io.airlift.mcp.McpTaskController.ErrorState.FAILED;
import static io.airlift.mcp.McpTaskController.ErrorState.NONE;
import static io.airlift.mcp.McpTaskController.SetStatus.TASK_COMPLETED;
import static io.airlift.mcp.McpTaskController.SetStatus.TASK_NOT_FOUND;
import static io.airlift.mcp.model.Constants.METADATA_TASKS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static java.time.Duration.ZERO;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestTaskController
{
    private final InternalTaskController controller = createController();

    private static final McpRequestContext REQUEST_CONTEXT = new MockRequestContext(
            new Authenticated<>(UUID.randomUUID()),
            new ClientCapabilities(Optional.of(new ListChanged(true)), Optional.of(new Sampling()), Optional.of(new Elicitation()), Optional.of(ImmutableMap.of(METADATA_TASKS, new Object())), Optional.empty()));

    private static InternalTaskController createController()
    {
        McpConfig config = new McpConfig()
                .setTaskTtl(new Duration(15, MINUTES))
                .setTaskPollInterval(new Duration(15, SECONDS));
        return new InternalTaskController(new MemoryStorageController(), config, buildJsonMapper());
    }

    private static InternalTaskController createShortTtlController()
    {
        return createShortTtlController(new MemoryStorageController(ZERO));
    }

    private static InternalTaskController createShortTtlController(MemoryStorageController storageController)
    {
        McpConfig config = new McpConfig()
                .setTaskTtl(new Duration(100, MILLISECONDS))
                .setTaskPollInterval(new Duration(10, MILLISECONDS));
        return new InternalTaskController(storageController, config, buildJsonMapper());
    }

    @Test
    public void testCreateTask()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        assertThat(task.taskId()).isNotNull();
        assertThat(task.status()).isEqualTo(TaskStatus.WORKING);
        assertThat(task.statusMessage()).isEmpty();
        assertThat(task.createdAt()).isNotNull();
        assertThat(task.lastUpdatedAt()).isNotNull();
    }

    @Test
    public void testCreateTaskProducesUniqueIds()
    {
        Task task1 = controller.createTask(REQUEST_CONTEXT);
        Task task2 = controller.createTask(REQUEST_CONTEXT);
        assertThat(task1.taskId()).isNotEqualTo(task2.taskId());
    }

    @Test
    public void testGetTask()
    {
        Task created = controller.createTask(REQUEST_CONTEXT);
        Optional<Task> retrieved = controller.getTask(created.taskId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.orElseThrow().taskId()).isEqualTo(created.taskId());
        assertThat(retrieved.orElseThrow().status()).isEqualTo(TaskStatus.WORKING);
    }

    @Test
    public void testGetTaskNotFound()
    {
        Optional<Task> result = controller.getTask(UUID.randomUUID().toString());
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetTaskInvalidId()
    {
        assertThatThrownBy(() -> controller.getTask("not-a-uuid"))
                .isInstanceOf(McpException.class);
    }

    @Test
    public void testSetResultCompletes()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        CallToolResult toolResult = new CallToolResult(new TextContent("done", Optional.empty()));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.of("completed"));

        Task updated = controller.getTask(task.taskId()).orElseThrow();
        assertThat(updated.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(updated.statusMessage()).hasValue("completed");
    }

    @Test
    public void testSetResultWithInputRequests()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        CallToolResult toolResult = new CallToolResult(new TextContent("need input", Optional.empty()))
                .withInputRequests(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(ImmutableMap.of("question", new InputRequest("prompt", ImmutableMap.of("text", "answer?")))));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());

        Task updated = controller.getTask(task.taskId()).orElseThrow();
        assertThat(updated.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
    }

    @Test
    public void testSetResultClearsResultReturnsToWorking()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        CallToolResult toolResult = new CallToolResult(new TextContent("partial", Optional.empty()))
                .withInputRequests(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(ImmutableMap.of("q", new InputRequest("prompt", ImmutableMap.of()))));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.INPUT_REQUIRED);

        controller.setResult(task.taskId(), Optional.empty(), Optional.empty());
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.WORKING);
    }

    @Test
    public void testSetResultOnCompletedTask()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        CallToolResult toolResult = new CallToolResult(new TextContent("done", Optional.empty()));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.COMPLETED);

        SetStatus status = controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());
        assertThat(status).isEqualTo(TASK_COMPLETED);
    }

    @Test
    public void testSetResultOnNonexistentTask()
    {
        CallToolResult toolResult = new CallToolResult(new TextContent("done", Optional.empty()));
        SetStatus status = controller.setResult(UUID.randomUUID().toString(), Optional.of(toolResult), Optional.empty());
        assertThat(status).isEqualTo(TASK_NOT_FOUND);
    }

    @Test
    public void testSetErrorStateFailed()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        controller.setErrorState(task.taskId(), FAILED, Optional.empty());

        Task updated = controller.getTask(task.taskId()).orElseThrow();
        assertThat(updated.status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    public void testSetErrorStateCanceled()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        controller.setErrorState(task.taskId(), CANCELED, Optional.empty());

        Task updated = controller.getTask(task.taskId()).orElseThrow();
        assertThat(updated.status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    public void testSetErrorStateNoneRejected()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        assertThatThrownBy(() -> controller.setErrorState(task.taskId(), NONE, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be set to NONE");
    }

    @Test
    public void testSetErrorStateOnAlreadyFailedTask()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        controller.setErrorState(task.taskId(), FAILED, Optional.empty());

        SetStatus status = controller.setErrorState(task.taskId(), CANCELED, Optional.empty());
        assertThat(status).isEqualTo(TASK_COMPLETED);
    }

    @Test
    public void testSetErrorStateOnCompletedTask()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        controller.setResult(task.taskId(), Optional.of(new CallToolResult(new TextContent("done", Optional.empty()))), Optional.empty());

        SetStatus status = controller.setErrorState(task.taskId(), FAILED, Optional.empty());
        assertThat(status).isEqualTo(TASK_COMPLETED);
    }

    @Test
    public void testSetErrorStateOnNonexistentTask()
    {
        SetStatus status = controller.setErrorState(UUID.randomUUID().toString(), FAILED, Optional.empty());
        assertThat(status).isEqualTo(TASK_NOT_FOUND);
    }

    @Test
    public void testSetAndGetInputResponses()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        Map<String, Object> responses = ImmutableMap.of("answer", "42");
        controller.setTaskInputResponses(task.taskId(), Optional.of(responses));

        InputResponses retrieved = controller.currentInputResponses(task.taskId());
        assertThat(retrieved.inputResponses().orElseThrow()).containsEntry("answer", "42");
    }

    @Test
    public void testInputResponsesOnNewTask()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        assertThat(controller.currentInputResponses(task.taskId()).inputResponses()).isEmpty();
    }

    @Test
    public void testInputResponsesOnNonexistentTask()
    {
        assertThat(controller.currentInputResponses(UUID.randomUUID().toString()).inputResponses()).isEmpty();
    }

    @Test
    public void testSetInputResponsesOnCompletedTask()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        controller.setResult(task.taskId(), Optional.of(new CallToolResult(new TextContent("done", Optional.empty()))), Optional.empty());

        SetStatus status = controller.setTaskInputResponses(task.taskId(), Optional.of(ImmutableMap.of("key", "value")));
        assertThat(status).isEqualTo(TASK_COMPLETED);
    }

    @Test
    public void testCurrentTaskResultWorking()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isInstanceOf(CompleteTaskResult.class);

        CompleteTaskResult taskResult = (CompleteTaskResult) result.orElseThrow();
        assertThat(taskResult.task().status()).isEqualTo(TaskStatus.WORKING);
        assertThat(taskResult.result()).isEmpty();
    }

    @Test
    public void testCurrentTaskResultCompleted()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        CallToolResult toolResult = new CallToolResult(new TextContent("done", Optional.empty()));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());

        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isInstanceOf(CompleteTaskResult.class);

        CompleteTaskResult completeResult = (CompleteTaskResult) result.orElseThrow();
        assertThat(completeResult.task().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completeResult.result()).isPresent();
    }

    @Test
    public void testCurrentTaskResultInputRequired()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        CallToolResult toolResult = new CallToolResult(new TextContent("need input", Optional.empty()))
                .withInputRequests(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(ImmutableMap.of("q", new InputRequest("prompt", ImmutableMap.of()))));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());

        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isInstanceOf(InputRequiredTaskResult.class);

        InputRequiredTaskResult inputResult = (InputRequiredTaskResult) result.orElseThrow();
        assertThat(inputResult.task().status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
        assertThat(inputResult.inputRequests()).isPresent();
    }

    @Test
    public void testCurrentTaskResultFailed()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        controller.setErrorState(task.taskId(), FAILED, Optional.empty());

        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isInstanceOf(CompleteTaskResult.class);

        CompleteTaskResult taskResult = (CompleteTaskResult) result.orElseThrow();
        assertThat(taskResult.task().status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    public void testCurrentTaskResultCancelled()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        controller.setErrorState(task.taskId(), CANCELED, Optional.empty());

        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isInstanceOf(CompleteTaskResult.class);

        CompleteTaskResult taskResult = (CompleteTaskResult) result.orElseThrow();
        assertThat(taskResult.task().status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    public void testCurrentTaskResultNotFound()
    {
        assertThat(controller.currentTaskResult(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    public void testWorkingToInputRequiredToCompleted()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.WORKING);

        // transition to INPUT_REQUIRED
        CallToolResult inputResult = new CallToolResult(new TextContent("need input", Optional.empty()))
                .withInputRequests(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(ImmutableMap.of("q", new InputRequest("prompt", ImmutableMap.of()))));
        controller.setResult(task.taskId(), Optional.of(inputResult), Optional.of("waiting for input"));
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.INPUT_REQUIRED);

        // provide input responses
        controller.setTaskInputResponses(task.taskId(), Optional.of(ImmutableMap.of("q", "answer")));
        assertThat(controller.currentInputResponses(task.taskId()).inputResponses()).isPresent();

        // complete the task
        CallToolResult finalResult = new CallToolResult(new TextContent("all done", Optional.empty()));
        controller.setResult(task.taskId(), Optional.of(finalResult), Optional.of("finished"));
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(controller.getTask(task.taskId()).orElseThrow().statusMessage()).hasValue("finished");
    }

    @Test
    public void testWorkingToFailed()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.WORKING);

        controller.setErrorState(task.taskId(), FAILED, Optional.of(new JsonRpcErrorDetail(INTERNAL_ERROR, "oops")));
        Task failed = controller.getTask(task.taskId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.statusMessage()).hasValue("oops");

        // further mutations are rejected
        assertThat(controller.setResult(task.taskId(), Optional.of(new CallToolResult(new TextContent("late", Optional.empty()))), Optional.empty()))
                .isEqualTo(TASK_COMPLETED);
    }

    @Test
    public void testTaskContainsTtlAndPollInterval()
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        assertThat(task.ttlMs()).hasValue(15 * 60 * 1000);
        assertThat(task.pollIntervalMs()).hasValue(15 * 1000);
    }

    @Test
    public void testShortTtlTaskContainsConfiguredValues()
    {
        InternalTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask(REQUEST_CONTEXT);
        assertThat(task.ttlMs()).hasValue(100);
        assertThat(task.pollIntervalMs()).hasValue(10);
    }

    @Test
    public void testTaskExpiresAfterTtl()
            throws InterruptedException
    {
        InternalTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask(REQUEST_CONTEXT);
        assertThat(shortTtlController.getTask(task.taskId())).isPresent();

        Thread.sleep(200);

        assertThat(shortTtlController.getTask(task.taskId())).isEmpty();
    }

    @Test
    public void testTaskAccessRefreshesTtl()
            throws InterruptedException
    {
        InternalTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask(REQUEST_CONTEXT);

        // access within TTL (100ms) to refresh lastUsage
        Thread.sleep(60);
        assertThat(shortTtlController.getTask(task.taskId())).isPresent();

        // access again within TTL from last access
        Thread.sleep(60);
        assertThat(shortTtlController.getTask(task.taskId())).isPresent();

        // wait well past TTL from last access
        Thread.sleep(200);
        assertThat(shortTtlController.getTask(task.taskId())).isEmpty();
    }

    @Test
    public void testExpiredTaskSetResultReturnsGone()
            throws InterruptedException
    {
        InternalTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask(REQUEST_CONTEXT);

        Thread.sleep(200);

        SetStatus status = shortTtlController.setResult(task.taskId(), Optional.empty(), Optional.empty());
        assertThat(status).isEqualTo(TASK_NOT_FOUND);
    }

    @Test
    public void testExpiredTaskSetErrorStateReturnsGone()
            throws InterruptedException
    {
        InternalTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask(REQUEST_CONTEXT);

        Thread.sleep(200);

        SetStatus status = shortTtlController.setErrorState(task.taskId(), FAILED, Optional.empty());
        assertThat(status).isEqualTo(TASK_NOT_FOUND);
    }

    @Test
    public void testExpiredTaskCurrentTaskResultReturnsEmpty()
            throws InterruptedException
    {
        InternalTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask(REQUEST_CONTEXT);

        Thread.sleep(200);

        assertThat(shortTtlController.currentTaskResult(task.taskId())).isEmpty();
    }

    @Test
    public void testExpiredTaskInputResponsesReturnsEmpty()
            throws InterruptedException
    {
        InternalTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask(REQUEST_CONTEXT);

        Thread.sleep(200);

        assertThat(shortTtlController.currentInputResponses(task.taskId()).inputResponses()).isEmpty();
    }

    @Test
    public void testExpiredTaskIsRemovedFromStorage()
            throws InterruptedException
    {
        // hold the storage controller so we can observe that the expired task's group is actually
        // removed (memory reclaimed), not merely reported as absent by the task controller
        MemoryStorageController storageController = new MemoryStorageController(ZERO);
        InternalTaskController shortTtlController = createShortTtlController(storageController);

        Task task = shortTtlController.createTask(REQUEST_CONTEXT);
        StorageGroupId groupId = new StorageGroupId(task.taskId());
        assertThat(storageController.listGroups(Optional.empty())).contains(groupId);

        // exceed the 100ms TTL, then a storage access triggers cleanup of expired groups
        Thread.sleep(200);

        assertThat(storageController.listGroups(Optional.empty())).doesNotContain(groupId);
    }

    @Test
    public void testActiveTaskIsRetainedWhileExpiredTaskIsCleanedUp()
            throws InterruptedException
    {
        MemoryStorageController storageController = new MemoryStorageController(ZERO);
        InternalTaskController shortTtlController = createShortTtlController(storageController);

        Task active = shortTtlController.createTask(REQUEST_CONTEXT);
        Task idle = shortTtlController.createTask(REQUEST_CONTEXT);
        StorageGroupId activeGroup = new StorageGroupId(active.taskId());
        StorageGroupId idleGroup = new StorageGroupId(idle.taskId());

        // keep the active task alive across the TTL window (each access both refreshes it and
        // triggers cleanup of expired groups); never touch the idle task
        for (int i = 0; i < 8; i++) {
            Thread.sleep(30);
            assertThat(shortTtlController.getTask(active.taskId())).isPresent();
        }

        List<StorageGroupId> groups = storageController.listGroups(Optional.empty());
        assertThat(groups).contains(activeGroup);
        assertThat(groups).doesNotContain(idleGroup);
    }

    @Test
    public void testAllExpiredTasksAreCleanedUp()
            throws InterruptedException
    {
        MemoryStorageController storageController = new MemoryStorageController(ZERO);
        InternalTaskController shortTtlController = createShortTtlController(storageController);

        List<StorageGroupId> groupIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            groupIds.add(new StorageGroupId(shortTtlController.createTask(REQUEST_CONTEXT).taskId()));
        }
        assertThat(storageController.listGroups(Optional.empty())).containsAll(groupIds);

        // exceed the TTL for all tasks, then cleanup should sweep every one of them
        Thread.sleep(200);

        assertThat(storageController.listGroups(Optional.empty())).isEmpty();
    }

    @Test
    public void testAwaitTimesOut()
            throws InterruptedException
    {
        Task task = controller.createTask(REQUEST_CONTEXT);
        boolean signaled = controller.await(task.taskId(), java.time.Duration.ofMillis(50));
        assertThat(signaled).isFalse();
    }

    @Test
    public void testAwaitSignaledBySetResult()
            throws Exception
    {
        Task task = controller.createTask(REQUEST_CONTEXT);

        CompletableFuture<Boolean> awaitResult = CompletableFuture.supplyAsync(() -> {
            try {
                return controller.await(task.taskId(), java.time.Duration.ofSeconds(5));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(50);
        controller.setResult(task.taskId(), Optional.empty(), Optional.of("done"));

        assertThat(awaitResult.get(5, SECONDS)).isTrue();
    }

    @Test
    public void testAwaitSignaledBySetErrorState()
            throws Exception
    {
        Task task = controller.createTask(REQUEST_CONTEXT);

        CompletableFuture<Boolean> awaitResult = CompletableFuture.supplyAsync(() -> {
            try {
                return controller.await(task.taskId(), java.time.Duration.ofSeconds(5));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(50);
        controller.setErrorState(task.taskId(), FAILED, Optional.empty());

        assertThat(awaitResult.get(5, SECONDS)).isTrue();
    }

    @Test
    public void testAwaitSignaledByInputResponses()
            throws Exception
    {
        Task task = controller.createTask(REQUEST_CONTEXT);

        CompletableFuture<Boolean> awaitResult = CompletableFuture.supplyAsync(() -> {
            try {
                return controller.await(task.taskId(), java.time.Duration.ofSeconds(5));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(50);
        controller.setTaskInputResponses(task.taskId(), Optional.of(ImmutableMap.of("key", "value")));

        assertThat(awaitResult.get(5, SECONDS)).isTrue();
    }

    @Test
    public void testExecuteCancelableCompletesTask()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            boolean started = executionController.executeCancelable(task.taskId(), () -> new CallToolResult(new TextContent("done", Optional.empty())));
            assertThat(started).isTrue();

            awaitStatus(executionController, task.taskId(), TaskStatus.COMPLETED);
            assertThat(resultText(executionController, task.taskId())).isEqualTo("done");
        }
    }

    @Test
    public void testExecuteCancelableHandlerReturnsFailure()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            JsonRpcErrorDetail errorDetail = new JsonRpcErrorDetail(INTERNAL_ERROR, "handler failed");
            executionController.executeCancelable(task.taskId(), () -> new TaskFailed(FAILED, Optional.of(errorDetail)));

            awaitStatus(executionController, task.taskId(), TaskStatus.FAILED);
            assertThat(executionController.getTask(task.taskId()).orElseThrow().statusMessage()).hasValue("handler failed");
        }
    }

    @Test
    public void testExecuteCancelableHandlerThrowsMcpException()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            executionController.executeCancelable(task.taskId(), () -> {
                throw McpException.exception(INTERNAL_ERROR, "explicit mcp failure");
            });

            awaitStatus(executionController, task.taskId(), TaskStatus.FAILED);
            assertThat(executionController.getTask(task.taskId()).orElseThrow().statusMessage()).hasValue("explicit mcp failure");
        }
    }

    @Test
    public void testExecuteCancelableHandlerThrowsRuntimeException()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            executionController.executeCancelable(task.taskId(), () -> {
                throw new RuntimeException("unexpected boom");
            });

            awaitStatus(executionController, task.taskId(), TaskStatus.FAILED);
            // an unexpected (non-McpException) failure is reported as an internal error carrying the message
            Task failed = executionController.getTask(task.taskId()).orElseThrow();
            assertThat(failed.statusMessage()).hasValue("unexpected boom");
            assertThat(failed.error().orElseThrow().code()).isEqualTo(INTERNAL_ERROR.code());
        }
    }

    @Test
    public void testExecuteCancelableUnknownTaskReturnsFalse()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            AtomicBoolean handlerRan = new AtomicBoolean(false);

            boolean started = executionController.executeCancelable(UUID.randomUUID().toString(), () -> {
                handlerRan.set(true);
                return new CallToolResult(new TextContent("should not run", Optional.empty()));
            });

            assertThat(started).isFalse();

            // no execution was scheduled, so the handler must never run
            Thread.sleep(100);
            assertThat(handlerRan.get()).isFalse();
        }
    }

    @Test
    public void testExecuteCancelableRejectsSecondExecutionWhileRunning()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            boolean first = executionController.executeCancelable(task.taskId(), () -> {
                running.countDown();
                release.await();
                return new CallToolResult(new TextContent("first", Optional.empty()));
            });
            assertThat(first).isTrue();
            assertThat(running.await(5, SECONDS)).isTrue();

            // a second execution is rejected while the first still owns the (live) execution
            AtomicBoolean secondRan = new AtomicBoolean(false);
            boolean second = executionController.executeCancelable(task.taskId(), () -> {
                secondRan.set(true);
                return new CallToolResult(new TextContent("second", Optional.empty()));
            });
            assertThat(second).isFalse();
            assertThat(secondRan.get()).isFalse();

            release.countDown();
            awaitStatus(executionController, task.taskId(), TaskStatus.COMPLETED);
            assertThat(resultText(executionController, task.taskId())).isEqualTo("first");
        }
    }

    @Test
    public void testConcurrentExecuteCancelableHasSingleOwner()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            int concurrency = 16;
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger handlerRuns = new AtomicInteger();

            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        startGate.await();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    return executionController.executeCancelable(task.taskId(), () -> {
                        handlerRuns.incrementAndGet();
                        release.await();
                        return new CallToolResult(new TextContent("done", Optional.empty()));
                    });
                }));
            }

            // fire all executeCancelable() calls at once
            startGate.countDown();

            long successes = 0;
            for (CompletableFuture<Boolean> future : futures) {
                if (future.get(5, SECONDS)) {
                    successes++;
                }
            }

            // exactly one call may take ownership of the task's execution
            assertThat(successes).isEqualTo(1);

            release.countDown();
            awaitStatus(executionController, task.taskId(), TaskStatus.COMPLETED);

            // and only the single owner's handler ever ran
            assertThat(handlerRuns.get()).isEqualTo(1);
        }
    }

    @Test
    public void testExecuteCancelableCancellationRequestedInterruptsHandler()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            CountDownLatch running = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean(false);

            executionController.executeCancelable(task.taskId(), () -> {
                running.countDown();
                try {
                    Thread.sleep(30_000);
                }
                catch (InterruptedException e) {
                    interrupted.set(true);
                    throw e;
                }
                return new CallToolResult(new TextContent("should not complete", Optional.empty()));
            });

            assertThat(running.await(5, SECONDS)).isTrue();

            // client-requested cancellation: the supervisor observes it and interrupts the handler
            executionController.setErrorState(task.taskId(), CANCELLATION_REQUESTED, Optional.empty());

            awaitStatus(executionController, task.taskId(), TaskStatus.CANCELLED);
            assertThat(interrupted.get()).isTrue();
        }
    }

    @Test
    public void testExecuteCancelableCanceledInterruptsHandler()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            CountDownLatch running = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean(false);

            executionController.executeCancelable(task.taskId(), () -> {
                running.countDown();
                try {
                    Thread.sleep(30_000);
                }
                catch (InterruptedException e) {
                    interrupted.set(true);
                    throw e;
                }
                return new CallToolResult(new TextContent("should not complete", Optional.empty()));
            });

            assertThat(running.await(5, SECONDS)).isTrue();

            executionController.setErrorState(task.taskId(), CANCELED, Optional.empty());

            awaitStatus(executionController, task.taskId(), TaskStatus.CANCELLED);
            // setting CANCELED marks the task terminal immediately; the supervisor still
            // interrupts the running handler asynchronously, so wait for that to happen
            awaitTrue(interrupted);
        }
    }

    @Test
    public void testStaleExecutionIsTakenOver()
            throws Exception
    {
        // Long poll interval so the first supervisor does not refresh the execution
        // timestamp during the test, plus a tiny stale timeout so the first (blocked)
        // execution is quickly considered stale and can be taken over.
        McpConfig config = new McpConfig()
                .setTaskTtl(new Duration(30, SECONDS))
                .setTaskPollInterval(new Duration(30, SECONDS))
                .setStaleTaskExecutionTimeout(new Duration(1, MILLISECONDS));

        try (InternalTaskController executionController = new InternalTaskController(new MemoryStorageController(), config, buildJsonMapper())) {
            Task task = executionController.createTask(REQUEST_CONTEXT);

            CountDownLatch firstRunning = new CountDownLatch(1);
            boolean first = executionController.executeCancelable(task.taskId(), () -> {
                firstRunning.countDown();
                Thread.sleep(30_000);
                return new CallToolResult(new TextContent("first", Optional.empty()));
            });
            assertThat(first).isTrue();
            assertThat(firstRunning.await(5, SECONDS)).isTrue();

            // let the first execution's timestamp age past the (1ms) stale threshold
            Thread.sleep(50);

            boolean second = executionController.executeCancelable(task.taskId(), () -> new CallToolResult(new TextContent("second", Optional.empty())));

            // the new one takes over the stale execution
            assertThat(second).isTrue();

            awaitStatus(executionController, task.taskId(), TaskStatus.COMPLETED);
            // the winning execution's result is the one that sticks; the stale (first) one is dropped
            assertThat(resultText(executionController, task.taskId())).isEqualTo("second");
        }
    }

    @Test
    public void testInputRequiredRoundTripCompletesWithHandlerResult()
            throws Exception
    {
        try (InternalTaskController executionController = createExecutionTaskController()) {
            Task task = executionController.createTask(REQUEST_CONTEXT);
            String taskId = task.taskId();

            executionController.executeCancelable(taskId, () -> {
                // ask the client for input, moving the task to INPUT_REQUIRED
                CallToolResult inputRequest = new CallToolResult(new TextContent("need input", Optional.empty()))
                        .withInputRequests(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(ImmutableMap.of("confirm", new InputRequest("prompt", ImmutableMap.of()))));
                executionController.setResult(taskId, Optional.of(inputRequest), Optional.empty());

                // block until every requested input has been answered
                if (!executionController.awaitInputResponses(taskId, java.time.Duration.ofSeconds(5), ImmutableSet.of("confirm"))) {
                    throw new RuntimeException("timed out waiting for input responses");
                }

                // then produce the real result
                return new CallToolResult(new TextContent("confirmed", Optional.empty()));
            });

            // the handler has asked for input
            awaitStatus(executionController, taskId, TaskStatus.INPUT_REQUIRED);

            // client answers all pending input requests in a single update
            executionController.setTaskInputResponses(taskId, Optional.of(ImmutableMap.of("confirm", "yes")));

            // the handler resumes and completes the task with its real payload,
            // NOT an empty result caused by a premature COMPLETED transition
            awaitStatus(executionController, taskId, TaskStatus.COMPLETED);
            assertThat(resultText(executionController, taskId)).isEqualTo("confirmed");
        }
    }

    // ------------------------------------------------------------------
    // executeCancelable() - asynchronous task execution
    //
    // These tests exercise the two background threads that executeCancelable()
    // spawns (the handler thread and the supervisor thread) and the
    // single-owner "execution" guarantee that keeps concurrent executions
    // consistent. Each test uses its own controller with a short poll interval
    // so the supervisor reacts quickly, and the controller is closed (which
    // shuts down/interrupts the executor) at the end of the test.
    // ------------------------------------------------------------------

    private static InternalTaskController createExecutionTaskController()
    {
        // long TTL so the task never expires mid-test, short poll interval so the
        // supervisor thread notices cancellation quickly, default (5 min) stale
        // execution timeout so a live execution is never treated as stale.
        McpConfig config = new McpConfig()
                .setTaskTtl(new Duration(30, SECONDS))
                .setTaskPollInterval(new Duration(50, MILLISECONDS));
        return new InternalTaskController(new MemoryStorageController(), config, buildJsonMapper());
    }

    private static JsonMapper buildJsonMapper()
    {
        return new JsonMapperProvider()
                .withJsonSubTypes(ImmutableSet.of(buildJsonSubType()))
                .get();
    }

    @SuppressWarnings("BusyWait")
    private static void awaitStatus(McpTaskController controller, String taskId, TaskStatus expected)
            throws InterruptedException
    {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        TaskStatus last = null;
        while (System.nanoTime() < deadline) {
            last = controller.getTask(taskId).map(Task::status).orElse(null);
            if (last == expected) {
                return;
            }
            Thread.sleep(5);
        }
        assertThat(last).as("task %s did not reach status %s", taskId, expected).isEqualTo(expected);
    }

    @SuppressWarnings("BusyWait")
    private static void awaitTrue(AtomicBoolean flag)
            throws InterruptedException
    {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (flag.get()) {
                return;
            }
            Thread.sleep(5);
        }
        assertThat(flag.get()).as("handler was interrupted after cancellation").isTrue();
    }

    private static String resultText(McpTaskController controller, String taskId)
    {
        CompleteTaskResult completeResult = (CompleteTaskResult) controller.currentTaskResult(taskId).orElseThrow();
        CallToolResult callToolResult = completeResult.result().orElseThrow();
        TextContent textContent = (TextContent) callToolResult.content().orElseThrow().getFirst();
        return textContent.text();
    }
}
