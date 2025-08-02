package io.airlift.mcp.internal;

import com.google.inject.Inject;
import io.airlift.mcp.session.SessionController;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.WebApplicationException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueParamProvider;

import java.util.Optional;
import java.util.function.Function;

import static io.airlift.mcp.model.Constants.SESSION_HEADER;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.util.Objects.requireNonNull;
import static org.glassfish.jersey.server.spi.internal.ValueParamProvider.Priority.HIGH;

public class SessionIdValueProvider
        implements ValueParamProvider
{
    private static final SessionId NO_SESSION = () -> "0";

    private final Optional<SessionController> sessionController;

    @Inject
    public SessionIdValueProvider(Optional<SessionController> sessionController)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
    }

    @Override
    public Function<ContainerRequest, ?> getValueProvider(Parameter parameter)
    {
        if (SessionId.class.isAssignableFrom(parameter.getRawType())) {
            return this::handler;
        }
        return null;
    }

    @Override
    public PriorityType getPriority()
    {
        return HIGH;
    }

    private SessionId handler(ContainerRequest request)
    {
        return sessionController.map(controller -> sessionIdHandler(request, controller))
                .orElse(NO_SESSION);
    }

    private SessionId sessionIdHandler(ContainerRequest request, SessionController sessionController)
    {
        String value = request.getHeaderString(SESSION_HEADER);
        if (value == null) {
            // see https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management
            throw new WebApplicationException("Invalid session", NOT_FOUND);
        }

        return sessionController.parseAndValidate(value)
                .orElseThrow(() -> {
                    // see https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management
                    return new WebApplicationException("Invalid session", NOT_FOUND);
                });
    }
}
