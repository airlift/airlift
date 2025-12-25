package io.airlift.mcp.versions;

import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.airlift.mcp.model.ResourcesUpdatedNotification;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.sessions.ValueKey.SESSION_VERSIONS;
import static io.airlift.mcp.versions.VersionType.RESOURCE;
import static io.airlift.mcp.versions.Versions.DEFAULT_VERSION;
import static io.airlift.mcp.versions.Versions.EMPTY;
import static io.airlift.mcp.versions.Versions.PROMPTS_LIST_KEY;
import static io.airlift.mcp.versions.Versions.RESOURCES_LIST_KEY;
import static io.airlift.mcp.versions.Versions.TOOLS_LIST_KEY;

public class VersionUtil
{
    private static final Logger log = Logger.get(VersionUtil.class);

    private VersionUtil() {}

    public static void initializeSession(SessionController sessionController, SessionId sessionId)
    {
        Versions systemVersions = systemSessionVersions(sessionController);
        sessionController.setSessionValue(sessionId, SESSION_VERSIONS, systemVersions);
    }

    public static void unsubscribeToResource(SessionController sessionController, SessionId sessionId, String uri)
    {
        VersionKey key = new VersionKey(RESOURCE, uri);

        sessionController.computeSessionValue(sessionId, SESSION_VERSIONS, current ->
                current.map(latestRequestVersions -> latestRequestVersions.withoutKey(key)));
    }

    public static void subscribeToResource(SessionController sessionController, SessionId sessionId, String uri)
    {
        VersionKey key = new VersionKey(RESOURCE, uri);
        Versions systemVersions = systemSessionVersions(sessionController);
        String currentVersion = systemVersions.versions().getOrDefault(key, DEFAULT_VERSION);

        sessionController.computeSessionValue(sessionId, SESSION_VERSIONS, current -> {
            Versions latestRequestVersions = current.orElse(systemVersions);
            Versions updatedVersions = latestRequestVersions.withVersion(key, currentVersion);
            return Optional.of(updatedVersions);
        });
    }

    public static void reconcile(SessionController sessionController, VersionNotifier notifier, SessionId sessionId)
    {
        Versions systemVersions = systemSessionVersions(sessionController);

        sessionController.getSessionValue(sessionId, SESSION_VERSIONS)
                .ifPresentOrElse(requestVersions -> reconcile(sessionController, sessionId, notifier, systemVersions, requestVersions),
                        () -> sessionController.setSessionValue(sessionId, SESSION_VERSIONS, systemVersions));
    }

    private static void reconcile(SessionController sessionController, SessionId sessionId, VersionNotifier notifier, Versions systemVersions, Versions requestVersions)
    {
        if (changedKeys(systemVersions, requestVersions).isEmpty()) {
            return;
        }

        Set<VersionKey> changedKeys = new HashSet<>();

        sessionController.computeSessionValue(sessionId, SESSION_VERSIONS, current -> {
            Versions latestRequestVersions = current.orElse(systemVersions);

            Set<VersionKey> localChangedKeys = changedKeys(systemVersions, latestRequestVersions);
            changedKeys.addAll(localChangedKeys);

            ImmutableMap.Builder<VersionKey, String> versionsBuilder = ImmutableMap.builder();
            versionsBuilder.putAll(latestRequestVersions.versions());
            versionsBuilder.putAll(systemVersions.versions());
            return Optional.of(new Versions(versionsBuilder.buildKeepingLast()));
        });

        // NOTE: if the server crashes here before the "changedKeys.forEach" is executed
        // the session will end up in an inconsistent state where its versions are updated
        // but the client was not notified about the changes. This is acceptable as the only
        // solution is to notify the client in "valueController.compute" which will
        // be a DB transaction in most implementations.

        changedKeys.forEach(changedKey -> {
            switch (changedKey.type()) {
                case SYSTEM -> {
                    if (changedKey.equals(TOOLS_LIST_KEY)) {
                        notifier.sendNotification(NOTIFICATION_TOOLS_LIST_CHANGED, Optional.empty());
                    }
                    else if (changedKey.equals(PROMPTS_LIST_KEY)) {
                        notifier.sendNotification(NOTIFICATION_PROMPTS_LIST_CHANGED, Optional.empty());
                    }
                    else if (changedKey.equals(RESOURCES_LIST_KEY)) {
                        notifier.sendNotification(NOTIFICATION_RESOURCES_LIST_CHANGED, Optional.empty());
                    }
                    else {
                        log.warn("Unknown system session version key changed: %s", changedKey.name());
                    }
                }

                case RESOURCE -> notifier.sendNotification(NOTIFICATION_RESOURCES_UPDATED, Optional.of(new ResourcesUpdatedNotification(changedKey.name())));
            }
        });
    }

    private static Set<VersionKey> changedKeys(Versions systemVersions, Versions requestVersions)
    {
        return requestVersions.versions().entrySet().stream()
                .filter(entry -> !entry.getValue().equals(systemVersions.versions().getOrDefault(entry.getKey(), DEFAULT_VERSION)))
                .map(Map.Entry::getKey)
                .collect(toImmutableSet());
    }

    private static Versions systemSessionVersions(SessionController sessionController)
    {
        return sessionController.getSystemValue(SESSION_VERSIONS).orElse(EMPTY);
    }
}
