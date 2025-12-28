package io.airlift.mcp.model;

public interface Constants
{
    String JSON_RPC_VERSION = "2.0";

    String MCP_SESSION_ID = "Mcp-Session-Id";

    String METHOD_INITIALIZE = "initialize";
    String METHOD_PING = "ping";
    String METHOD_TOOLS_LIST = "tools/list";
    String METHOD_TOOLS_CALL = "tools/call";
    String METHOD_RESOURCES_LIST = "resources/list";
    String METHOD_RESOURCES_READ = "resources/read";
    String METHOD_RESOURCES_TEMPLATES_LIST = "resources/templates/list";
    String METHOD_RESOURCES_SUBSCRIBE = "resources/subscribe";
    String METHOD_RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
    String METHOD_PROMPT_LIST = "prompts/list";
    String METHOD_PROMPT_GET = "prompts/get";
    String METHOD_COMPLETION_COMPLETE = "completion/complete";
    String METHOD_LOGGING_SET_LEVEL = "logging/setLevel";
    String METHOD_ROOTS_LIST = "roots/list";
    String METHOD_SAMPLING_CREATE_MESSAGE = "sampling/createMessage";
    String METHOD_ELICITATION_CREATE = "elicitation/create";

    String NOTIFICATION_INITIALIZED = "notifications/initialized";
    String NOTIFICATION_PROGRESS = "notifications/progress";
    String NOTIFICATION_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
    String NOTIFICATION_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
    String NOTIFICATION_RESOURCES_UPDATED = "notifications/resources/updated";
    String NOTIFICATION_PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";
    String NOTIFICATION_MESSAGE = "notifications/message";
    String NOTIFICATION_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";
    String NOTIFICATION_CANCELLED = "notifications/cancelled";

    String HEADER_SESSION_ID = "Mcp-Session-Id";
    String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version";

    String MCP_IDENTITY_ATTRIBUTE = Constants.class.getName() + ".identity";
    String HTTP_RESPONSE_ATTRIBUTE = Constants.class.getName() + ".response";
}
