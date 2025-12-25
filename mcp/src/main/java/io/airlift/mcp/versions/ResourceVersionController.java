package io.airlift.mcp.versions;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.airlift.log.Logger;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.internal.InternalMcpServer;
import io.airlift.mcp.model.ResourcesUpdatedNotification;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.sessions.SessionValueKey.SYSTEM_LIST_VERSIONS;
import static io.airlift.mcp.sessions.SessionValueKey.resourceVersionKey;
import static io.airlift.mcp.versions.ResourceVersion.DEFAULT_VERSION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ResourceVersionController
{
    private static final Logger log = Logger.get(ResourceVersionController.class);
    private static final int PAGE_SIZE = 100;

    private final Provider<InternalMcpServer> mcpServer;
    private final Optional<SessionController> sessionController;
    private final Duration updateInterval;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(daemonThreadsNamed("resource-version-updater"));
    private final Map<String, String> resourceHashes = new ConcurrentHashMap<>();

    @Inject
    public ResourceVersionController(Provider<InternalMcpServer> mcpServer, Optional<SessionController> sessionController, McpConfig mcpConfig)
    {
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");

        updateInterval = mcpConfig.getResourceVersionUpdateInterval().toJavaTime();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static <T> String listHash(Stream<? extends T> stream)
    {
        Hasher hasher = Hashing.sha256().newHasher();
        stream.forEach(item -> hasher.putString(item.toString(), UTF_8));
        return hasher.hash().toString();
    }

    @PostConstruct
    public void start()
    {
        sessionController.ifPresent(_ ->
                executorService.scheduleWithFixedDelay(this::internalUpdateResourceVersions, 0, updateInterval.toMillis(), MILLISECONDS));
    }

    @PreDestroy
    public void stop()
    {
        if (!shutdownAndAwaitTermination(executorService, 10, SECONDS)) {
            log.warn("Resource version updater did not shut down cleanly");
        }
    }

    public Optional<String> resourceVersion(String uri)
    {
        return Optional.ofNullable(resourceHashes.get(uri));
    }

    public void reconcile(BiConsumer<String, Optional<Object>> sendMessageProc, SessionId sessionId, SystemListVersions systemVersions)
    {
        sessionController.ifPresent(controller -> internalReconcile(controller, sendMessageProc, sessionId, systemVersions));
    }

    private void internalReconcile(SessionController sessionController, BiConsumer<String, Optional<Object>> sendMessageProc, SessionId sessionId, SystemListVersions systemVersions)
    {
        SystemListVersions sessionSystemListVersions = sessionController.getSessionValue(sessionId, SYSTEM_LIST_VERSIONS).orElse(SystemListVersions.DEFAULT);
        if (!sessionSystemListVersions.equals(systemVersions)) {
            if (!sessionSystemListVersions.toolsVersion().equals(systemVersions.toolsVersion())) {
                sendMessageProc.accept(NOTIFICATION_TOOLS_LIST_CHANGED, Optional.empty());
            }
            if (!sessionSystemListVersions.promptsVersion().equals(systemVersions.promptsVersion())) {
                sendMessageProc.accept(NOTIFICATION_PROMPTS_LIST_CHANGED, Optional.empty());
            }
            if (!sessionSystemListVersions.resourcesVersion().equals(systemVersions.resourcesVersion()) || !sessionSystemListVersions.resourceTemplatesVersion().equals(systemVersions.resourceTemplatesVersion())) {
                sendMessageProc.accept(NOTIFICATION_RESOURCES_LIST_CHANGED, Optional.empty());
            }
            sessionController.setSessionValue(sessionId, SYSTEM_LIST_VERSIONS, systemVersions);
        }

        // iterate over any subscribed resources and notify if updated
        Optional<String> cursor = Optional.empty();
        do {
            List<Map.Entry<String, ResourceVersion>> subscriptions = sessionController.listSessionValues(sessionId, ResourceVersion.class, PAGE_SIZE, cursor);
            subscriptions.forEach(subscription -> {
                String uri = subscription.getKey();
                SessionValueKey<ResourceVersion> key = resourceVersionKey(uri);
                String lastKnownVersionHash = resourceHashes.getOrDefault(uri, DEFAULT_VERSION);

                if (!lastKnownVersionHash.equals(subscription.getValue().version())) {
                    sendMessageProc.accept(NOTIFICATION_RESOURCES_UPDATED, Optional.of(new ResourcesUpdatedNotification(uri)));
                    sessionController.setSessionValue(sessionId, key, new ResourceVersion(lastKnownVersionHash));
                }
            });

            cursor = (subscriptions.size() < PAGE_SIZE) ? Optional.empty() : Optional.of(subscriptions.getLast().getKey());
        }
        while (cursor.isPresent());
    }

    private void internalUpdateResourceVersions()
    {
        InternalMcpServer internalMcpServer = mcpServer.get();

        internalMcpServer.streamResources().forEach(resource -> {
            String hash = internalMcpServer.readResources(resource.uri())
                    .map(resources -> listHash(resources.stream()))
                    .orElse("");
            resourceHashes.put(resource.uri(), hash);
        });
    }
}
