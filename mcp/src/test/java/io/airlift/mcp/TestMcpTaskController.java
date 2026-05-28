package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpTaskController.ErrorState;
import io.airlift.mcp.McpTaskController.SetStatus;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteTaskResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputRequiredTaskResult;
import io.airlift.mcp.model.InputResponses;
import io.airlift.mcp.model.Result;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.storage.MemoryStorageController;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.airlift.mcp.McpTaskController.SetStatus.TASK_IS_COMPLETED;
import static io.airlift.mcp.McpTaskController.SetStatus.TASK_IS_GONE;
import static java.time.Duration.ZERO;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMcpTaskController
{
    private final McpTaskController controller = createController();

    private static McpTaskController createController()
    {
        McpConfig config = new McpConfig()
                .setTaskTtl(new Duration(15, MINUTES))
                .setTaskPollInterval(new Duration(15, SECONDS));
        return new McpTaskController(new MemoryStorageController(), config);
    }

    private static McpTaskController createShortTtlController()
    {
        McpConfig config = new McpConfig()
                .setTaskTtl(new Duration(100, MILLISECONDS))
                .setTaskPollInterval(new Duration(10, MILLISECONDS));
        return new McpTaskController(new MemoryStorageController(ZERO), config);
    }

    @Test
    public void testCreateTask()
    {
        Task task = controller.createTask();
        assertThat(task.taskId()).isNotNull();
        assertThat(task.status()).isEqualTo(TaskStatus.WORKING);
        assertThat(task.statusMessage()).isEmpty();
        assertThat(task.createdAt()).isNotNull();
        assertThat(task.lastUpdatedAt()).isNotNull();
    }

    @Test
    public void testCreateTaskProducesUniqueIds()
    {
        Task task1 = controller.createTask();
        Task task2 = controller.createTask();
        assertThat(task1.taskId()).isNotEqualTo(task2.taskId());
    }

    @Test
    public void testGetTask()
    {
        Task created = controller.createTask();
        Optional<Task> retrieved = controller.getTask(created.taskId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().taskId()).isEqualTo(created.taskId());
        assertThat(retrieved.get().status()).isEqualTo(TaskStatus.WORKING);
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
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testSetResultCompletes()
    {
        Task task = controller.createTask();
        CallToolResult toolResult = new CallToolResult(new TextContent("done", Optional.empty()));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.of("completed"));

        Task updated = controller.getTask(task.taskId()).orElseThrow();
        assertThat(updated.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(updated.statusMessage()).hasValue("completed");
    }

    @Test
    public void testSetResultWithInputRequests()
    {
        Task task = controller.createTask();
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
        Task task = controller.createTask();
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
        Task task = controller.createTask();
        CallToolResult toolResult = new CallToolResult(new TextContent("done", Optional.empty()));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.COMPLETED);

        SetStatus status = controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());
        assertThat(status).isEqualTo(TASK_IS_COMPLETED);
    }

    @Test
    public void testSetResultOnNonexistentTask()
    {
        CallToolResult toolResult = new CallToolResult(new TextContent("done", Optional.empty()));
        SetStatus status = controller.setResult(UUID.randomUUID().toString(), Optional.of(toolResult), Optional.empty());
        assertThat(status).isEqualTo(TASK_IS_GONE);
    }

    @Test
    public void testSetErrorStateFailed()
    {
        Task task = controller.createTask();
        controller.setErrorState(task.taskId(), ErrorState.FAILED, Optional.of("something went wrong"));

        Task updated = controller.getTask(task.taskId()).orElseThrow();
        assertThat(updated.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(updated.statusMessage()).hasValue("something went wrong");
    }

    @Test
    public void testSetErrorStateCanceled()
    {
        Task task = controller.createTask();
        controller.setErrorState(task.taskId(), ErrorState.CANCELED, Optional.of("user canceled"));

        Task updated = controller.getTask(task.taskId()).orElseThrow();
        assertThat(updated.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(updated.statusMessage()).hasValue("user canceled");
    }

    @Test
    public void testSetErrorStateNoneRejected()
    {
        Task task = controller.createTask();
        assertThatThrownBy(() -> controller.setErrorState(task.taskId(), ErrorState.NONE, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be set to NONE");
    }

    @Test
    public void testSetErrorStateOnAlreadyFailedTask()
    {
        Task task = controller.createTask();
        controller.setErrorState(task.taskId(), ErrorState.FAILED, Optional.empty());

        SetStatus status = controller.setErrorState(task.taskId(), ErrorState.CANCELED, Optional.empty());
        assertThat(status).isEqualTo(TASK_IS_COMPLETED);
    }

    @Test
    public void testSetErrorStateOnCompletedTask()
    {
        Task task = controller.createTask();
        controller.setResult(task.taskId(), Optional.of(new CallToolResult(new TextContent("done", Optional.empty()))), Optional.empty());

        SetStatus status = controller.setErrorState(task.taskId(), ErrorState.FAILED, Optional.empty());
        assertThat(status).isEqualTo(TASK_IS_COMPLETED);
    }

    @Test
    public void testSetErrorStateOnNonexistentTask()
    {
        SetStatus status = controller.setErrorState(UUID.randomUUID().toString(), ErrorState.FAILED, Optional.empty());
        assertThat(status).isEqualTo(TASK_IS_GONE);
    }

    @Test
    public void testPingTaskUpdatesTimestamp()
            throws InterruptedException
    {
        Task task = controller.createTask();
        String originalUpdatedAt = task.lastUpdatedAt();

        Thread.sleep(10);
        controller.pingTask(task.taskId());

        Task updated = controller.getTask(task.taskId()).orElseThrow();
        assertThat(updated.lastUpdatedAt()).isNotEqualTo(originalUpdatedAt);
        assertThat(updated.status()).isEqualTo(TaskStatus.WORKING);
    }

    @Test
    public void testPingCompletedTask()
    {
        Task task = controller.createTask();
        controller.setResult(task.taskId(), Optional.of(new CallToolResult(new TextContent("done", Optional.empty()))), Optional.empty());

        SetStatus status = controller.pingTask(task.taskId());
        assertThat(status).isEqualTo(TASK_IS_COMPLETED);
    }

    @Test
    public void testPingNonexistentTask()
    {
        SetStatus status = controller.pingTask(UUID.randomUUID().toString());
        assertThat(status).isEqualTo(TASK_IS_GONE);
    }

    @Test
    public void testSetAndGetInputResponses()
    {
        Task task = controller.createTask();
        Map<String, Object> responses = ImmutableMap.of("answer", "42");
        controller.setTaskInputResponses(task.taskId(), Optional.of(responses));

        Optional<InputResponses> retrieved = controller.currentInputResponses(task.taskId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().inputResponses().orElseThrow()).containsEntry("answer", "42");
    }

    @Test
    public void testClearInputResponses()
    {
        Task task = controller.createTask();
        controller.setTaskInputResponses(task.taskId(), Optional.of(ImmutableMap.of("key", "value")));
        assertThat(controller.currentInputResponses(task.taskId())).isPresent();

        controller.setTaskInputResponses(task.taskId(), Optional.empty());
        assertThat(controller.currentInputResponses(task.taskId())).isEmpty();
    }

    @Test
    public void testInputResponsesOnNewTask()
    {
        Task task = controller.createTask();
        assertThat(controller.currentInputResponses(task.taskId())).isEmpty();
    }

    @Test
    public void testInputResponsesOnNonexistentTask()
    {
        assertThat(controller.currentInputResponses(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    public void testSetInputResponsesOnCompletedTask()
    {
        Task task = controller.createTask();
        controller.setResult(task.taskId(), Optional.of(new CallToolResult(new TextContent("done", Optional.empty()))), Optional.empty());

        SetStatus status = controller.setTaskInputResponses(task.taskId(), Optional.of(ImmutableMap.of("key", "value")));
        assertThat(status).isEqualTo(TASK_IS_COMPLETED);
    }

    @Test
    public void testCurrentTaskResultWorking()
    {
        Task task = controller.createTask();
        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(CompleteTaskResult.class);

        CompleteTaskResult taskResult = (CompleteTaskResult) result.get();
        assertThat(taskResult.task().status()).isEqualTo(TaskStatus.WORKING);
        assertThat(taskResult.result()).isEmpty();
    }

    @Test
    public void testCurrentTaskResultCompleted()
    {
        Task task = controller.createTask();
        CallToolResult toolResult = new CallToolResult(new TextContent("done", Optional.empty()));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());

        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(CompleteTaskResult.class);

        CompleteTaskResult completeResult = (CompleteTaskResult) result.get();
        assertThat(completeResult.task().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completeResult.result()).isPresent();
    }

    @Test
    public void testCurrentTaskResultInputRequired()
    {
        Task task = controller.createTask();
        CallToolResult toolResult = new CallToolResult(new TextContent("need input", Optional.empty()))
                .withInputRequests(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(ImmutableMap.of("q", new InputRequest("prompt", ImmutableMap.of()))));
        controller.setResult(task.taskId(), Optional.of(toolResult), Optional.empty());

        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(InputRequiredTaskResult.class);

        InputRequiredTaskResult inputResult = (InputRequiredTaskResult) result.get();
        assertThat(inputResult.task().status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
        assertThat(inputResult.inputRequests()).isPresent();
    }

    @Test
    public void testCurrentTaskResultFailed()
    {
        Task task = controller.createTask();
        controller.setErrorState(task.taskId(), ErrorState.FAILED, Optional.of("error"));

        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(CompleteTaskResult.class);

        CompleteTaskResult taskResult = (CompleteTaskResult) result.get();
        assertThat(taskResult.task().status()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    public void testCurrentTaskResultCancelled()
    {
        Task task = controller.createTask();
        controller.setErrorState(task.taskId(), ErrorState.CANCELED, Optional.empty());

        Optional<Result> result = controller.currentTaskResult(task.taskId());
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(CompleteTaskResult.class);

        CompleteTaskResult taskResult = (CompleteTaskResult) result.get();
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
        Task task = controller.createTask();
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
        assertThat(controller.currentInputResponses(task.taskId())).isPresent();

        // complete the task
        CallToolResult finalResult = new CallToolResult(new TextContent("all done", Optional.empty()));
        controller.setResult(task.taskId(), Optional.of(finalResult), Optional.of("finished"));
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(controller.getTask(task.taskId()).orElseThrow().statusMessage()).hasValue("finished");
    }

    @Test
    public void testWorkingToFailed()
    {
        Task task = controller.createTask();
        assertThat(controller.getTask(task.taskId()).orElseThrow().status()).isEqualTo(TaskStatus.WORKING);

        controller.setErrorState(task.taskId(), ErrorState.FAILED, Optional.of("oops"));
        Task failed = controller.getTask(task.taskId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.statusMessage()).hasValue("oops");

        // further mutations are rejected
        assertThat(controller.setResult(task.taskId(), Optional.of(new CallToolResult(new TextContent("late", Optional.empty()))), Optional.empty()))
                .isEqualTo(TASK_IS_COMPLETED);
        assertThat(controller.pingTask(task.taskId())).isEqualTo(TASK_IS_COMPLETED);
    }

    @Test
    public void testTaskContainsTtlAndPollInterval()
    {
        Task task = controller.createTask();
        assertThat(task.ttlMs()).hasValue(15 * 60 * 1000);
        assertThat(task.pollIntervalMs()).hasValue(15 * 1000);
    }

    @Test
    public void testShortTtlTaskContainsConfiguredValues()
    {
        McpTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask();
        assertThat(task.ttlMs()).hasValue(100);
        assertThat(task.pollIntervalMs()).hasValue(10);
    }

    @Test
    public void testTaskExpiresAfterTtl()
            throws InterruptedException
    {
        McpTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask();
        assertThat(shortTtlController.getTask(task.taskId())).isPresent();

        Thread.sleep(200);

        assertThat(shortTtlController.getTask(task.taskId())).isEmpty();
    }

    @Test
    public void testTaskAccessRefreshesTtl()
            throws InterruptedException
    {
        McpTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask();

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
        McpTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask();

        Thread.sleep(200);

        SetStatus status = shortTtlController.setResult(task.taskId(), Optional.empty(), Optional.empty());
        assertThat(status).isEqualTo(TASK_IS_GONE);
    }

    @Test
    public void testExpiredTaskSetErrorStateReturnsGone()
            throws InterruptedException
    {
        McpTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask();

        Thread.sleep(200);

        SetStatus status = shortTtlController.setErrorState(task.taskId(), ErrorState.FAILED, Optional.empty());
        assertThat(status).isEqualTo(TASK_IS_GONE);
    }

    @Test
    public void testExpiredTaskPingReturnsGone()
            throws InterruptedException
    {
        McpTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask();

        Thread.sleep(200);

        SetStatus status = shortTtlController.pingTask(task.taskId());
        assertThat(status).isEqualTo(TASK_IS_GONE);
    }

    @Test
    public void testExpiredTaskCurrentTaskResultReturnsEmpty()
            throws InterruptedException
    {
        McpTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask();

        Thread.sleep(200);

        assertThat(shortTtlController.currentTaskResult(task.taskId())).isEmpty();
    }

    @Test
    public void testExpiredTaskInputResponsesReturnsEmpty()
            throws InterruptedException
    {
        McpTaskController shortTtlController = createShortTtlController();
        Task task = shortTtlController.createTask();

        Thread.sleep(200);

        assertThat(shortTtlController.currentInputResponses(task.taskId())).isEmpty();
    }

    @Test
    public void testAwaitTimesOut()
            throws InterruptedException
    {
        Task task = controller.createTask();
        boolean signaled = controller.await(task.taskId(), java.time.Duration.ofMillis(50));
        assertThat(signaled).isFalse();
    }

    @Test
    public void testAwaitSignaledByPing()
            throws Exception
    {
        Task task = controller.createTask();

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
        controller.pingTask(task.taskId());

        assertThat(awaitResult.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testAwaitSignaledBySetResult()
            throws Exception
    {
        Task task = controller.createTask();

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

        assertThat(awaitResult.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testAwaitSignaledBySetErrorState()
            throws Exception
    {
        Task task = controller.createTask();

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
        controller.setErrorState(task.taskId(), ErrorState.FAILED, Optional.empty());

        assertThat(awaitResult.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testAwaitSignaledByInputResponses()
            throws Exception
    {
        Task task = controller.createTask();

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

        assertThat(awaitResult.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
}
