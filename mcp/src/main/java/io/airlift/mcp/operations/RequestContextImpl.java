package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpTasks;
import io.airlift.mcp.messages.MessageWriter;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskSupport;
import io.airlift.mcp.tasks.TaskExecutor;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.mcp.McpException.clientCapabilityError;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.ACCEPTS_STREAMING_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.EXTENSION_TASKS;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static java.lang.Boolean.FALSE;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

class RequestContextImpl
        implements McpRequestContext
{
    private final HttpServletRequest request;
    private final RequestMetadata requestMetadata;
    private final JsonMapper jsonMapper;
    private final MessageWriter messageWriter;
    private final Authenticated<?> identity;
    private final Optional<McpTasks> tasks;
    private final Duration taskTtl;
    private final Duration taskPollInterval;
    private final TaskSupport taskSupport;

    RequestContextImpl(
            HttpServletRequest request,
            RequestMetadata requestMetadata,
            JsonMapper jsonMapper,
            MessageWriter messageWriter,
            Authenticated<?> identity,
            Optional<McpTasks> tasks,
            Duration taskTtl,
            Duration taskPollInterval)
    {
        // only a tool that declares it may create a task - nothing else handled for the request can
        this(request, requestMetadata, jsonMapper, messageWriter, identity, tasks, taskTtl, taskPollInterval, TaskSupport.NONE);
    }

    private RequestContextImpl(
            HttpServletRequest request,
            RequestMetadata requestMetadata,
            JsonMapper jsonMapper,
            MessageWriter messageWriter,
            Authenticated<?> identity,
            Optional<McpTasks> tasks,
            Duration taskTtl,
            Duration taskPollInterval,
            TaskSupport taskSupport)
    {
        this.request = requireNonNull(request, "request is null");
        this.requestMetadata = requireNonNull(requestMetadata, "requestMetadata is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.messageWriter = requireNonNull(messageWriter, "messageWriter is null");
        this.identity = requireNonNull(identity, "identity is null");
        this.tasks = requireNonNull(tasks, "tasks is null");
        this.taskTtl = requireNonNull(taskTtl, "taskTtl is null");
        this.taskPollInterval = requireNonNull(taskPollInterval, "taskPollInterval is null");
        this.taskSupport = requireNonNull(taskSupport, "taskSupport is null");
    }

    RequestContextImpl withTaskSupport(TaskSupport taskSupport)
    {
        return new RequestContextImpl(request, requestMetadata, jsonMapper, messageWriter, identity, tasks, taskTtl, taskPollInterval, taskSupport);
    }

    @Override
    public Task createTask(CallToolRequest callToolRequest)
    {
        McpTasks mcpTasks = requireTasks(callToolRequest);

        return mcpTasks.createTask(this, callToolRequest, taskTtl, taskPollInterval);
    }

    @Override
    public Task createAndExecuteTask(CallToolRequest callToolRequest, TaskExecutor executor)
    {
        McpTasks mcpTasks = requireTasks(callToolRequest);

        Task task = mcpTasks.createTask(this, callToolRequest, taskTtl, taskPollInterval);
        mcpTasks.executeTask(task.taskId(), executor);

        return task;
    }

    @Override
    public HttpServletRequest request()
    {
        return request;
    }

    @Override
    public Authenticated<?> identity()
    {
        return identity;
    }

    @Override
    public void sendProgress(double progress, double total, String message)
    {
        if (!acceptsStreaming()) {
            return;
        }

        Optional<Object> appliedProgressToken = requestMetadata.progressToken().map(token -> switch (token) {
            case Number n -> Optional.of(n.longValue());
            default -> requestMetadata.progressToken();
        });

        ProgressNotification notification = new ProgressNotification(appliedProgressToken, message, OptionalDouble.of(progress), OptionalDouble.of(total));
        sendMessage(NOTIFICATION_PROGRESS, Optional.of(notification));
    }

    @Override
    public void sendMessage(String method, Optional<Object> params)
    {
        checkState(acceptsStreaming(), "Streaming is not supported by client connection");

        JsonRpcRequest<?> notification = params.map(p -> buildNotification(method, p)).orElseGet(() -> buildNotification(method));
        internalSendRequest(notification);
    }

    @Override
    public ClientCapabilities clientCapabilities()
    {
        return requestMetadata.clientCapabilities();
    }

    @Override
    public void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data)
    {
        if (!acceptsStreaming()) {
            return;
        }

        requestMetadata.loggingLevel().ifPresent(requestLoggingLevel -> {
            if (level.level() >= requestLoggingLevel.level()) {
                LoggingMessageNotification logNotification = new LoggingMessageNotification(level, logger, data);
                sendMessage(NOTIFICATION_MESSAGE, Optional.of(logNotification));
            }
        });
    }

    private McpTasks requireTasks(CallToolRequest callToolRequest)
    {
        McpTasks mcpTasks = tasks.orElseThrow(() -> new UnsupportedOperationException("Tasks are not supported by this server"));

        // checked first so that an undeclared tool fails for every client, not only those that negotiated the extension
        checkState(taskSupport != TaskSupport.NONE, "Tool %s is declared with taskSupport %s and cannot create a task", callToolRequest.name(), TaskSupport.NONE);

        // the extension forbids answering a request that did not declare it with a task - reject it before a task is stored or run
        if (!requestMetadata.clientCapabilities().hasExtension(EXTENSION_TASKS)) {
            throw clientCapabilityError(ClientCapabilities.requiredExtension(EXTENSION_TASKS));
        }

        return mcpTasks;
    }

    private boolean acceptsStreaming()
    {
        return requireNonNullElse((Boolean) request.getAttribute(ACCEPTS_STREAMING_ATTRIBUTE), FALSE);
    }

    private void internalSendRequest(JsonRpcRequest<?> rpcRequest)
    {
        try {
            String json = jsonMapper.writeValueAsString(rpcRequest);
            messageWriter.writeMessage(json);
            messageWriter.flush();
        }
        catch (IOException e) {
            throw exception(e);
        }
    }
}
