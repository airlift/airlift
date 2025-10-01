package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.McpIdentity;
import jakarta.servlet.http.HttpServletRequest;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.TestMcp.IDENTITY_HEADER;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.McpIdentity.Error.error;
import static io.airlift.mcp.model.McpIdentity.Unauthenticated.unauthenticated;
import static io.airlift.mcp.model.McpIdentity.Unauthorized.unauthorized;

public class TestingIdentityMapper
        implements McpIdentityMapper
{
    public static final String EXPECTED_IDENTITY = "Mr. Tester";
    public static final String ERRORED_IDENTITY = "Bad Actor";

    @Override
    public McpIdentity map(HttpServletRequest request)
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
        return authenticated(new TestingIdentity(EXPECTED_IDENTITY));
    }
}
