package io.airlift.api.builders;

import io.airlift.api.ApiService;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.validation.ValidatorException;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class ServiceBuilder
{
    private final Class<?> serviceClass;
    private final Map<Class<?>, ModelService> builtServices;
    private final Function<Method, MethodBuilder> methodBuilder;

    private ServiceBuilder(Class<?> serviceClass, Map<Class<?>, ModelService> builtServices, Function<Method, MethodBuilder> methodBuilder)
    {
        this.serviceClass = requireNonNull(serviceClass, "clazz is null");
        this.builtServices = requireNonNull(builtServices, "builtServices is null");    // don't copy
        this.methodBuilder = requireNonNull(methodBuilder, "methodBuilder is null");
    }

    public static ServiceBuilder serviceBuilder(Class<?> serviceClass)
    {
        return new ServiceBuilder(serviceClass, new HashMap<>(), MethodBuilder::methodBuilder);
    }

    public static ServiceBuilder serviceBuilder(Class<?> serviceClass, Function<Method, MethodBuilder> methodSupplier)
    {
        return new ServiceBuilder(serviceClass, new HashMap<>(), methodSupplier);
    }

    public static ServiceBuilder serviceBuilder(Class<?> serviceClass, ServiceBuilder from)
    {
        return new ServiceBuilder(serviceClass, from.builtServices, from.methodBuilder);
    }

    public ServiceBuilder toBuilder(Class<?> serviceClass)
    {
        return serviceBuilder(serviceClass, this);
    }

    public ModelService build()
    {
        return internalBuildAndAdd();
    }

    private ModelService internalBuildAndAdd()
    {
        ModelService modelService = internalBuild();
        builtServices.put(serviceClass, modelService);
        return modelService;
    }

    private ModelService internalBuild()
    {
        ModelService modelService = builtServices.get(serviceClass);
        if (modelService != null) {
            return modelService;
        }

        ApiService apiService = serviceClass.getAnnotation(ApiService.class);
        if (apiService == null) {
            throw new ValidatorException("%s is not annotated with @ApiService".formatted(serviceClass));
        }

        ModelServiceMetadata metadata = ModelServiceMetadata.map(apiService);
        List<ModelMethod> methods = Stream.of(serviceClass.getDeclaredMethods())
                .flatMap(method -> methodBuilder.apply(method).build().stream())
                .collect(toImmutableList());
        return new ModelService(metadata, serviceClass, methods);
    }
}
