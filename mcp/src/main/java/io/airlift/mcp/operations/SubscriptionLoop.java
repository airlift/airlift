package io.airlift.mcp.operations;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.airlift.log.Logger;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpTasks;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.Constants;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.SubscriptionFilter;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskNotification;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TASKS;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.model.Meta.normalize;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

class SubscriptionLoop
{
    private static final Logger log = Logger.get(SubscriptionLoop.class);

    private static final Object TOOLS_LIST_KEY = new Object();
    private static final Object PROMPTS_LIST_KEY = new Object();
    private static final Object RESOURCES_LIST_KEY = new Object();
    private static final Object RESOURCE_TEMPLATES_LIST_KEY = new Object();

    private final JsonMapper jsonMapper;
    private final Object requestId;
    private final McpEntities entities;
    private final RequestContextImpl requestContext;
    private final SubscriptionFilter subscriptionFilter;
    private final Optional<McpTasks> tasks;
    private final Duration streamingTimeout;
    private final Duration resourceSubscriptionCachePeriod;
    private final UUID subscriptionId;

    private Snapshot snapshot;

    SubscriptionLoop(
            JsonMapper jsonMapper,
            Object requestId,
            McpEntities entities,
            RequestContextImpl requestContext,
            SubscriptionFilter subscriptionFilter,
            Optional<McpTasks> tasks,
            Duration streamingTimeout,
            Duration resourceSubscriptionCachePeriod)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.requestId = requireNonNull(requestId, "requestId is null");
        this.entities = requireNonNull(entities, "entities is null");
        this.requestContext = requireNonNull(requestContext, "requestContext is null");
        this.tasks = requireNonNull(tasks, "tasks is null");
        this.subscriptionFilter = withAccessibleTasks(requireNonNull(subscriptionFilter, "subscriptionFilter is null"));
        this.streamingTimeout = requireNonNull(streamingTimeout, "streamingTimeout is null");
        this.resourceSubscriptionCachePeriod = requireNonNull(resourceSubscriptionCachePeriod, "resourceSubscriptionCachePeriod is null");

        subscriptionId = UUID.randomUUID();

