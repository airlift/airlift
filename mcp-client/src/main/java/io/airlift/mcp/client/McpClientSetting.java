package io.airlift.mcp.client;

import io.airlift.mcp.client.internal.settings.Setting;
import io.airlift.mcp.client.settings.ClientMode;
import io.airlift.mcp.client.settings.RequestCacheFactory;
import io.airlift.mcp.client.settings.SettingMap;
import io.airlift.mcp.model.LoggingLevel;

import java.time.Duration;

public abstract class McpClientSetting<V>
        extends Setting<V>
{
    public static final McpClientSetting<LoggingLevel> LOGGING_LEVEL = new McpClientSetting<>(LoggingLevel.class) {};

    public static final McpClientSetting<ClientMode> MODE = new McpClientSetting<>(ClientMode.class) {};

    public static final McpClientSetting<String> CLIENT_NAME = new McpClientSetting<>(String.class) {};

    public static final McpClientSetting<String> CLIENT_VERSION = new McpClientSetting<>(String.class) {};

    public static final McpClientSetting<Boolean> ELICITATION_ENABLED = new McpClientSetting<>(Boolean.class) {};

    public static final McpClientSetting<SettingMap> EXPERIMENTAL = new McpClientSetting<>(SettingMap.class) {};

    public static final McpClientSetting<SettingMap> EXTENSIONS = new McpClientSetting<>(SettingMap.class) {};

    public static final McpClientSetting<Duration> MIN_TASK_SERVICE_PERIOD = new McpClientSetting<>(Duration.class) {};

    public static final McpClientSetting<Duration> MAX_TASK_SERVICE_PERIOD = new McpClientSetting<>(Duration.class) {};

    public static final McpClientSetting<RequestCacheFactory> REQUEST_CACHE_FACTORY = new McpClientSetting<>(RequestCacheFactory.class) {};

    protected McpClientSetting(Class<V> valueType)
    {
        super(valueType);
    }
}
