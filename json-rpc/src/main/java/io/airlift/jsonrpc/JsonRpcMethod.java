package io.airlift.jsonrpc;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.util.Objects.requireNonNull;

public record JsonRpcMethod(Class<?> clazz, Method javaMethod, String rpcMethod, String httpMethod)
{
    public JsonRpcMethod
    {
        requireNonNull(clazz, "clazz is null");
        requireNonNull(javaMethod, "javaMethod is null");
        requireNonNull(rpcMethod, "rpcMethod is null");
        requireNonNull(httpMethod, "httpMethod is null");
    }

    public static <T extends JsonRpcModule.Builder<?>> T addAllInClass(T builder, Class<?> clazz)
    {
        inClass(clazz).forEach(builder::add);
        return builder;
    }

    public static List<JsonRpcMethod> inClass(Class<?> clazz)
    {
        return Stream.of(clazz.getMethods())
                .flatMap(method -> {
                    JsonRpc jsonRpc = method.getAnnotation(JsonRpc.class);
                    if (jsonRpc != null) {
                        String value = jsonRpc.value();
                        String rpcMethod = value.isEmpty() ? httpPath(method).orElseThrow(() -> missingPathException(clazz, method)) : value;
                        String httpMethod = httpMethod(method).orElse(POST);
                        return Stream.of(new JsonRpcMethod(clazz, method, rpcMethod, httpMethod));
                    }
                    return Stream.empty();
                })
                .collect(toImmutableList());
    }

    private static Optional<String> httpPath(Method method)
    {
        return Optional.ofNullable(method.getAnnotation(Path.class)).map(Path::value);
    }

    private static Optional<String> httpMethod(Method method)
    {
        return Stream.of(method.getAnnotations())
                .flatMap(annotation -> Optional.ofNullable(annotation.annotationType().getAnnotation(HttpMethod.class)).map(HttpMethod::value).stream())
                .findFirst();
    }

    private static RuntimeException missingPathException(Class<?> clazz, Method method)
    {
        return new IllegalStateException("%s's %s annotation does not have a method attribute and the method does not have a %s annotation"
                .formatted(clazz.getName() + "#" + method.getName(), JsonRpc.class.getSimpleName(), Path.class.getSimpleName()));
    }
}