        snapshot = buildSnapshot();
    }

    public record Acknowledgment(SubscriptionFilter notifications, Optional<Map<String, Object>> meta)
            implements Meta<Acknowledgment>
    {
        public Acknowledgment
        {
            requireNonNull(notifications, "notifications is null");
            meta = normalize(meta);
        }

        @Override
        public Acknowledgment withMeta(Map<String, Object> meta)
        {
            return new Acknowledgment(notifications, Optional.of(meta));
        }
    }

    @SuppressWarnings("BusyWait")
    void run()
    {
        Stopwatch timeoutStopwatch = Stopwatch.createStarted();

        Acknowledgment acknowledgment = withSubscriptionId(Acknowledgment.class, new Acknowledgment(subscriptionFilter, Optional.empty()));
        requestContext.sendMessage(NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED, Optional.of(acknowledgment));

        // a task's state is sent at once - one that settled before the subscription would otherwise never be reported. It is
        // the state the snapshot holds, so a change since is sent once, by the next poll, rather than now and again then
        snapshot.tasks().values().forEach(this::sendTask);

        while (timeoutStopwatch.elapsed().compareTo(streamingTimeout) < 0) {
            try {
                Thread.sleep(resourceSubscriptionCachePeriod.toMillis());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Subscription streaming interrupted");
                break;
            }

            // a changed task is sent as this snapshot read it, so the state sent is the one remembered as seen
            Snapshot newSnapshot = buildSnapshot();
            newSnapshot.hashes().forEach((key, hash) -> {
                if (!hash.equals(snapshot.hashes().get(key))) {
                    sendChange(key, newSnapshot);
                }
            });
            snapshot = newSnapshot;
        }

        CancelledNotification cancelledNotification = withSubscriptionId(CancelledNotification.class, new CancelledNotification(requestId, Optional.empty(), Optional.empty()));
        requestContext.sendMessage(NOTIFICATION_CANCELLED, Optional.of(cancelledNotification));
    }

    public record Notification(Optional<String> uri, Optional<Map<String, Object>> meta)
            implements Meta<Notification>
    {
        public Notification
        {
            requireNonNull(uri, "uri is null");
            meta = normalize(meta);
        }

        @Override
        public Notification withMeta(Map<String, Object> meta)
        {
            return new Notification(uri, Optional.of(meta));
        }
    }

    private record TaskKey(String taskId) {}

    // the hashes compared from one poll to the next, and the tasks as read for them
    private record Snapshot(Map<Object, String> hashes, Map<TaskKey, Task> tasks)
    {
        private Snapshot
        {
            hashes = ImmutableMap.copyOf(hashes);
            tasks = ImmutableMap.copyOf(tasks);
        }
    }

    private void sendChange(Object key, Snapshot snapshot)
    {
        if (key instanceof TaskKey taskKey) {
            Optional.ofNullable(snapshot.tasks().get(taskKey)).ifPresent(this::sendTask);
            return;
        }

        String message;
        Optional<String> uri;
        if (key.equals(TOOLS_LIST_KEY)) {
            message = NOTIFICATION_TOOLS_LIST_CHANGED;
            uri = Optional.empty();
        }
        else if (key.equals(PROMPTS_LIST_KEY)) {
            message = NOTIFICATION_PROMPTS_LIST_CHANGED;
            uri = Optional.empty();
        }
        else if (key.equals(RESOURCES_LIST_KEY) || key.equals(RESOURCE_TEMPLATES_LIST_KEY)) {
            message = NOTIFICATION_RESOURCES_LIST_CHANGED;
            uri = Optional.empty();
        }
        else {
            message = NOTIFICATION_RESOURCES_UPDATED;
            uri = Optional.of(String.valueOf(key));
        }

        Notification notification = withSubscriptionId(Notification.class, new Notification(uri, Optional.empty()));
        requestContext.sendMessage(message, Optional.of(notification));
    }

    private void sendTask(Task task)
    {
        TaskNotification notification = withSubscriptionId(TaskNotification.class, new TaskNotification(task, Optional.empty()));
        requestContext.sendMessage(NOTIFICATION_TASKS, Optional.of(notification));
    }

    // the acknowledgement must list only the task ids notifications will be sent for - an
    // unknown, expired or another caller's task is dropped, indistinguishably, as tasks/get would
    private SubscriptionFilter withAccessibleTasks(SubscriptionFilter subscriptionFilter)
    {
        Optional<List<String>> taskIds = subscriptionFilter.taskIds().map(requested -> requested.stream()
                .filter(taskId -> tasks.map(tasks -> tasks.validateTaskAccess(requestContext, taskId)).orElse(false))
                .distinct()
                .collect(toImmutableList()));

        return new SubscriptionFilter(
                subscriptionFilter.toolsListChanged(),
                subscriptionFilter.promptsListChanged(),
                subscriptionFilter.resourcesListChanged(),
                subscriptionFilter.resourceSubscriptions(),
                taskIds);
    }

    private Optional<Task> currentTask(String taskId)
    {
        return tasks.filter(tasks -> tasks.validateTaskAccess(requestContext, taskId))
                .flatMap(tasks -> tasks.getTask(taskId));
    }

    private <T extends Meta<?>> T withSubscriptionId(Class<T> clazz, T instance)
    {
        Map<String, Object> meta = ImmutableMap.of(Constants.METADATA_SUBSCRIPTION_ID, subscriptionId.toString());
        return clazz.cast(instance.withMeta(meta));
    }

    private Snapshot buildSnapshot()
    {
        ImmutableMap.Builder<Object, String> builder = ImmutableMap.builder();
        ImmutableMap.Builder<TaskKey, Task> tasksBuilder = ImmutableMap.builder();
        if (subscriptionFilter.toolsListChanged().orElse(false)) {
            builder.put(TOOLS_LIST_KEY, listHash(entities.tools(requestContext).stream()));
        }
        if (subscriptionFilter.promptsListChanged().orElse(false)) {
            builder.put(PROMPTS_LIST_KEY, listHash(entities.prompts(requestContext).stream()));
        }
        if (subscriptionFilter.resourcesListChanged().orElse(false)) {
            builder.put(RESOURCES_LIST_KEY, listHash(entities.resources(requestContext).stream()));
            builder.put(RESOURCE_TEMPLATES_LIST_KEY, listHash(entities.resourceTemplates(requestContext).stream()));
        }
        subscriptionFilter.resourceSubscriptions().ifPresent(resourceSubscriptions -> resourceSubscriptions.forEach(uri -> {
            Optional<List<ResourceContents>> resourceContents = entities.readResourceContents(requestContext, new ReadResourceRequest(uri, Optional.empty()))
                    .flatMap(ReadResourceResult::contents);
            String hash = resourceContents
                    .map(contents -> listHash(contents.stream()))
                    .orElse("");
            builder.put(uri, hash);
        }));
        subscriptionFilter.taskIds().ifPresent(taskIds -> taskIds.forEach(taskId -> {
            TaskKey key = new TaskKey(taskId);
            Optional<Task> task = currentTask(taskId);
            task.ifPresent(value -> tasksBuilder.put(key, value));
            builder.put(key, task.map(this::asJson).orElse(""));
        }));
        return new Snapshot(builder.build(), tasksBuilder.build());
    }

    @SuppressWarnings("UnstableApiUsage")
    private <T> String listHash(Stream<? extends T> stream)
    {
        Hasher hasher = Hashing.sha256().newHasher();
        stream.map(this::asJson)
                .forEach(json -> hasher.putString(json, UTF_8));
        return hasher.hash().toString();
    }

    private <T> String asJson(T item)
    {
        return jsonMapper.writeValueAsString(item);
    }
}
