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
    String METHOD_COMPLETION_COMPLETE = "completion/complete";

    String NOTIFICATION_PROGRESS = "notifications/progress";
    String NOTIFICATION_MESSAGE = "notifications/message";
    String NOTIFICATION_INITIALIZED = "notifications/initialized";
}
