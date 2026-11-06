package io.airlift.mcp.client.internal;

import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.HttpClient;
import io.airlift.mcp.client.McpClient;
import io.airlift.mcp.client.McpClientSetting;
import io.airlift.mcp.client.McpConnection;
import io.airlift.mcp.client.McpConnectionSetting;
import io.airlift.mcp.client.McpTasksClient;
import io.airlift.mcp.client.McpTasksConnection;
import io.airlift.mcp.client.internal.legacy.NullLegacyElicitationHandler;
import io.airlift.mcp.client.internal.settings.Setting;
import io.airlift.mcp.client.internal.settings.SettingContainer;
import io.airlift.mcp.client.settings.ClientMode;
import io.airlift.mcp.client.settings.MetaOnly;
import io.airlift.mcp.client.settings.ProgressToken;
import io.airlift.mcp.client.settings.SettingMap;
import io.airlift.mcp.client.settings.StandardExceptionMapper;
import io.airlift.mcp.client.settings.StandardRequestCache;
import io.airlift.mcp.model.LoggingLevel;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.client.McpClientSetting.CLIENT_NAME;
import static io.airlift.mcp.client.McpClientSetting.CLIENT_VERSION;
import static io.airlift.mcp.client.McpClientSetting.ELICITATION_ENABLED;
import static io.airlift.mcp.client.McpClientSetting.EXPERIMENTAL;
import static io.airlift.mcp.client.McpClientSetting.EXTENSIONS;
import static io.airlift.mcp.client.McpClientSetting.LOGGING_LEVEL;
import static io.airlift.mcp.client.McpClientSetting.MAX_TASK_SERVICE_PERIOD;
import static io.airlift.mcp.client.McpClientSetting.MIN_TASK_SERVICE_PERIOD;
import static io.airlift.mcp.client.McpClientSetting.MODE;
import static io.airlift.mcp.client.McpClientSetting.REQUEST_CACHE_FACTORY;
import static io.airlift.mcp.client.McpConnectionSetting.EXCEPTION_MAPPER;
import static io.airlift.mcp.client.McpConnectionSetting.LEGACY_ELICITATION_HANDLER;
import static io.airlift.mcp.client.McpConnectionSetting.MAX_INPUT_REQUEST_ROUNDS;
import static io.airlift.mcp.client.McpConnectionSetting.META;
import static io.airlift.mcp.client.McpConnectionSetting.NOTIFICATION_CONSUMER;
import static io.airlift.mcp.client.McpConnectionSetting.PROGRESS_TOKEN;
import static io.airlift.mcp.client.McpConnectionSetting.REQUEST_FILTER;
import static io.airlift.mcp.client.McpConnectionSetting.RESPONSE_FILTER;
import static io.airlift.mcp.client.internal.InternalConnection.createInternalConnection;
import static io.airlift.mcp.client.internal.legacy.LegacyConnection.createLegacyConnection;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalConnection.createLegacyOptionalConnection;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_ONLY;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_OPTIONAL;
import static io.airlift.mcp.model.LoggingLevel.INFO;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.util.Objects.requireNonNull;

