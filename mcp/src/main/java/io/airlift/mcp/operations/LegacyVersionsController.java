package io.airlift.mcp.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourcesUpdatedNotification;
import io.airlift.mcp.model.SubscribeRequest;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.google.common.base.Throwables.getRootCause;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.RESOURCE_NOT_FOUND;
import static io.airlift.mcp.sessions.SessionValueKey.RESOURCE_VERSIONS;
import static io.airlift.mcp.sessions.SessionValueKey.SYSTEM_LIST_VERSIONS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

class LegacyVersionsController
{
    private static final Logger log = Logger.get(LegacyVersionsController.class);

    private final McpEntities entities;
    private final JsonMapper jsonMapper;
    private final Cache<String, String> resourceVersionsCache;

    @Inject
    LegacyVersionsController(McpEntities entities, JsonMapper jsonMapper, McpConfig mcpConfig)
    {
        this.entities = requireNonNull(entities, "entities is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");

        resourceVersionsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(mcpConfig.getResourceSubscriptionCachePeriod().toJavaTime())
                .build();
    }

    void initializeSessionVersions(RequestContextImpl requestContext)
    {
        if (requestContext.session().isValid()) {
            requestContext.session().setValue(SYSTEM_LIST_VERSIONS, buildSystemListVersions(requestContext));
        }
    }

    void resourcesSubscribe(RequestContextImpl requestContext, SubscribeRequest subscribeRequest)
    {
        String version = readResourceVersion(requestContext, subscribeRequest.uri(), true);

        if (!(requestContext instanceof RequestContextImpl internalRequestContext) || !internalRequestContext.session().isValid()) {
            throw exception(INVALID_REQUEST, "Invalid session - cannot subscribe to resource updates");
        }

        internalRequestContext.session().computeValue(RESOURCE_VERSIONS, currentValue -> {
            ResourceVersions resourceVersions = currentValue.orElseGet(() -> new ResourceVersions(ImmutableMap.of()));
            ResourceVersions updatedVersions = resourceVersions.with(subscribeRequest.uri(), version);
            return Optional.of(updatedVersions);
        });
    }

    void resourcesUnsubscribe(RequestContextImpl requestContext, String uri)
    {
        if (!requestContext.session().isValid()) {
            throw exception(INVALID_REQUEST, "Invalid session - cannot unsubscribe to resource updates");
        }

        requestContext.session().computeValue(RESOURCE_VERSIONS, currentValue -> {
            ResourceVersions resourceVersions = currentValue.orElseGet(() -> new ResourceVersions(ImmutableMap.of()));
            ResourceVersions updatedVersions = resourceVersions.without(uri);
            return Optional.of(updatedVersions);
        });
    }

    private record Notification(String message, Optional<Object> params) {}

    void reconcileVersions(RequestContextImpl requestContext)
    {
        if (!requestContext.session().isValid()) {
            return;
        }

        List<Notification> notifications = new ArrayList<>();

        reconcileSystemListVersions(requestContext, notifications);
        reconcileResourceSubscriptions(requestContext, notifications);

        // ideally, we'd send these messages before updating the session state, but that would require
        // sending them inside of a DB transaction and that isn't ideal. So, we send them after updating the session state.
        // There is a small chance that these messages fail to send and the client will miss the notifications.
        notifications.forEach(notification -> requestContext.sendMessage(notification.message, notification.params));
    }

