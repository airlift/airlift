package io.airlift.mcp.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.airlift.log.Logger;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.Constants;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.SubscriptionFilter;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
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
    private final Duration streamingTimeout;
    private final Duration resourceSubscriptionCachePeriod;
    private final UUID subscriptionId;

    private Map<Object, String> hashes;

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

        hashes = buildHashes();
    }

    public record Acknowledgment(SubscriptionFilter notifications, Optional<Map<String, Object>> meta)
            implements Meta
    {
        public Acknowledgment
        {
            requireNonNull(notifications, "notifications is null");
            requireNonNull(meta, "meta is null");
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

            Map<Object, String> newHashes = buildHashes();
            newHashes.forEach((key, hash) -> {
                if (!hash.equals(hashes.get(key))) {
                    sendChange(key);
                }
            });
            hashes = newHashes;
        }

        CancelledNotification cancelledNotification = withSubscriptionId(CancelledNotification.class, new CancelledNotification(requestId, Optional.empty(), Optional.empty()));
        requestContext.sendMessage(NOTIFICATION_CANCELLED, Optional.of(cancelledNotification));
    }

    public record Notification(Optional<String> uri, Optional<Map<String, Object>> meta)
            implements Meta
    {
        public Notification
        {
            requireNonNull(uri, "uri is null");
            requireNonNull(meta, "meta is null");
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

    private <T extends Meta> T withSubscriptionId(Class<T> clazz, T instance)
    {
        Map<String, Object> meta = ImmutableMap.of(Constants.METADATA_SUBSCRIPTION_ID, subscriptionId.toString());
        return clazz.cast(instance.withMeta(meta));
    }

    private Map<Object, String> buildHashes()
    {
        ImmutableMap.Builder<Object, String> builder = ImmutableMap.builder();
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
            Optional<List<ResourceContents>> resourceContents = entities.readResourceContents(requestContext, new ReadResourceRequest(uri, Optional.empty()));
            String hash = resourceContents
                    .map(contents -> listHash(contents.stream()))
                    .orElse("");
            builder.put(uri, hash);
        }));
        return builder.build();
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
        try {
            return jsonMapper.writeValueAsString(item);
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