public class InternalClient
        implements McpClient
{
    private static final LoggingLevel INITIAL_LOGGING_LEVEL = INFO;
    private static final Duration DEFAULT_MIN_TASK_SERVICE_PERIOD = Duration.ofSeconds(10);
    private static final Duration DEFAULT_MAX_TASK_SERVICE_PERIOD = Duration.ofMinutes(1);

    private final HttpClient httpClient;
    private final SettingContainer settingContainer;

    public InternalClient(HttpClient httpClient)
    {
        this(buildSettingContainer(), httpClient);
    }

    private InternalClient(SettingContainer settingContainer, HttpClient httpClient)
    {
        this.settingContainer = requireNonNull(settingContainer, "settingContainer is null");
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
    }

    @Override
    public <V> V setting(McpClientSetting<V> setting)
    {
        return settingContainer.getSettingValue(setting);
    }

    @Override
    public <V> McpClient withSetting(McpClientSetting<V> setting, V value)
    {
        return new InternalClient(settingContainer.with(setting, value), httpClient);
    }

    @Override
    public <V> V defaultConnectionSetting(McpConnectionSetting<V> setting)
    {
        return settingContainer.getSettingValue(setting);
    }

    @Override
    public <V> McpClient withDefaultConnectionSetting(McpConnectionSetting<V> setting, V value)
    {
        return new InternalClient(settingContainer.with(setting, value), httpClient);
    }

    @Override
    public McpConnection connect(URI uri)
    {
        return internalConnect(uri, false);
    }

    @Override
    public McpTasksClient withTasks()
    {
        ClientMode clientMode = settingContainer.getSettingValue(MODE);
        if (clientMode == LEGACY_PROTOCOL_ONLY) {
            throw new IllegalStateException("Task support is not available for LEGACY_PROTOCOL_ONLY");
        }
        return uri -> internalConnect(uri, true);
    }

    private McpTasksConnection internalConnect(URI uri, boolean tasksEnabled)
    {
        return switch (settingContainer.getSettingValue(MODE)) {
            case LEGACY_PROTOCOL_ONLY -> createLegacyConnection(this, uri, settingContainer);
            case LEGACY_PROTOCOL_OPTIONAL -> createLegacyOptionalConnection(this, uri, settingContainer, tasksEnabled);
            case LEGACY_PROTOCOL_DISABLED -> createInternalConnection(this, uri, settingContainer, tasksEnabled);
        };
    }

    @Override
    public HttpClient httpClient()
    {
        return httpClient;
    }

    @SuppressWarnings("unchecked")
    private static SettingContainer buildSettingContainer()
    {
        SettingContainer settingContainer = SettingContainer.create().with(CLIENT_NAME, "MCP Client")
                .with(CLIENT_VERSION, "1.0.0")
                .with(ELICITATION_ENABLED, false)
                .with(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .with(EXPERIMENTAL, new SettingMap(ImmutableMap.of()))
                .with(EXTENSIONS, new SettingMap(ImmutableMap.of()))
                .with(NOTIFICATION_CONSUMER, (_, _, _) -> {})
                .with(RESPONSE_FILTER, (_, r) -> r)
                .with(REQUEST_FILTER, b -> b)
                .with(LOGGING_LEVEL, INITIAL_LOGGING_LEVEL)
                .with(PROGRESS_TOKEN, new ProgressToken())
                .with(LEGACY_ELICITATION_HANDLER, new NullLegacyElicitationHandler())
                .with(REQUEST_CACHE_FACTORY, uri -> StandardRequestCache.builder(uri).build())
                .with(MIN_TASK_SERVICE_PERIOD, DEFAULT_MIN_TASK_SERVICE_PERIOD)
                .with(MAX_TASK_SERVICE_PERIOD, DEFAULT_MAX_TASK_SERVICE_PERIOD)
                .with(EXCEPTION_MAPPER, new StandardExceptionMapper())
                .with(MAX_INPUT_REQUEST_ROUNDS, 10)
                .with(META, new MetaOnly(Optional.empty()));

        validateComplete(settingContainer, McpClientSetting.class);
        validateComplete(settingContainer, McpConnectionSetting.class);

        return settingContainer;
    }

    private static <T extends Setting<T>> void validateComplete(SettingContainer settingContainer, Class<T> keyClass)
    {
        getInstances(keyClass).forEach((name, instance) -> {
            try {
                settingContainer.getSettingValue(instance);
            }
            catch (IllegalArgumentException e) {
                throw new RuntimeException(name + " does not have an initial setting", e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> getInstances(Class<? extends T> keyClass)
    {
        Map<String, T> result = new HashMap<>();
        for (Field f : keyClass.getFields()) {
            int m = f.getModifiers();
            if (isPublic(m) && isStatic(m) && isFinal(m) && keyClass.isAssignableFrom(f.getType())) {
                try {
                    result.put(f.getName(), (T) f.get(null));
                }
                catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return result;
    }
}
