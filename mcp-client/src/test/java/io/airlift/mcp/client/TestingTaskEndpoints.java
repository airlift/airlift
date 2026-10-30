package io.airlift.mcp.client;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpTool;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.ElicitRequestForm;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.model.TaskResult;
import io.airlift.mcp.model.ToolResult;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.TaskSupport.REQUIRED;
import static java.util.Objects.requireNonNull;

public class TestingTaskEndpoints
{
    private static final Duration INPUT_TIMEOUT = Duration.ofSeconds(10);

    private final JsonMapper jsonMapper;

    @Inject
    public TestingTaskEndpoints(JsonMapper jsonMapper)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
    }

    @McpTool(name = "task_add", description = "Add two numbers via a task", taskSupport = REQUIRED)
    public ToolResult taskAdd(McpRequestContext requestContext, CallToolRequest callToolRequest, int a, int b)
    {
        return requestContext.createAndExecuteTask(callToolRequest, (_, _, _) -> {
            TimeUnit.MILLISECONDS.sleep(100);
            return new CallToolResult(new TextContent(String.valueOf(a + b)));
        });
    }

    public record Confirmation(boolean confirm) {}

    @McpTool(name = "task_confirm", description = "Ask for confirmation via a task", taskSupport = REQUIRED)
    public ToolResult taskConfirm(McpRequestContext requestContext, CallToolRequest callToolRequest, String filename)
    {
        return requestContext.createAndExecuteTask(callToolRequest, (_, _, taskContext) -> {
            ObjectNode schema = new JsonSchemaBuilder().build(Optional.empty(), Confirmation.class);
            ElicitRequestForm elicitRequestForm = new ElicitRequestForm("Are you sure you want to delete " + filename + "?", schema);

            ElicitResult elicitResult = taskContext.awaitInputResponse("confirm", METHOD_ELICITATION_CREATE, elicitRequestForm, INPUT_TIMEOUT)
                    .map(value -> jsonMapper.convertValue(value, ElicitResult.class))
                    .orElseThrow(() -> new RuntimeException("Timed out waiting for input responses"));
            boolean confirmed = (elicitResult.action() == ElicitResult.Action.ACCEPT)
                    && elicitResult.mapContent(jsonMapper, Confirmation.class).orElseThrow().confirm();
            return new CallToolResult(new TextContent(confirmed ? "Deleted " + filename : "Deletion cancelled"));
        });
    }

    @McpTool(name = "endless_input", description = "Always asks for more input")
    public CallToolResult endlessInput()
    {
        ObjectNode schema = new JsonSchemaBuilder().build(Optional.empty(), Confirmation.class);
        ElicitRequestForm elicitRequestForm = new ElicitRequestForm("Again?", schema);
        return CallToolResult.inputRequestsBuilder()
                .add("again", METHOD_ELICITATION_CREATE, elicitRequestForm)
                .build();
    }

    @McpTool(name = "task_endless_input", description = "Asks for more input forever via a task", taskSupport = REQUIRED)
    public ToolResult taskEndlessInput(McpRequestContext requestContext, CallToolRequest callToolRequest)
    {
        return requestContext.createAndExecuteTask(callToolRequest, (_, _, taskContext) -> {
            ObjectNode schema = new JsonSchemaBuilder().build(Optional.empty(), Confirmation.class);
            for (int round = 0; round < 100; round++) {
                if (taskContext.awaitInputResponse("confirm-" + round, METHOD_ELICITATION_CREATE, new ElicitRequestForm("Again?", schema), INPUT_TIMEOUT).isEmpty()) {
                    throw new RuntimeException("Timed out waiting for input responses");
                }
            }
            return new CallToolResult(new TextContent("Unreachable - the client gives up long before 100 rounds"));
        });
    }

    @McpTool(name = "task_fail", description = "Fail via a task", taskSupport = REQUIRED)
    public ToolResult taskFail(McpRequestContext requestContext, CallToolRequest callToolRequest)
    {
        return requestContext.createAndExecuteTask(callToolRequest, (_, _, _) -> {
            TimeUnit.MILLISECONDS.sleep(100);
            return new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "task failed as requested"));
        });
    }
}
