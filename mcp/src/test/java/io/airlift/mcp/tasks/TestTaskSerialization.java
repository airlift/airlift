package io.airlift.mcp.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.jackson.JacksonSubType;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.ResultType;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskNotification;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.model.UpdateTaskRequest;
import io.airlift.mcp.operations.ResultTypeWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.McpJacksonSubTypes.buildJacksonSubType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The wire shapes of the Tasks extension. See <a href="https://github.com/modelcontextprotocol/ext-tasks">ext-tasks</a>.
 */
public class TestTaskSerialization
{
    private static final String CREATED_AT = "2026-09-13T10:30:00Z";
    private static final String LAST_UPDATED_AT = "2026-09-13T10:40:00Z";

    private final JsonMapper jsonMapper;

    public TestTaskSerialization()
    {
        JacksonSubType jacksonSubType = buildJacksonSubType();
        jsonMapper = new JsonMapperProvider()
                .withJacksonSubTypes(ImmutableSet.of(jacksonSubType))
                .get();
    }

    @Test
    public void testCreateTaskResult()
    {
        // a created task is flat: resultType and the task's fields at the top level, no nested "task"
        JsonNode json = toJson(new ResultTypeWrapper(ResultType.TASK, workingTask()));

        assertThat(json.get("resultType").asText()).isEqualTo("task");
        assertThat(json.get("taskId").asText()).isEqualTo("the-task");
        assertThat(json.get("status").asText()).isEqualTo("working");
        assertThat(json.get("createdAt").asText()).isEqualTo(CREATED_AT);
        assertThat(json.get("lastUpdatedAt").asText()).isEqualTo(LAST_UPDATED_AT);
        assertThat(json.get("ttlMs").asLong()).isEqualTo(60_000);
        assertThat(json.get("pollIntervalMs").asLong()).isEqualTo(5_000);
        assertThat(json.has("task")).isFalse();

        // the fields the 2025-11-25 tasks utility used are not on this wire
        assertThat(json.has("ttl")).isFalse();
        assertThat(json.has("pollInterval")).isFalse();
        assertThat(json.has("requestState")).isFalse();
    }

