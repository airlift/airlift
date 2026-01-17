package io.airlift.mcp.tasks;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.Meta;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.PROGRESS_TOKEN;
import static io.airlift.mcp.model.Constants.RPC_REQUEST_ID_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.TASK_CONTEXT_ID_ATTRIBUTE;
import static java.util.Objects.requireNonNull;

public class NewTask
{
    private final TaskContextId taskContextId;
    private final Object requestId;
    private final Map<String, String> attributes;
    private final Optional<Object> progressToken;
    private final OptionalInt pollInterval;
    private final OptionalInt ttl;

    private NewTask(TaskContextId taskContextId, Object requestId, Map<String, String> attributes, Optional<Object> progressToken, OptionalInt pollInterval, OptionalInt ttl)
    {
        this.taskContextId = requireNonNull(taskContextId, "taskContextId is null");
        this.requestId = requireNonNull(requestId, "requestId is null");
        this.attributes = ImmutableMap.copyOf(attributes);
        this.progressToken = requireNonNull(progressToken, "progressToken is null");
        this.pollInterval = requireNonNull(pollInterval, "pollInterval is null");
        this.ttl = requireNonNull(ttl, "ttl is null");
    }

    public OptionalInt ttl()
    {
        return ttl;
    }

    public OptionalInt pollInterval()
    {
        return pollInterval;
    }

    public Optional<Object> progressToken()
    {
        return progressToken;
    }

    public Map<String, String> attributes()
    {
        return attributes;
    }

    public Object requestId()
    {
        return requestId;
    }

    public TaskContextId taskContextId()
    {
        return taskContextId;
    }

    public interface Builder
    {
        Builder addAttribute(String key, String value);

        Builder withMeta(Meta meta);

        Builder withPollInterval(int pollIntervalMs);

        Builder withTtlInterval(int ttlMs);

        NewTask build();
    }

    public static Builder builder(HttpServletRequest request)
    {
        TaskContextId taskContextId = (TaskContextId) Optional.ofNullable(request.getAttribute(TASK_CONTEXT_ID_ATTRIBUTE)).orElseThrow(() -> exception("task context id not set in request"));
        Object requestId = Optional.ofNullable(request.getAttribute(RPC_REQUEST_ID_ATTRIBUTE)).orElseThrow(() -> exception("request id not set in request"));

        return builder(taskContextId, requestId);
    }

    public static Builder builder(TaskContextId taskContextId, Object requestId)
    {
        requireNonNull(requestId, "requestId is null");

        return new Builder()
        {
            private final ImmutableMap.Builder<String, String> attributes = ImmutableMap.builder();
            private Optional<Object> progressToken = Optional.empty();
            private OptionalInt pollInterval = OptionalInt.empty();
            private OptionalInt ttl = OptionalInt.empty();

            @Override
            public Builder addAttribute(String key, String value)
            {
                attributes.put(key, value);
                return this;
            }

            @Override
            public Builder withMeta(Meta meta)
            {
                this.progressToken = meta.meta().flatMap(m -> Optional.ofNullable(m.get(PROGRESS_TOKEN)));
                return this;
            }

            @Override
            public Builder withPollInterval(int pollIntervalMs)
            {
                pollInterval = OptionalInt.of(pollIntervalMs);
                return this;
            }

            @Override
            public Builder withTtlInterval(int ttlMs)
            {
                ttl = OptionalInt.of(ttlMs);
                return this;
            }

            @Override
            public NewTask build()
            {
                return new NewTask(taskContextId, requestId, attributes.build(), progressToken, pollInterval, ttl);
            }
        };
    }
}
