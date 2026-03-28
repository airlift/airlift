package io.airlift.mcp.testing;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.HeaderName;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpIdentityMapper;
import jakarta.servlet.http.HttpServletRequest;

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
    public static final String ANOTHER_IDENTITY = "Mrs. Other Tester";
    public static final String ERRORED_IDENTITY = "Bad Actor";
    public static final HeaderName IDENTITY_HEADER = HeaderName.of("X-Testing-Identity");

    @Override
    public McpIdentity map(HttpServletRequest request)
    {
        String authHeader = request.getHeader(IDENTITY_HEADER.toString());
        if (isNullOrEmpty(authHeader)) {
            return unauthenticated("Empty or missing identity header", ImmutableList.of(IDENTITY_HEADER.toString()));
        }
        if (authHeader.equals(ERRORED_IDENTITY)) {
            return error(exception(INTERNAL_ERROR, "This identity cannot catch a break"));
        }
        if (!authHeader.equals(EXPECTED_IDENTITY) && !authHeader.equals(ANOTHER_IDENTITY)) {
            return unauthorized("Identity %s is not authorized to access".formatted(authHeader));
        }
        return authenticated(new TestingIdentity(authHeader));
    }
}