    @Test
    public void testUnlimitedTtlIsNull()
    {
        // the extension requires ttlMs and spells "unlimited" as null, so it cannot be left out
        Task unlimited = new Task("the-task", TaskStatus.WORKING, Optional.empty(), CREATED_AT, LAST_UPDATED_AT, OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        JsonNode json = toJson(new ResultTypeWrapper(ResultType.COMPLETE, unlimited));

        assertThat(json.has("ttlMs")).isTrue();
        assertThat(json.get("ttlMs").isNull()).isTrue();

        // pollIntervalMs is optional, so an absent one stays absent
        assertThat(json.has("pollIntervalMs")).isFalse();
    }

    @Test
    public void testGetTaskResult()
    {
        // tasks/get is a standard result - the task is the payload, not the discriminator
        JsonNode working = toJson(new ResultTypeWrapper(ResultType.COMPLETE, workingTask()));
        assertThat(working.get("resultType").asText()).isEqualTo("complete");
        assertThat(working.get("status").asText()).isEqualTo("working");
        assertThat(working.has("result")).isFalse();
        assertThat(working.has("error")).isFalse();
        assertThat(working.has("inputRequests")).isFalse();

        JsonNode completed = toJson(new ResultTypeWrapper(ResultType.COMPLETE, completedTask()));
        assertThat(completed.get("status").asText()).isEqualTo("completed");
        assertThat(completed.get("result").get("content").get(0).get("text").asText()).isEqualTo("It worked");
        assertThat(completed.get("result").has("requestState")).isFalse();

        JsonNode failed = toJson(new ResultTypeWrapper(ResultType.COMPLETE, failedTask()));
        assertThat(failed.get("status").asText()).isEqualTo("failed");
        assertThat(failed.get("error").get("code").asInt()).isEqualTo(INTERNAL_ERROR.code());
        assertThat(failed.get("error").get("message").asText()).isEqualTo("Didn't work");
        assertThat(failed.has("result")).isFalse();

        JsonNode inputRequired = toJson(new ResultTypeWrapper(ResultType.COMPLETE, inputRequiredTask()));
        assertThat(inputRequired.get("status").asText()).isEqualTo("input_required");
        assertThat(inputRequired.get("inputRequests").get("confirm").get("method").asText()).isEqualTo(METHOD_ELICITATION_CREATE);
    }

    @Test
    public void testTaskNotification()
    {
        JsonNode json = toJson(new TaskNotification(completedTask(), Optional.empty()));

        assertThat(json.get("taskId").asText()).isEqualTo("the-task");
        assertThat(json.get("status").asText()).isEqualTo("completed");
        assertThat(json.get("result").get("content").get(0).get("text").asText()).isEqualTo("It worked");
    }

    @Test
    public void testUpdateTaskRequestKeepsAbsentApartFromEmpty()
    {
        // the extension requires inputResponses, so tasks/update without it must be told apart from one answering nothing
        UpdateTaskRequest absent = jsonMapper.convertValue(ImmutableMap.of("taskId", "the-task"), UpdateTaskRequest.class);
        assertThat(absent.inputResponses()).isEmpty();

        UpdateTaskRequest empty = jsonMapper.convertValue(ImmutableMap.of("taskId", "the-task", "inputResponses", ImmutableMap.of()), UpdateTaskRequest.class);
        assertThat(empty.inputResponses()).contains(ImmutableMap.of());
    }

    @Test
    public void testStatusPayloadIsValidated()
    {
        assertThatThrownBy(() -> new Task("the-task", TaskStatus.COMPLETED, Optional.empty(), CREATED_AT, LAST_UPDATED_AT, OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMPLETED tasks must have a result");

        assertThatThrownBy(() -> new Task("the-task", TaskStatus.WORKING, Optional.empty(), CREATED_AT, LAST_UPDATED_AT, OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), Optional.of(new CallToolResult(new TextContent("It worked"))), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WORKING tasks cannot have a result");
    }

    private static Task workingTask()
    {
        return new Task("the-task", TaskStatus.WORKING, Optional.empty(), CREATED_AT, LAST_UPDATED_AT, OptionalLong.of(60_000), OptionalLong.of(5_000), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Task completedTask()
    {
        CallToolResult result = new CallToolResult(List.of(new TextContent("It worked")), Optional.empty(), false);
        return new Task("the-task", TaskStatus.COMPLETED, Optional.empty(), CREATED_AT, LAST_UPDATED_AT, OptionalLong.of(60_000), OptionalLong.of(5_000), Optional.empty(), Optional.of(result), Optional.empty());
    }

    private static Task failedTask()
    {
        JsonRpcErrorDetail error = new JsonRpcErrorDetail(INTERNAL_ERROR, "Didn't work");
        return new Task("the-task", TaskStatus.FAILED, Optional.of("Didn't work"), CREATED_AT, LAST_UPDATED_AT, OptionalLong.of(60_000), OptionalLong.of(5_000), Optional.empty(), Optional.empty(), Optional.of(error));
    }

    private static Task inputRequiredTask()
    {
        InputRequest inputRequest = new InputRequest(METHOD_ELICITATION_CREATE, ImmutableMap.of("message", "Are you sure?"));
        return new Task("the-task", TaskStatus.INPUT_REQUIRED, Optional.empty(), CREATED_AT, LAST_UPDATED_AT, OptionalLong.of(60_000), OptionalLong.of(5_000), Optional.of(ImmutableMap.of("confirm", inputRequest)), Optional.empty(), Optional.empty());
    }

    private JsonNode toJson(Object value)
    {
        return jsonMapper.valueToTree(value);
    }
}
