package io.airlift.mcp.client;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpTaskController;
import io.airlift.mcp.McpTool;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.ElicitRequestForm;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonSchemaBuilder;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskHandlerResult.TaskFailed;
import io.airlift.mcp.model.ToolResult;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.TaskErrorState.FAILED;
import static java.util.Objects.requireNonNull;

public class TestingTaskEndpoints
{
    private final JsonMapper jsonMapper;
    private final McpTaskController taskController;

    @Inject
    public TestingTaskEndpoints(JsonMapper jsonMapper, McpTaskController taskController)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.taskController = requireNonNull(taskController, "taskController is null");
    }

    @McpTool(name = "task_add", description = "Add two numbers via a task")
    public ToolResult taskAdd(McpRequestContext requestContext, int a, int b)
    {
        Task task = requestContext.createTask();
        taskController.executeCancelable(task.taskId(), () -> {
            TimeUnit.MILLISECONDS.sleep(100);
            return new CallToolResult(new TextContent(String.valueOf(a + b)));
        });
        return task;
    }

    public record Confirmation(boolean confirm) {}

    @McpTool(name = "task_confirm", description = "Ask for confirmation via a task")
    public ToolResult taskConfirm(McpRequestContext requestContext, String filename)
    {
        Task task = requestContext.createTask();
        taskController.executeCancelable(task.taskId(), () -> {
            ObjectNode schema = new JsonSchemaBuilder().build(Optional.empty(), Confirmation.class);
            ElicitRequestForm elicitRequestForm = new ElicitRequestForm("Are you sure you want to delete " + filename + "?", schema);
            CallToolResult requests = CallToolResult.inputRequestsBuilder()
                    .add("confirm", METHOD_ELICITATION_CREATE, elicitRequestForm)
                    .build();
            taskController.setResult(task.taskId(), Optional.of(requests), Optional.empty());

            if (!taskController.awaitInputResponses(task.taskId(), Duration.ofSeconds(10), ImmutableSet.of("confirm"))) {
                throw new RuntimeException("Timed out waiting for input responses");
            }

            ElicitResult elicitResult = taskController.currentInputResponses(task.taskId())
                    .getInputResponse("confirm")
                    .map(value -> jsonMapper.convertValue(value, ElicitResult.class))
                    .orElseThrow();
            boolean confirmed = (elicitResult.action() == ElicitResult.Action.ACCEPT)
                    && elicitResult.mapContent(jsonMapper, Confirmation.class).orElseThrow().confirm();
            return new CallToolResult(new TextContent(confirmed ? "Deleted " + filename : "Deletion cancelled"));
        });
        return task;
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

    @McpTool(name = "task_endless_input", description = "Asks for more input forever via a task")
    public ToolResult taskEndlessInput(McpRequestContext requestContext)
    {
        Task task = requestContext.createTask();
        taskController.executeCancelable(task.taskId(), () -> {
            ObjectNode schema = new JsonSchemaBuilder().build(Optional.empty(), Confirmation.class);
            for (int round = 0; round < 100; round++) {
                String key = "confirm-" + round;
                CallToolResult requests = CallToolResult.inputRequestsBuilder()
                        .add(key, METHOD_ELICITATION_CREATE, new ElicitRequestForm("Again?", schema))
                        .build();
                taskController.setResult(task.taskId(), Optional.of(requests), Optional.empty());

                if (!taskController.awaitInputResponses(task.taskId(), Duration.ofSeconds(10), ImmutableSet.of(key))) {
                    throw new RuntimeException("Timed out waiting for input responses");
                }
            }
            return new CallToolResult(new TextContent("Unreachable - the client gives up long before 100 rounds"));
        });
        return task;
    }

    @McpTool(name = "task_fail", description = "Fail via a task")
    public ToolResult taskFail(McpRequestContext requestContext)
    {
        Task task = requestContext.createTask();
        taskController.executeCancelable(task.taskId(), () -> {
            TimeUnit.MILLISECONDS.sleep(100);
            return new TaskFailed(FAILED, Optional.of(new JsonRpcErrorDetail(INTERNAL_ERROR, "task failed as requested")));
        });
        return task;
    }
}
