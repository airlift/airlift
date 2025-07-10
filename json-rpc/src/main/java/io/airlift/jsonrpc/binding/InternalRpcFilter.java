package io.airlift.jsonrpc.binding;

import com.google.inject.Inject;
import io.airlift.jsonrpc.binding.RpcMetadata.MethodMetadata;
import io.airlift.jsonrpc.model.JsonRpcErrorCode;
import io.airlift.jsonrpc.model.JsonRpcErrorDetail;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Optional;

import static io.airlift.jsonrpc.model.JsonRpcErrorCode.METHOD_NOT_FOUND;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.util.Objects.requireNonNull;

@Priority(0)
@PreMatching
public class InternalRpcFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    private final InternalRpcHelper rpcHelper;

    @Inject
    InternalRpcFilter(InternalRpcHelper rpcHelper)
    {
        this.rpcHelper = requireNonNull(rpcHelper, "jsonRpcObjects is null");
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        if (!rpcHelper.isPotentialJsonRpc(requestContext)) {
            return;
        }

        RpcMetadata rpcMetadata = rpcHelper.jsonRpcMetadata();
        InternalRequest internalRequest = rpcHelper.buildRequest(requestContext.getEntityStream());

        MethodMetadata methodMetadata = rpcMetadata.methodMap().get(internalRequest.method());
        if (methodMetadata == null) {
            Object error = rpcHelper.rpcError(internalRequest.id(), METHOD_NOT_FOUND, "No method found for JSON-RPC request: " + internalRequest.method(), Optional.empty());
            requestContext.abortWith(Response.status(OK).type(APPLICATION_JSON_TYPE).entity(error).build());
            return;
        }

        requestContext.setProperty(InternalRpcFilter.class.getName(), internalRequest);

        URI methodUri = UriBuilder.fromUri(requestContext.getUriInfo().getRequestUri())
                .replacePath(methodMetadata.methodPath())
                .build();
        requestContext.setRequestUri(methodUri);
        requestContext.setMethod(methodMetadata.httpMethod());
        requestContext.setEntityStream(internalRequest.payload().orElseGet(EmptyInputStream::new));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
    {
        jsonRpcRequest(requestContext).ifPresent(internalRequest -> finalizeJsonRpcResponse(responseContext, internalRequest));
    }

    public static Optional<Object> requestId(ContainerRequestContext requestContext)
    {
        return jsonRpcRequest(requestContext).map(InternalRequest::id);
    }

    static Optional<InternalRequest> jsonRpcRequest(ContainerRequestContext requestContext)
    {
        return Optional.ofNullable((InternalRequest) requestContext.getProperty(InternalRpcFilter.class.getName()));
    }

    private void finalizeJsonRpcResponse(ContainerResponseContext responseContext, InternalRequest internalRequest)
    {
        if (responseContext.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            if (responseContext.hasEntity()) {
                setResponse(responseContext, rpcHelper.rpcResponse(internalRequest.id(), responseContext.getEntity()));
            }
            else {
                setResponse(responseContext, rpcHelper.rpcResponse(internalRequest.id(), null));
            }
        }
        else {
            if (responseContext.hasEntity() && (responseContext.getEntity() instanceof JsonRpcErrorDetail(int errorCode, String message, Optional<Object> data))) {
                setResponse(responseContext, rpcHelper.rpcError(internalRequest.id(), errorCode, message, data));
            }
            else {
                JsonRpcErrorCode.fromCode(responseContext.getStatusInfo().getStatusCode()).ifPresentOrElse(
                        errorCode -> setResponse(responseContext, rpcHelper.rpcError(internalRequest.id(), errorCode, responseContext.getStatusInfo().getReasonPhrase(), Optional.empty())),
                        () -> setResponse(responseContext, rpcHelper.rpcError(internalRequest.id(), responseContext.getStatusInfo().getStatusCode(), responseContext.getStatusInfo().getReasonPhrase(), Optional.empty())));
            }
        }

        responseContext.setStatus(OK.getStatusCode());
    }

    private static void setResponse(ContainerResponseContext responseContext, Object response)
    {
        responseContext.setEntity(response, new Annotation[0], APPLICATION_JSON_TYPE);
    }

    private static class EmptyInputStream
            extends InputStream
    {
        @Override
        public int available()
        {
            return 0;
        }

        public int read()
        {
            return -1;
        }
    }
}
