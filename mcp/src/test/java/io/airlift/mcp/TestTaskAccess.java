package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.json.JsonCodecFactory;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.tasks.memory.MemoryTaskEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.mcp.TestingIdentityMapper.ANOTHER_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.model.Constants.EXTENSION_TASKS;
import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_CAPABILITIES;
import static io.airlift.mcp.model.Constants.METADATA_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METHOD_SUBSCRIPTIONS_LISTEN;
import static io.airlift.mcp.model.Constants.METHOD_TASKS_CANCEL;
import static io.airlift.mcp.model.Constants.METHOD_TASKS_GET;
import static io.airlift.mcp.model.Constants.METHOD_TASKS_UPDATE;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static io.airlift.mcp.model.TaskSupport.REQUIRED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * A task belongs to the caller that created it: to anyone else it is a task that does not exist.
 */
@TestInstance(PER_CLASS)
public class TestTaskAccess
{
    private static final String UNKNOWN_TASK_ID = "no-such-task";

    private TestingServer testingServer;
    private JsonCodecFactory jsonCodecFactory;
    private URI uri;

    @BeforeAll
    public void setup()
    {
        testingServer = new TestingServer(ImmutableMap.of(), Optional.empty(), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withTasks(binding -> binding.to(MemoryTaskEngine.class).in(SINGLETON))
                .withAllInClass(TaskTools.class)
                .build());
        jsonCodecFactory = new JsonCodecFactory(testingServer.injector().getInstance(JsonMapper.class));
        uri = testingServer.injector().getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");
    }

    @AfterAll
    public void shutdown()
    {
        testingServer.close();
    }

    public static class TaskTools
    {
        // the task is created and never run, so it stays working until something acts on it
        @McpTool(name = "idle_task", description = "test", taskSupport = REQUIRED)
        public ToolResult idleTask(McpRequestContext requestContext, CallToolRequest callToolRequest)
        {
            return requestContext.createTask(callToolRequest);
        }
    }

    @Test
    public void testAnotherCallersTaskIsNotFound()
    {
        String taskId = createTask(EXPECTED_IDENTITY);

        assertSameAsUnknown(METHOD_TASKS_GET, taskId, ImmutableMap.of());
        assertSameAsUnknown(METHOD_TASKS_UPDATE, taskId, ImmutableMap.of("inputResponses", ImmutableMap.of("confirm", ImmutableMap.of("action", "accept"))));
        assertSameAsUnknown(METHOD_TASKS_CANCEL, taskId, ImmutableMap.of());

        // the owner's task is untouched
        Map<String, Object> task = result(call(EXPECTED_IDENTITY, METHOD_TASKS_GET, ImmutableMap.of("taskId", taskId)));
        assertThat(task)
                .containsEntry("taskId", taskId)
                .containsEntry("status", "working")
                .doesNotContainKey("inputRequests");
    }

    @Test
    public void testMalformedParamsAreInvalidParams()
    {
        assertInvalidParams(METHOD_TASKS_GET, "{}", "taskId is null");
        assertInvalidParams(METHOD_TASKS_CANCEL, "{}", "taskId is null");
        assertInvalidParams(METHOD_TASKS_UPDATE, "{\"inputResponses\": {}}", "taskId is null");
        assertInvalidParams(METHOD_TASKS_UPDATE, "{\"taskId\": \"%s\", \"inputResponses\": {\"confirm\": null}}".formatted(UNKNOWN_TASK_ID), "null value");
        assertInvalidParams(METHOD_SUBSCRIPTIONS_LISTEN, "{\"notifications\": {\"taskIds\": [null]}}", "at index 0");

        // a client that did not declare the extension is told so, whatever its params
        Map<String, Object> undeclared = error(rawCall(METHOD_TASKS_GET, "{}", false));
        assertThat(undeclared).containsEntry("code", MISSING_REQUIRED_CLIENT_CAPABILITY.code());
    }

    private void assertInvalidParams(String method, String paramsJson, String reason)
    {
        Map<String, Object> error = error(rawCall(method, paramsJson, true));
        assertThat(error).containsEntry("code", INVALID_PARAMS.code());
        assertThat((String) error.get("message"))
                .startsWith("Invalid params for " + method)
                .contains(reason);
    }

