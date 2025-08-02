package io.airlift.mcp.model;

public interface Constants
{
    String PROTOCOL_VERSION = "2025-06-18";

    String METHOD_CALL_TOOL = "tools/call";
    String METHOD_GET_PROMPT = "prompts/get";
    String METHOD_READ_RESOURCES = "resources/read";
    String METHOD_PING = "ping";
    String METHOD_INITIALIZE = "initialize";
    String METHOD_TOOLS_LIST = "tools/list";
    String METHOD_PROMPTS_LIST = "prompts/list";
    String METHOD_RESOURCES_LIST = "resources/list";
    String METHOD_RESOURCES_TEMPLATES_LIST = "resources/templates/list";
    String METHOD_RESOURCES_SUBSCRIBE = "resources/subscribe";
    String METHOD_RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
    String METHOD_COMPLETION_COMPLETE = "completion/complete";
    String METHOD_SET_LOGGING_LEVEL = "logging/setLevel";
    String METHOD_ROOTS_LIST = "roots/list";

    String NOTIFICATION_PROGRESS = "notifications/progress";
    String NOTIFICATION_MESSAGE = "notifications/message";
    String NOTIFICATION_INITIALIZED = "notifications/initialized";
    String NOTIFICATION_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
    String NOTIFICATION_PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";
    String NOTIFICATION_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
    String NOTIFICATION_RESOURCES_UPDATED = "notifications/resources/updated";
    String NOTIFICATION_ROOTS_CHANGED = "notifications/roots/list_changed";
    String NOTIFICATION_CANCELLED = "notifications/cancelled";

    String SESSION_HEADER = "Mcp-Session-Id";
}
