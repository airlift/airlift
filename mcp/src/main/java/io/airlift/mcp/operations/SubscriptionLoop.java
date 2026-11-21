package io.airlift.mcp.operations;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.Constants;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.SubscriptionFilter;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.model.Meta.normalize;
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
    private final Duration streamingTimeout;
    private final Duration resourceSubscriptionCachePeriod;
    private final UUID subscriptionId;

    private Map<Object, Optional<SubscriptionSnapshot>> snapshots = Map.of();

    SubscriptionLoop(
            JsonMapper jsonMapper,
            Object requestId,
            McpEntities entities,
            RequestContextImpl requestContext,
            SubscriptionFilter subscriptionFilter,
            Duration streamingTimeout,
            Duration resourceSubscriptionCachePeriod)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.requestId = requireNonNull(requestId, "requestId is null");
        this.entities = requireNonNull(entities, "entities is null");
        this.requestContext = requireNonNull(requestContext, "requestContext is null");
        this.subscriptionFilter = requireNonNull(subscriptionFilter, "subscriptionFilter is null");
        this.streamingTimeout = requireNonNull(streamingTimeout, "streamingTimeout is null");
        this.resourceSubscriptionCachePeriod = requireNonNull(resourceSubscriptionCachePeriod, "resourceSubscriptionCachePeriod is null");

        subscriptionId = UUID.randomUUID();

        snapshots = buildSnapshots();
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

        while (timeoutStopwatch.elapsed().compareTo(streamingTimeout) < 0) {
            try {
                Thread.sleep(resourceSubscriptionCachePeriod.toMillis());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Subscription streaming interrupted");
                break;
            }

            checkForChanges();
        }

        CancelledNotification cancelledNotification = withSubscriptionId(CancelledNotification.class, new CancelledNotification(requestId, Optional.empty(), Optional.empty()));
        requestContext.sendMessage(NOTIFICATION_CANCELLED, Optional.of(cancelledNotification));
    }

    @VisibleForTesting
    void checkForChanges()
    {
        Map<Object, Optional<SubscriptionSnapshot>> newSnapshots = buildSnapshots();
        newSnapshots.forEach((key, value) -> {
            if (!value.equals(snapshots.get(key))) {
                sendChange(key);
            }
        });
        snapshots = newSnapshots;
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

    private void sendChange(Object key)
    {
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

    private <T extends Meta<?>> T withSubscriptionId(Class<T> clazz, T instance)
    {
        Map<String, Object> meta = ImmutableMap.of(Constants.METADATA_SUBSCRIPTION_ID, subscriptionId.toString());
        return clazz.cast(instance.withMeta(meta));
    }

    private Map<Object, Optional<SubscriptionSnapshot>> buildSnapshots()
    {
        ImmutableMap.Builder<Object, Optional<SubscriptionSnapshot>> builder = ImmutableMap.builder();
        if (subscriptionFilter.toolsListChanged().orElse(false)) {
            builder.put(TOOLS_LIST_KEY, Optional.of(snapshot(TOOLS_LIST_KEY, entities.tools(requestContext))));
        }
        if (subscriptionFilter.promptsListChanged().orElse(false)) {
            builder.put(PROMPTS_LIST_KEY, Optional.of(snapshot(PROMPTS_LIST_KEY, entities.prompts(requestContext))));
        }
        if (subscriptionFilter.resourcesListChanged().orElse(false)) {
            builder.put(RESOURCES_LIST_KEY, Optional.of(snapshot(RESOURCES_LIST_KEY, entities.resources(requestContext))));
            builder.put(RESOURCE_TEMPLATES_LIST_KEY, Optional.of(snapshot(RESOURCE_TEMPLATES_LIST_KEY, entities.resourceTemplates(requestContext))));
        }
        subscriptionFilter.resourceSubscriptions().ifPresent(resourceSubscriptions -> resourceSubscriptions.forEach(uri -> {
            Optional<List<ResourceContents>> resourceContents = entities.readResourceContents(requestContext, new ReadResourceRequest(uri, Optional.empty()))
                    .flatMap(ReadResourceResult::contents);
            builder.put(uri, resourceContents.map(contents -> snapshot(uri, contents)));
        }));
        return builder.build();
    }

    private SubscriptionSnapshot snapshot(Object key, List<? extends Meta<?>> items)
    {
        return SubscriptionSnapshot.snapshot(jsonMapper, items, snapshots.getOrDefault(key, Optional.empty()).orElse(null));
    }
}
