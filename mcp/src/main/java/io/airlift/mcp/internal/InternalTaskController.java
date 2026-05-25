package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.concurrent.Threads;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.log.Logger;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpTaskController;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteTaskResult;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputRequiredTaskResult;
import io.airlift.mcp.model.InputResponses;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.Result;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskHandler;
import io.airlift.mcp.model.TaskHandlerResult;
import io.airlift.mcp.model.TaskHandlerResult.Incomplete;
import io.airlift.mcp.model.TaskHandlerResult.TaskFailed;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.storage.StorageController;
import io.airlift.mcp.storage.StorageGroupId;
import io.airlift.mcp.storage.StorageKeyId;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.mcp.McpException.clientCapabilityError;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpTaskController.ErrorState.CANCELED;
import static io.airlift.mcp.McpTaskController.ErrorState.CANCELLATION_REQUESTED;
import static io.airlift.mcp.McpTaskController.ErrorState.FAILED;
import static io.airlift.mcp.McpTaskController.ErrorState.NONE;
import static io.airlift.mcp.McpTaskController.SetStatus.SUCCESS;
import static io.airlift.mcp.McpTaskController.SetStatus.TASK_COMPLETED;
import static io.airlift.mcp.McpTaskController.SetStatus.TASK_NOT_FOUND;
import static io.airlift.mcp.model.Constants.METADATA_TASKS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.TimeUnit.SECONDS;

