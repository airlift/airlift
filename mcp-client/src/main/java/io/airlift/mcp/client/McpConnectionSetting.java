package io.airlift.mcp.client;

import io.airlift.mcp.client.internal.settings.Setting;
import io.airlift.mcp.client.settings.ExceptionMapper;
import io.airlift.mcp.client.settings.LegacyElicitationHandler;
import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.client.settings.ProgressToken;
import io.airlift.mcp.client.settings.RequestFilter;
import io.airlift.mcp.client.settings.ResponseFilter;

public abstract class McpConnectionSetting<V>
        extends Setting<V>
{
    public static final McpConnectionSetting<NotificationConsumer> NOTIFICATION_CONSUMER = new McpConnectionSetting<>(NotificationConsumer.class) {};

    public static final McpConnectionSetting<ProgressToken> PROGRESS_TOKEN = new McpConnectionSetting<>(ProgressToken.class) {};

    public static final McpConnectionSetting<RequestFilter> REQUEST_FILTER = new McpConnectionSetting<>(RequestFilter.class) {};

    public static final McpConnectionSetting<ResponseFilter> RESPONSE_FILTER = new McpConnectionSetting<>(ResponseFilter.class) {};

    public static final McpConnectionSetting<LegacyElicitationHandler> LEGACY_ELICITATION_HANDLER = new McpConnectionSetting<>(LegacyElicitationHandler.class) {};

    public static final McpConnectionSetting<ExceptionMapper> EXCEPTION_MAPPER = new McpConnectionSetting<>(ExceptionMapper.class) {};

    public static final McpConnectionSetting<Integer> MAX_INPUT_REQUEST_ROUNDS = new McpConnectionSetting<>(Integer.class) {};

    protected McpConnectionSetting(Class<V> valueType)
    {
        super(valueType);
    }
}
