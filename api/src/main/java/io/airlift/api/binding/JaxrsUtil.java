package io.airlift.api.binding;

import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServices;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.ResourceMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

public class JaxrsUtil
{
    private static final Map<Method, Optional<ModelMethod>> cache = new ConcurrentHashMap<>();
    private static final Map<ModelMethod, Optional<ModelService>> methodToServiceMap = new ConcurrentHashMap<>();

    private JaxrsUtil() {}

    public static boolean isApiService(ContainerRequestContext requestContext)
    {
        return findApiResourceMethod(requestContext).isPresent();
    }

    public static boolean isApiResource(Type type)
    {
        return (type instanceof Class<?> clazz) && isApiResource(clazz);
    }

    public static boolean isApiResource(Class<?> clazz)
    {
        return (clazz.getAnnotation(ApiResource.class) != null) || (clazz.getAnnotation(ApiPolyResource.class) != null);
    }

    public static Optional<ModelMethod> findApiServiceMethod(ContainerRequestContext requestContext, ModelServices modelServices)
    {
        return findApiResourceMethod(requestContext)
                .stream()
                .flatMap(resourceMethod -> findApiService(resourceMethod, modelServices).stream())
                .findFirst();
    }

    public static Optional<ApiServiceWithMethod> findApiServiceWithMethod(ContainerRequestContext requestContext, ModelServices modelServices)
    {
        return findApiServiceMethod(requestContext, modelServices)
                .flatMap(modelMethod -> findMethodToServiceAssociation(modelServices, modelMethod)
                        .map(modelService -> new ApiServiceWithMethod(modelService, modelMethod)));
    }

    public static Optional<ModelMethod> findApiServiceFromMethod(ResourceMethod resourceMethod, ModelServices modelServices)
    {
        return findApiService(resourceMethod, modelServices);
    }

    static Optional<ResourceMethod> findApiResourceMethod(ContainerRequestContext requestContext)
    {
        ResourceMethod resourceMethod = ((ContainerRequest) requestContext).getUriInfo().getMatchedResourceMethod();
        if (((resourceMethod != null) && (resourceMethod.getInvocable() != null) && (resourceMethod.getInvocable().getHandler().getHandlerClass().getDeclaredAnnotation(ApiService.class) != null))) {
            return Optional.of(resourceMethod);
        }
        return Optional.empty();
    }

    private static Optional<ModelService> findMethodToServiceAssociation(ModelServices modelServices, ModelMethod modelMethod)
    {
        return methodToServiceMap.computeIfAbsent(modelMethod, method -> modelServices.services().stream()
                .filter(modelService -> modelService.methods().contains(method)).findFirst());
    }

    private static Optional<ModelMethod> findApiService(ResourceMethod resourceMethod, ModelServices modelServices)
    {
        Method handlingMethod = resourceMethod.getInvocable().getHandlingMethod();

        return cache.computeIfAbsent(handlingMethod, ignore -> modelServices.services()
                .stream()
                .flatMap(modelService -> findApiService(handlingMethod, modelService).stream())
                .findFirst());
    }

    private static Optional<ModelMethod> findApiService(Method handlingMethod, ModelService modelService)
    {
        return modelService.methods()
                .stream()
                .filter(modelMethod -> modelMethod.method().equals(handlingMethod))
                .findFirst();
    }

    public record ApiServiceWithMethod(ModelService service, ModelMethod method)
    {
        public ApiServiceWithMethod
        {
            requireNonNull(service, "service is null");
            requireNonNull(method, "method is null");
        }
    }
}
