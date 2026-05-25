package io.airlift.mcp;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.airlift.json.JsonSubType;
import io.airlift.log.Logger;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteTaskResult;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.Content.AudioContent;
import io.airlift.mcp.model.Content.EmbeddedResource;
import io.airlift.mcp.model.Content.ImageContent;
import io.airlift.mcp.model.Content.ResourceLink;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.InputRequiredTaskResult;
import io.airlift.mcp.model.Result;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.storage.StorageController;
import io.airlift.mcp.storage.StorageGroupId;
import io.airlift.mcp.storage.StorageKeyId;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.mcp.McpTaskController.SetStatus.TASK_IS_COMPLETED;
import static io.airlift.mcp.McpTaskController.SetStatus.TASK_IS_GONE;
import static io.airlift.mcp.model.Constants.METADATA_TASKS;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

public class McpTaskController
{
    private static final Logger log = Logger.get(McpTaskController.class);

    private static final StorageKeyId KEY_ID = new StorageKeyId("task");

    private final StorageController storageController;
    private final int taskTtlMs;
    private final int pollIntervalMs;
    private final JsonCodec<TaskEntry> taskEntryCodec;

    public enum ErrorState
    {
        NONE,
        FAILED,
        CANCELED,
    }

    public enum SetStatus
    {
        SUCCESS,
        TASK_IS_GONE,
        TASK_IS_COMPLETED,
    }

    // public for Jackson
    public record TaskEntry(
            ErrorState errorState,
            Instant createdAt,
            Instant updatedAt,
            Optional<String> statusMessage,
            Optional<Map<String, Object>> inputResponses,
            Optional<CallToolResult> result)
    {
        public TaskEntry
        {
            requireNonNull(errorState, "errorState is null");
            requireNonNull(createdAt, "createdAt is null");
            requireNonNull(updatedAt, "updatedAt is null");
            requireNonNull(statusMessage, "statusMessage is null");
            requireNonNull(inputResponses, "inputResponses is null");
            requireNonNull(result, "result is null");
        }

        TaskEntry withErrorState(ErrorState errorState, Optional<String> statusMessage)
        {
            return new TaskEntry(errorState, createdAt, Instant.now(), statusMessage, inputResponses, result);
        }

        TaskEntry withResult(Optional<CallToolResult> result, Optional<String> statusMessage)
        {
            return new TaskEntry(errorState, createdAt, Instant.now(), statusMessage, Optional.empty(), result);
        }

        TaskEntry withUpdatedAt()
        {
            return new TaskEntry(errorState, createdAt, Instant.now(), statusMessage, inputResponses, result);
        }

        TaskEntry withInputResponses(Optional<Map<String, Object>> inputResponses)
        {
            return new TaskEntry(errorState, createdAt, Instant.now(), statusMessage, inputResponses, Optional.empty());
        }
    }

    @Inject
    public McpTaskController(StorageController storageController, McpConfig mcpConfig)
    {
        this.storageController = requireNonNull(storageController, "storageController is null");

        taskTtlMs = toIntExact(mcpConfig.getTaskTtl().toMillis());
        pollIntervalMs = toIntExact(mcpConfig.getTaskPollInterval().toMillis());
        taskEntryCodec = buildTaskEntryCodec();
    }

    public static boolean clientSupportsTasks(ClientCapabilities clientCapabilities)
    {
        return clientCapabilities.experimental()
                .map(experimental -> experimental.containsKey(METADATA_TASKS))
                .orElse(false);
    }

    public Task createTask()
    {
        Instant now = Instant.now();
        StorageGroupId storageGroupId = new StorageGroupId(UUID.randomUUID().toString());
        storageController.createGroup(storageGroupId, Duration.ofMillis(taskTtlMs));

        TaskEntry taskEntry = new TaskEntry(ErrorState.NONE, now, now, Optional.empty(), Optional.empty(), Optional.empty());
        storageController.setValue(storageGroupId, KEY_ID, taskEntryCodec.toJson(taskEntry));
        return toTask(storageGroupId, taskEntry);
    }

