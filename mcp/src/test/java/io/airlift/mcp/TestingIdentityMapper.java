package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.tasks.TaskContextId;
import io.airlift.mcp.tasks.TaskController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.McpIdentity.Error.error;
import static io.airlift.mcp.McpIdentity.Unauthenticated.unauthenticated;
import static io.airlift.mcp.McpIdentity.Unauthorized.unauthorized;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;

public class TestingIdentityMapper
        implements McpIdentityMapper
{
    public static final String EXPECTED_IDENTITY = "Mr. Tester";
    public static final String ERRORED_IDENTITY = "Bad Actor";
    public static final String IDENTITY_HEADER = "X-Testing-Identity";

    private final Optional<TaskContextId> taskContextId;

    @Inject
    public TestingIdentityMapper(TaskController taskController, Optional<SessionController> sessionController)
    {
        taskContextId = sessionController.map(_ -> taskController.newTaskContextId(Optional.empty()));
    }

    @Override
    public McpIdentity map(HttpServletRequest request, Optional<SessionId> sessionId)
    {
        String authHeader = request.getHeader(IDENTITY_HEADER);
        if (isNullOrEmpty(authHeader)) {
            return unauthenticated("Empty or missing identity header", ImmutableList.of(IDENTITY_HEADER));
        }
        if (authHeader.equals(ERRORED_IDENTITY)) {
            return error(exception(INTERNAL_ERROR, "This identity cannot catch a break"));
        }
        if (!authHeader.equals(EXPECTED_IDENTITY)) {
            return unauthorized("Identity %s is not authorized to access".formatted(authHeader));
        }
        return authenticated(new TestingIdentity(EXPECTED_IDENTITY), taskContextId);
    }
}