    private void assertSameAsUnknown(String method, String taskId, Map<String, Object> params)
    {
        Map<String, Object> unknown = error(call(ANOTHER_IDENTITY, method, withTaskId(params, UNKNOWN_TASK_ID)));
        Map<String, Object> anothers = error(call(ANOTHER_IDENTITY, method, withTaskId(params, taskId)));

        // the same error, but for the id it names
        assertThat(anothers)
                .containsEntry("code", unknown.get("code"))
                .containsEntry("message", ((String) unknown.get("message")).replace(UNKNOWN_TASK_ID, taskId));
        assertThat(anothers.keySet()).isEqualTo(unknown.keySet());
    }

    private String createTask(String identity)
    {
        Map<String, Object> task = result(call(identity, METHOD_TOOLS_CALL, ImmutableMap.of("name", "idle_task", "arguments", ImmutableMap.of())));
        assertThat(task).containsEntry("resultType", "task");
        assertThat(task.get("taskId")).isInstanceOf(String.class);
        return (String) task.get("taskId");
    }

    private static Map<String, Object> withTaskId(Map<String, Object> params, String taskId)
    {
        return ImmutableMap.<String, Object>builder()
                .putAll(params)
                .put("taskId", taskId)
                .buildOrThrow();
    }

    private Map<String, Object> call(String identity, String method, Map<String, Object> params)
    {
        Map<String, Object> paramsWithMeta = ImmutableMap.<String, Object>builder()
                .putAll(params)
                .put("_meta", ImmutableMap.of(
                        METADATA_PROTOCOL_VERSION, PROTOCOL_MCP_2026_07_28.value(),
                        METADATA_CLIENT_CAPABILITIES, ImmutableMap.of("extensions", ImmutableMap.of(EXTENSION_TASKS, ImmutableMap.of()))))
                .buildOrThrow();

        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, method, paramsWithMeta);

        Request request = preparePost().setUri(uri)
                .addHeader(CONTENT_TYPE, "application/json")
                .addHeader(ACCEPT, "application/json")
                .addHeader(IDENTITY_HEADER, identity)
                .addHeader(HeaderName.of(HEADER_PROTOCOL_VERSION), PROTOCOL_MCP_2026_07_28.value())
                .setBodyGenerator(jsonBodyGenerator(jsonCodecFactory.jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest))
                .build();

        JsonResponse<Map<String, Object>> response = testingServer.httpClient()
                .execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.hasValue()).isTrue();
        return response.getValue();
    }

    // JSON text, so params can carry the nulls a client might send
    private Map<String, Object> rawCall(String method, String paramsJson, boolean declareTasks)
    {
        String extensions = declareTasks ? "{\"%s\": {}}".formatted(EXTENSION_TASKS) : "{}";
        String meta = "{\"%s\": \"%s\", \"%s\": {\"extensions\": %s}}".formatted(
                METADATA_PROTOCOL_VERSION, PROTOCOL_MCP_2026_07_28.value(), METADATA_CLIENT_CAPABILITIES, extensions);
        String otherParams = paramsJson.substring(1);
        String params = "{\"_meta\": %s%s%s".formatted(meta, otherParams.equals("}") ? "" : ", ", otherParams);
        String body = "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"%s\", \"params\": %s}".formatted(method, params);

        Request request = preparePost().setUri(uri)
                .addHeader(CONTENT_TYPE, "application/json")
                .addHeader(ACCEPT, "application/json")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .addHeader(HeaderName.of(HEADER_PROTOCOL_VERSION), PROTOCOL_MCP_2026_07_28.value())
                .setBodyGenerator(createStaticBodyGenerator(body, UTF_8))
                .build();

        JsonResponse<Map<String, Object>> response = testingServer.httpClient()
                .execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));
        assertThat(response.hasValue()).isTrue();
        return response.getValue();
    }

    private static Map<String, Object> result(Map<String, Object> response)
    {
        assertThat(response).doesNotContainKey("error");
        return map(response.get("result"));
    }

    private static Map<String, Object> error(Map<String, Object> response)
    {
        assertThat(response).doesNotContainKey("result");
        return map(response.get("error"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value)
    {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