    public Optional<Task> getTask(String taskId)
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        Optional<TaskEntry> taskEntry = storageController.getValue(storageGroupId, KEY_ID)
                .map(taskEntryCodec::fromJson);
        return taskEntry.map(entry -> toTask(storageGroupId, entry));
    }

    public Optional<Result> currentTaskResult(String taskId)
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        Optional<TaskEntry> taskEntry = storageController.getValue(storageGroupId, KEY_ID)
                .map(taskEntryCodec::fromJson);
        return taskEntry.map(entry -> {
            Task task = toTask(storageGroupId, entry);
            return switch (task.status()) {
                case WORKING, CANCELLED, FAILED -> new CompleteTaskResult(task, Optional.empty());
                case COMPLETED -> new CompleteTaskResult(task, entry.result);
                case INPUT_REQUIRED -> new InputRequiredTaskResult(task, entry.result.flatMap(CallToolResult::inputRequests));
            };
        });
    }

    public Optional<Map<String, Object>> currentInputResponses(String taskId)
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        return storageController.getValue(storageGroupId, KEY_ID)
                .map(taskEntryCodec::fromJson)
                .flatMap(TaskEntry::inputResponses);
    }

    public SetStatus setErrorState(String taskId, ErrorState errorState, Optional<String> statusMessage)
    {
        checkArgument(errorState != ErrorState.NONE, "errorState cannot be set to NONE");

        return updateTask(taskId, (taskEntry, setStatus) -> {
            if (isCompleted(taskEntry)) {
                setStatus.set(TASK_IS_COMPLETED);
                return taskEntry;
            }
            checkState(taskEntry.errorState == ErrorState.NONE, "errorState is already set to %s", taskEntry.errorState);
            return taskEntry.withErrorState(errorState, statusMessage);
        });
    }

    public SetStatus setTaskInputResponses(String taskId, Optional<Map<String, Object>> inputResponses)
    {
        return updateTask(taskId, (taskEntry, setStatus) -> {
            if (isCompleted(taskEntry)) {
                setStatus.set(TASK_IS_COMPLETED);
                return taskEntry;
            }
            return taskEntry.withInputResponses(inputResponses);
        });
    }

    public SetStatus pingTask(String taskId)
    {
        return updateTask(taskId, (taskEntry, setStatus) -> {
            if (isCompleted(taskEntry)) {
                setStatus.set(TASK_IS_COMPLETED);
                return taskEntry;
            }
            return taskEntry.withUpdatedAt();
        });
    }

    public SetStatus setResult(String taskId, Optional<CallToolResult> result, Optional<String> statusMessage)
    {
        return updateTask(taskId, (taskEntry, setStatus) -> {
            if (isCompleted(taskEntry)) {
                setStatus.set(TASK_IS_COMPLETED);
                return taskEntry;
            }
            return taskEntry.withResult(result, statusMessage);
        });
    }

    public boolean await(String taskId, Duration timeout)
            throws InterruptedException
    {
        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        return storageController.await(storageGroupId, timeout);
    }

    private static StorageGroupId toStorageGroupId(String taskId)
    {
        String normalized = UUID.fromString(taskId).toString(); // also validates
        return new StorageGroupId(normalized);
    }

    private boolean isCompleted(TaskEntry taskEntry)
    {
        TaskStatus taskStatus = toTaskStatus(taskEntry);
        return (taskStatus != TaskStatus.WORKING) && (taskStatus != TaskStatus.INPUT_REQUIRED);
    }

    private SetStatus updateTask(String taskId, BiFunction<TaskEntry, AtomicReference<SetStatus>, TaskEntry> updater)
    {
        AtomicReference<SetStatus> setStatus = new AtomicReference<>(TASK_IS_GONE);

        StorageGroupId storageGroupId = toStorageGroupId(taskId);
        storageController.computeValue(storageGroupId, KEY_ID, currentEntry -> {
            if (currentEntry.isEmpty()) {
                log.warn("Task %s has no current entry", taskId);
                return currentEntry;
            }

            TaskEntry taskEntry = taskEntryCodec.fromJson(currentEntry.get());
            return Optional.of(updater.apply(taskEntry, setStatus)).map(taskEntryCodec::toJson);
        });

        return setStatus.get();
    }

    private Task toTask(StorageGroupId storageGroupId, TaskEntry taskEntry)
    {
        return new Task(
                storageGroupId.group(),
                toTaskStatus(taskEntry),
                taskEntry.statusMessage,
                taskEntry.createdAt.toString(),
                taskEntry.updatedAt.toString(),
                OptionalInt.of(taskTtlMs),
                OptionalInt.of(pollIntervalMs));
    }

    private static TaskStatus toTaskStatus(TaskEntry taskEntry)
    {
        return switch (taskEntry.errorState) {
            case CANCELED -> TaskStatus.CANCELLED;
            case FAILED -> TaskStatus.FAILED;
            case NONE -> taskEntry.result.map(result -> result.inputRequests().isPresent() ? TaskStatus.INPUT_REQUIRED : TaskStatus.COMPLETED)
                    .orElse(TaskStatus.WORKING);
        };
    }

    private static JsonCodec<TaskEntry> buildTaskEntryCodec()
    {
        JsonSubType jsonSubType = JsonSubType.builder()
                .forBase(Content.class, "type")
                .add(TextContent.class, "text")
                .add(ImageContent.class, "image")
                .add(AudioContent.class, "audio")
                .add(EmbeddedResource.class, "resource")
                .add(ResourceLink.class, "resource_link")
                .build();

        JsonMapperProvider jsonMapperProvider = new JsonMapperProvider();
        jsonMapperProvider.setJsonSubTypes(ImmutableSet.of(jsonSubType));
        JsonMapper jsonMapper = jsonMapperProvider.get();
        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(jsonMapper);

        return jsonCodecFactory.jsonCodec(TaskEntry.class);
    }
}
