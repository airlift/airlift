package io.airlift.mcp.versions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourcesUpdatedNotification;
import io.airlift.mcp.model.SubscribeRequest;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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
import static java.util.Objects.requireNonNull;

public class VersionsController
{
    private static final Logger log = Logger.get(VersionsController.class);

    private final Optional<SessionController> sessionController;
    private final McpServer mcpServer;
    private final ObjectMapper objectMapper;

    @Inject
    public VersionsController(Optional<SessionController> sessionController, McpServer mcpServer, ObjectMapper objectMapper)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    public void initializeSessionVersions(SessionId sessionId)
    {
        sessionController.ifPresent(controller -> controller.setSessionValue(sessionId, SYSTEM_LIST_VERSIONS, buildSystemListVersions()));
    }

    public void resourcesSubscribe(SessionId sessionId, McpRequestContext requestContext, SubscribeRequest subscribeRequest)
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> exception(INVALID_REQUEST, "Sessions are not enabled"));

        List<ResourceContents> resourceContents = mcpServer.readResourceContents(requestContext, new ReadResourceRequest(subscribeRequest.uri(), subscribeRequest.meta()))
                .orElseThrow(() -> exception(RESOURCE_NOT_FOUND, "Resource not found: " + subscribeRequest.uri()));

        String hash = listHash(resourceContents.stream());

        localSessionController.computeSessionValue(sessionId, RESOURCE_VERSIONS, currentValue -> {
            ResourceVersions resourceVersions = currentValue.orElseGet(() -> new ResourceVersions(ImmutableMap.of()));
            ResourceVersions updatedVersions = resourceVersions.with(subscribeRequest.uri(), hash);
            return Optional.of(updatedVersions);
        });
    }

    public void resourcesUnsubscribe(SessionId sessionId, String uri)
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> exception(INVALID_REQUEST, "Sessions are not enabled"));

        localSessionController.computeSessionValue(sessionId, RESOURCE_VERSIONS, currentValue -> {
            ResourceVersions resourceVersions = currentValue.orElseGet(() -> new ResourceVersions(ImmutableMap.of()));
            ResourceVersions updatedVersions = resourceVersions.without(uri);
            return Optional.of(updatedVersions);
        });
    }

    private record Notification(String message, Optional<Object> params) {}

    public void reconcileVersions(SessionId sessionId, McpRequestContext requestContext)
    {
        sessionController.ifPresent(controller -> {
            List<Notification> notifications = new ArrayList<>();

            reconcileSystemListVersions(controller, sessionId, notifications);
            reconcileResourceSubscriptions(controller, requestContext, sessionId, notifications);

            // ideally, we'd send these messages before updating the session state, but that would require
            // sending them inside of a DB transaction and that isn't ideal. So, we send them after updating the session state.
            // There is a small chance that these messages fail to send and the client will miss the notifications.
            notifications.forEach(notification -> requestContext.sendMessage(notification.message, notification.params));
        });
    }

    private void reconcileSystemListVersions(SessionController sessionController, SessionId sessionId, List<Notification> notifications)
    {
        SystemListVersions currentSystemListVersions = buildSystemListVersions();
        Optional<SystemListVersions> sessionSystemListVersions = sessionController.getSessionValue(sessionId, SYSTEM_LIST_VERSIONS);

        // pre-check against current value which should be cached in memory
        if (!sessionSystemListVersions.map(currentSystemListVersions::equals).orElse(false)) {
            sessionController.computeSessionValue(sessionId, SYSTEM_LIST_VERSIONS, maybePreviousVersions -> {
                SystemListVersions previousVersions = maybePreviousVersions.orElseGet(() -> {
                    log.warn("No current versions found for session %s", sessionId);
                    return buildSystemListVersions();
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

    private void reconcileResourceSubscriptions(SessionController sessionController, McpRequestContext requestContext, SessionId sessionId, List<Notification> notifications)
    {
        // TODO this is somewhat expensive. In the future consider adding some hooks that clients can use to optimize this.
        sessionController.computeSessionValue(sessionId, RESOURCE_VERSIONS, currentValue -> currentValue.map(resourceVersions -> {
            Map<String, String> updatedUriToVersion = resourceVersions.uriToVersion().entrySet()
                    .stream()
                    .map(entry -> {
                        String uri = entry.getKey();
                        String oldHash = entry.getValue();

                        Optional<List<ResourceContents>> resourceContents = mcpServer.readResourceContents(requestContext, new ReadResourceRequest(uri, Optional.empty()));
                        String newHash = resourceContents
                                .map(contents -> listHash(contents.stream()))
                                .orElse("");

                        if (!oldHash.equals(newHash)) {
                            notifications.add(new Notification(NOTIFICATION_RESOURCES_UPDATED, Optional.of(new ResourcesUpdatedNotification(uri))));
                            return Map.entry(uri, newHash);
                        }

                        return entry;
                    })
                    .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
            return new ResourceVersions(updatedUriToVersion);
        }));
    }

    private SystemListVersions buildSystemListVersions()
    {
        return new SystemListVersions(
                listHash(mcpServer.tools().stream()),
                listHash(mcpServer.prompts().stream()),
                listHash(mcpServer.resources().stream()),
                listHash(mcpServer.resourceTemplates().stream()));
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
            return objectMapper.writeValueAsString(item);
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
