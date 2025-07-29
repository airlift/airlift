package io.airlift.mcp.handler;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;

import java.util.List;
import java.util.Map;

public interface ResourceTemplateHandler
{
    record PathTemplateValues(Map<String, String> values)
    {
        public PathTemplateValues
        {
            values = ImmutableMap.copyOf(values);
        }
    }

    List<ResourceContents> readResource(Request request, SessionId sessionId, McpNotifier notifier, ResourceTemplate sourceResourceTemplate, ReadResourceRequest readResourceRequest, PathTemplateValues pathTemplateValues);
}