public class InternalTaskController
        implements AutoCloseable, McpTaskController
{
    private static final Logger log = Logger.get(McpTaskController.class);

    private static final StorageKeyId KEY_ID = new StorageKeyId("task");

    private final StorageController storageController;
    private final int taskTtlMs;
    private final int pollIntervalMs;
    private final JsonCodec<InternalTask> taskEntryCodec;
    private final ExecutorService executorService;
    private final Duration staleTaskExecutionTimeout;

    @Inject
    public InternalTaskController(StorageController storageController, McpConfig mcpConfig, JsonMapper jsonMapper)
    {
        this.storageController = requireNonNull(storageController, "storageController is null");

        taskTtlMs = toIntExact(mcpConfig.getTaskTtl().toMillis());
        pollIntervalMs = toIntExact(mcpConfig.getTaskPollInterval().toMillis());
        taskEntryCodec = new JsonCodecFactory(jsonMapper).jsonCodec(InternalTask.class);
        staleTaskExecutionTimeout = mcpConfig.getStaleTaskExecutionTimeout().toJavaTime();

        executorService = Executors.newThreadPerTaskExecutor(Threads.virtualThreadsNamed("mcp-task-%d"));
    }

    @PreDestroy
    @Override
    public void close()
    {
        if (!shutdownAndAwaitTermination(executorService, 10, SECONDS)) {
            log.warn("Failed to shutdown task executor");
        }
    }

    @Override
    public boolean executeCancelable(String taskId, TaskHandler handler)
    {
        UUID executionId = UUID.randomUUID();

        if (!setTaskExecution(taskId, executionId)) {
            return false;
        }

        AtomicBoolean taskCancelled = new AtomicBoolean(false);

        // thread to run the task handler
        Future<?> taskFuture = executorService.submit(() -> {
            try {
                TaskHandlerResult handlerResult = handler.run();
                finalizeTaskExecution(taskId, executionId, handlerResult);
            }
            catch (McpException e) {
                finalizeTaskExecution(taskId, executionId, new TaskFailed(FAILED, Optional.of(e.errorDetail())));
            }
            catch (Exception e) {
                if (taskCancelled.get()) {
                    finalizeTaskExecution(taskId, executionId, new TaskFailed(CANCELED, Optional.empty()));
                }
                else {
                    finalizeTaskExecution(taskId, executionId, new TaskFailed(FAILED, Optional.of(new JsonRpcErrorDetail(INTERNAL_ERROR, requireNonNullElse(e.getMessage(), "Unknown error")))));
                }
            }
        });

        // supervisor thread
        executorService.submit(() -> {
            while (!taskFuture.isDone()) {
                if (!updateTaskExecution(taskId, executionId, taskCancelled::set)) {
                    taskFuture.cancel(true);
                    break;
                }

                try {
                    await(taskId, Duration.ofMillis(pollIntervalMs));
                }
                catch (InterruptedException e) {
                    log.warn("Task %s has been interrupted", taskId);
                    taskFuture.cancel(true);
                    break;
                }
            }
        });

        return true;
    }

    public Task createTask(McpRequestContext requestContext)
    {
        validateClientCapabilities(requestContext.clientCapabilities());

        Instant now = Instant.now();
        StorageGroupId storageGroupId = new StorageGroupId(UUID.randomUUID().toString());
        storageController.createGroup(storageGroupId, Duration.ofMillis(taskTtlMs));

        InternalTask taskEntry = new InternalTask(NONE, now, now, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        storageController.setValue(storageGroupId, KEY_ID, taskEntryCodec.toJson(taskEntry));
        return toTask(storageGroupId, taskEntry);
    }

    public static void validateClientCapabilities(ClientCapabilities clientCapabilities)
    {
        if (!clientCapabilities.supportsTasks()) {
            ImmutableMap<String, Object> extensions = ImmutableMap.of(METADATA_TASKS, new Object());
            throw clientCapabilityError(new ClientCapabilities(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(extensions), Optional.empty()));
        }
    }

    @Override
    public Optional<Task> getTask(String taskId)
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        Optional<InternalTask> taskEntry = storageController.getValue(storageGroupId, KEY_ID)
                .map(taskEntryCodec::fromJson);
        return taskEntry.map(entry -> toTask(storageGroupId, entry));
    }

    @Override
    public Optional<Result> currentTaskResult(String taskId)
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        Optional<InternalTask> taskEntry = storageController.getValue(storageGroupId, KEY_ID)
                .map(taskEntryCodec::fromJson);
        return taskEntry.map(entry -> {
            Task task = toTask(storageGroupId, entry);
            return switch (task.status()) {
                case WORKING, CANCELLED, FAILED -> new CompleteTaskResult(task, Optional.empty());
                case COMPLETED -> new CompleteTaskResult(task, entry.result());
                case INPUT_REQUIRED -> new InputRequiredTaskResult(task, entry.result().flatMap(CallToolResult::inputRequests));
            };
        });
    }

    @Override
    public InputResponses currentInputResponses(String taskId)
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        Optional<Map<String, Object>> inputResponses = storageController.getValue(storageGroupId, KEY_ID)
                .map(taskEntryCodec::fromJson)
                .flatMap(InternalTask::inputResponses);
        return () -> inputResponses;
    }

    @Override
    public ErrorState getErrorState(String taskId)
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        return storageController.getValue(storageGroupId, KEY_ID)
                .map(taskEntryCodec::fromJson)
                .map(InternalTask::errorState)
                .orElse(NONE);
    }

    @Override
    public SetStatus setErrorState(String taskId, ErrorState errorState, Optional<JsonRpcErrorDetail> errorDetail)
    {
        checkArgument(errorState != NONE, "errorState cannot be set to NONE");

        return updateTask(taskId, TASK_NOT_FOUND, (taskEntry, setStatus) -> {
            if (isCompleted(taskEntry)) {
                setStatus.accept(TASK_COMPLETED);
                return taskEntry;
            }

            setStatus.accept(SUCCESS);
            checkState((taskEntry.errorState() == ErrorState.NONE) || (taskEntry.errorState() == CANCELLATION_REQUESTED), "errorState is already set to %s", taskEntry.errorState());

            if (errorDetail.isEmpty() && (errorState == FAILED)) {
                return taskEntry.withErrorState(errorState, Optional.of(new JsonRpcErrorDetail(INTERNAL_ERROR, "Internal error")));
            }

            return taskEntry.withErrorState(errorState, errorDetail);
        });
    }

    @Override
    public SetStatus setTaskInputResponses(String taskId, Optional<Map<String, Object>> inputResponses)
    {
        return updateTask(taskId, TASK_NOT_FOUND, (taskEntry, setStatus) -> {
            if (isCompleted(taskEntry)) {
                setStatus.accept(TASK_COMPLETED);
                return taskEntry;
            }

            Map<String, Object> unwrappedInputResponses = inputResponses.orElseGet(ImmutableMap::of);

            Optional<CallToolResult> mappedResult = taskEntry.result().flatMap(result -> result.inputRequests().flatMap(inputRequests -> {
                Map<String, InputRequest> updatedInputRequests = inputRequests.entrySet()
                        .stream()
                        .filter(entry -> !unwrappedInputResponses.containsKey(entry.getKey()))
                        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
                return updatedInputRequests.isEmpty()
                        ? Optional.empty()   // all answered → clear result → back to WORKING so the handler can post its final result
                        : Optional.of(result.withInputRequests(Optional.empty(), Optional.empty(), Optional.of(updatedInputRequests)));
            }));

            Optional<Map<String, Object>> mergedInputResponses = taskEntry.inputResponses().map(oldInputResponses -> {
                ImmutableMap.Builder<String, Object> newInputResponses = ImmutableMap.builder();
                newInputResponses.putAll(oldInputResponses);
                newInputResponses.putAll(unwrappedInputResponses);
                return (Map<String, Object>) newInputResponses.buildKeepingLast();
            }).or(() -> inputResponses);

            setStatus.accept(SUCCESS);
            return taskEntry.withResult(mappedResult, taskEntry.statusMessage())
                    .withInputResponses(mergedInputResponses);
        });
    }

    @Override
    public SetStatus setResult(String taskId, Optional<CallToolResult> result, Optional<String> statusMessage)
    {
        return internalSetResult(taskId, result, statusMessage, false);
    }

    @Override
    public boolean awaitInputResponses(String taskId, Duration timeout, Set<String> keys)
            throws InterruptedException
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);

        Stopwatch stopwatch = Stopwatch.createStarted();
        while (true) {
            Optional<InternalTask> maybeTaskEntry = storageController.getValue(storageGroupId, KEY_ID)
                    .map(taskEntryCodec::fromJson);
            if (maybeTaskEntry.isEmpty()) {
                log.warn("Task %s has no entry - exiting awaitInputResponses()", taskId);
                return false;
            }
            InternalTask taskEntry = maybeTaskEntry.orElseThrow();
            if (taskEntry.inputResponses().map(inputResponses -> inputResponses.keySet().containsAll(keys)).orElse(false)) {
                return true;
            }

            Duration waitDuration = timeout.minus(stopwatch.elapsed());
            if (waitDuration.isNegative()) {
                break;
            }
            await(taskId, waitDuration);
        }

        return false;
    }

    @Override
    public boolean await(String taskId, Duration timeout)
            throws InterruptedException
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        return storageController.await(storageGroupId, timeout);
    }

    private SetStatus internalSetResult(String taskId, Optional<CallToolResult> result, Optional<String> statusMessage, boolean clearErrorState)
    {
        return updateTask(taskId, TASK_NOT_FOUND, (taskEntry, setStatus) -> {
            if (isCompleted(taskEntry)) {
                setStatus.accept(TASK_COMPLETED);
                return taskEntry;
            }

            if (clearErrorState) {
                taskEntry = taskEntry.withErrorState(NONE, Optional.empty());
            }

            setStatus.accept(SUCCESS);
            return taskEntry.withResult(result, statusMessage);
        });
    }

    private void finalizeTaskExecution(String taskId, UUID executionId, TaskHandlerResult handlerResult)
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        Optional<InternalTask> maybeTaskEntry = storageController.getValue(storageGroupId, KEY_ID)
                .map(taskEntryCodec::fromJson);
        if (maybeTaskEntry.isEmpty()) {
            log.warn("Task %s has no entry - dropping result. ExecutionID: %s", taskId, executionId);
            return;
        }
        InternalTask taskEntry = maybeTaskEntry.orElseThrow();
        if (taskEntry.execution().isEmpty() || !taskEntry.execution().orElseThrow().executionId().equals(executionId)) {
            log.warn("Lost execution - dropping result. TaskId: %s, ExecutionID: %s", taskId, executionId);
            return;
        }

        // note: this is not atomic and is not intended to be. This is best-effort only.
        switch (handlerResult) {
            case TaskFailed(var errorState, var errorDetail) -> setErrorState(taskId, errorState, errorDetail);
            // when taskEntry.errorState() == CANCELLATION_REQUESTED, task never saw the cancellation and finished - this is an acceptable completion
            case CallToolResult callToolResult -> internalSetResult(taskId, Optional.of(callToolResult), Optional.empty(), taskEntry.errorState() == CANCELLATION_REQUESTED);
            case Incomplete _ -> {}
        }
    }

    private boolean updateTaskExecution(String taskId, UUID executionId, Consumer<Boolean> taskCancelled)
    {
        return updateTask(taskId, false, (taskEntry, setResult) -> {
            if (taskEntry.execution().isEmpty() || !taskEntry.execution().orElseThrow().executionId().equals(executionId)) {
                log.warn("Lost execution. Exiting. TaskId: %s, ExecutionID: %s", taskId, executionId);
                return taskEntry;
            }

            ErrorState errorState = taskEntry.errorState();
            if ((errorState == CANCELED) || (errorState == CANCELLATION_REQUESTED)) {
                log.warn("Task %s has been cancelled", taskId);
                taskCancelled.accept(true);
                return taskEntry;
            }

            setResult.accept(true);
            return taskEntry.withExecution(new InternalTaskExecution(executionId, Instant.now()));
        });
    }

    private boolean setTaskExecution(String taskId, UUID executionId)
    {
        return updateTask(taskId, false, (taskEntry, setSuccess) -> {
            if (taskEntry.execution().isEmpty()) {
                setSuccess.accept(true);
                return taskEntry.withExecution(new InternalTaskExecution(executionId, Instant.now()));
            }

            InternalTaskExecution currentExecution = taskEntry.execution().orElseThrow();
            if (currentExecution.hasExpired(staleTaskExecutionTimeout)) {
                log.warn("Execution has expired. Taking execution. TaskId: %s, ExecutionID: %s", taskId, currentExecution.executionId());
                setSuccess.accept(true);
                return taskEntry.withExecution(new InternalTaskExecution(executionId, Instant.now()));
            }
            return taskEntry;
        });
    }

    private static StorageGroupId toStorageGroupId(String taskId)
    {
        try {
            String normalized = UUID.fromString(taskId).toString(); // also validates
            return new StorageGroupId(normalized);
        }
        catch (IllegalArgumentException e) {
            throw exception(INVALID_PARAMS, "Task not found: " + taskId);
        }
    }

    private boolean isCompleted(InternalTask taskEntry)
    {
        if (taskEntry.errorState() == CANCELLATION_REQUESTED) {
            return false;
        }

        TaskStatus taskStatus = toTaskStatus(taskEntry);
        return (taskStatus != TaskStatus.WORKING) && (taskStatus != TaskStatus.INPUT_REQUIRED);
    }

    private <T> T updateTask(String taskId, T defaultValue, BiFunction<InternalTask, Consumer<T>, InternalTask> updater)
    {
        AtomicReference<T> setResult = new AtomicReference<>(defaultValue);

        StorageGroupId storageGroupId = toStorageGroupId(taskId);

        storageController.computeValue(storageGroupId, KEY_ID, currentEntry -> {
            if (currentEntry.isEmpty()) {
                log.warn("Task %s has no current entry", taskId);
                return currentEntry;
            }

            return currentEntry.map(taskEntryCodec::fromJson)
                    .map(taskEntry -> updater.apply(taskEntry, setResult::set))
                    .map(taskEntryCodec::toJson);
        });

        return setResult.get();
    }

    private Task toTask(StorageGroupId storageGroupId, InternalTask taskEntry)
    {
        return new Task(
                storageGroupId.group(),
                toTaskStatus(taskEntry),
                taskEntry.statusMessage(),
                taskEntry.createdAt().toString(),
                taskEntry.updatedAt().toString(),
                OptionalInt.of(taskTtlMs),
                OptionalInt.of(pollIntervalMs),
                taskEntry.error());
    }

    private static TaskStatus toTaskStatus(InternalTask taskEntry)
    {
        return switch (taskEntry.errorState()) {
            case CANCELLATION_REQUESTED -> TaskStatus.WORKING;
            case CANCELED -> TaskStatus.CANCELLED;
            case FAILED -> TaskStatus.FAILED;
            case NONE -> taskEntry.result().map(result -> result.inputRequests().isPresent() ? TaskStatus.INPUT_REQUIRED : TaskStatus.COMPLETED)
                    .orElse(TaskStatus.WORKING);
        };
    }
}