    private void reconcileSystemListVersions(RequestContextImpl requestContext, List<Notification> notifications)
    {
        // pre-check against current value which should be cached in memory via CachingSessionController
        SystemListVersions currentSystemListVersions = buildSystemListVersions(requestContext);
        Optional<SystemListVersions> sessionSystemListVersions = requestContext.session().getValue(SYSTEM_LIST_VERSIONS);
        boolean precheckHasChanges = !sessionSystemListVersions.map(currentSystemListVersions::equals).orElse(false);

        if (precheckHasChanges) {
            // now do it for real - this will also flush/reset the in-memory cached value in CachingSessionController
            requestContext.session().computeValue(SYSTEM_LIST_VERSIONS, maybePreviousVersions -> {
                SystemListVersions previousVersions = maybePreviousVersions.orElseGet(() -> {
                    log.warn("No current versions found for session %s", requestContext.session().sessionId());
                    return buildSystemListVersions(requestContext);
                });

                if (!previousVersions.toolsVersion().equals(currentSystemListVersions.toolsVersion())) {
                    notifications.add(new Notification(NOTIFICATION_TOOLS_LIST_CHANGED, Optional.empty()));
                }
                if (!previousVersions.promptsVersion().equals(currentSystemListVersions.promptsVersion())) {
                    notifications.add(new Notification(NOTIFICATION_PROMPTS_LIST_CHANGED, Optional.empty()));
                }
                if (!previousVersions.resourcesVersion().equals(currentSystemListVersions.resourcesVersion()) || !previousVersions.resourceTemplatesVersion().equals(currentSystemListVersions.resourceTemplatesVersion())) {
                    notifications.add(new Notification(NOTIFICATION_RESOURCES_LIST_CHANGED, Optional.empty()));
                }

                return Optional.of(currentSystemListVersions);
            });
        }
    }

    private void reconcileResourceSubscriptions(RequestContextImpl requestContext, List<Notification> notifications)
    {
        // will likely be cached/in-memory via CachingSessionController
        Map<String, String> currentSubscriptions = requestContext.session().getValue(RESOURCE_VERSIONS)
                .map(ResourceVersions::uriToVersion)
                .orElseGet(ImmutableMap::of);

        // first, check likely cached/in-memory values to see if there are any changes
        boolean precheckHasChanges = currentSubscriptions.entrySet()
                .stream()
                .anyMatch(entry -> {
                    String uri = entry.getKey();
                    String oldVersion = entry.getValue();
                    String newVersion = readResourceVersion(requestContext, uri, false);
                    return !oldVersion.equals(newVersion);
                });

        if (precheckHasChanges) {
            // now do it for real - this will also flush/reset the in-memory cached value in CachingSessionController
            requestContext.session().computeValue(RESOURCE_VERSIONS, currentValue -> currentValue.map(resourceVersions -> {
                Map<String, String> updatedUriToVersion = resourceVersions.uriToVersion().entrySet()
                        .stream()
                        .map(entry -> {
                            String uri = entry.getKey();
                            String oldVersion = entry.getValue();
                            String newVersion = readResourceVersion(requestContext, uri, false);

                            if (!oldVersion.equals(newVersion)) {
                                notifications.add(new Notification(NOTIFICATION_RESOURCES_UPDATED, Optional.of(new ResourcesUpdatedNotification(uri))));
                            }

                            return entry(uri, newVersion);
                        })
                        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
                return new ResourceVersions(updatedUriToVersion);
            }));
        }
    }

    private String readResourceVersion(McpRequestContext requestContext, String uri, boolean required)
    {
        try {
            return resourceVersionsCache.get(uri, () -> {
                Optional<List<ResourceContents>> resourceContents = entities.readResourceContents(requestContext, new ReadResourceRequest(uri, Optional.empty()));
                if (required && resourceContents.isEmpty()) {
                    throw exception(RESOURCE_NOT_FOUND, "Resource not found: " + uri);
                }
                return resourceContents
                        .map(contents -> listHash(contents.stream()))
                        .orElse("");
            });
        }
        catch (ExecutionException e) {
            if (getRootCause(e) instanceof McpException mcpException) {
                throw mcpException;
            }
            throw new UncheckedExecutionException(e);
        }
    }

    private SystemListVersions buildSystemListVersions(McpRequestContext requestContext)
    {
        return new SystemListVersions(
                listHash(entities.tools(requestContext).stream()),
                listHash(entities.prompts(requestContext).stream()),
                listHash(entities.resources(requestContext).stream()),
                listHash(entities.resourceTemplates(requestContext).stream()));
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
