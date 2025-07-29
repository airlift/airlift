package io.airlift.jsonrpc.binding;

import io.airlift.jsonrpc.binding.RpcMetadata.MethodMetadata;
import jakarta.ws.rs.Produces;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static java.util.Objects.requireNonNull;

class RpcResourceBuilder
{
    private final Resource.Builder builder;
    private final String path;
    private final Map<String, MethodMetadata> methodMap;

    RpcResourceBuilder(String path, Map<String, MethodMetadata> methodMap)
    {
        this.path = normalize(requireNonNull(path, "path is null"));
        this.methodMap = requireNonNull(methodMap, "methodMap is null");    // don't copy - we want to modify the argument
        builder = Resource.builder(path);
    }

    void add(Class<?> clazz, Method javaMethod, String rpcMethod, String httpMethod, boolean isRpcResult)
    {
        rpcMethod = normalize(rpcMethod);

        String source = toSource(clazz, javaMethod);

        MethodMetadata previous = methodMap.get(rpcMethod);
        if (previous != null) {
            throw new IllegalStateException("Duplicate JSON-RPC method. Method: %s, Found at: %s, Duplicate at: %s".formatted(rpcMethod, source, previous.source()));
        }

        String methodPath = path + "/" + rpcMethod;
        methodMap.put(rpcMethod, new MethodMetadata(clazz, httpMethod, methodPath, source, isRpcResult));

        String[] produces = Optional.ofNullable(javaMethod.getAnnotation(Produces.class))
                .map(Produces::value)
                .orElseGet(() -> new String[] {APPLICATION_JSON});

        Resource.Builder childBuilder = builder.addChildResource(rpcMethod);
        ResourceMethod.Builder methodBuilder = childBuilder.addMethod(httpMethod);
        methodBuilder.produces(produces);
        methodBuilder.consumes(APPLICATION_JSON_TYPE);
        methodBuilder.handledBy(clazz, javaMethod);
    }

    Resource build()
    {
        return builder.build();
    }

    private static String toSource(Class<?> clazz, Method javaMethod)
    {
        return clazz.getName() + "#" + javaMethod.getName();
    }

    private String normalize(String path)
    {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
