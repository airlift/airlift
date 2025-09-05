package io.airlift.api.builders;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.model.ModelDeprecation;
import io.airlift.api.model.ModelResponse;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServices;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.api.builders.DeprecationBuilder.buildDeprecations;
import static io.airlift.api.builders.MethodBuilder.methodBuilder;
import static java.util.Objects.requireNonNull;

public class ServicesBuilder
{
    private final Function<Class<?>, ServiceBuilder> serviceBuilder;
    private final ImmutableSet.Builder<ModelService> services;

    private ServicesBuilder(Function<Class<?>, ServiceBuilder> serviceBuilder)
    {
        this.serviceBuilder = requireNonNull(serviceBuilder, "serviceBuilder is null");

        services = ImmutableSet.builder();
    }

    public static ServicesBuilder servicesBuilder()
    {
        Function<Type, ResourceBuilder> resourceBuilder = createThunk(ResourceBuilder::resourceBuilder, ((type, builder) -> builder.toBuilder(type)));
        Function<Method, MethodBuilder> methodBuilder = createThunk(method -> methodBuilder(method, resourceBuilder), ((method, builder) -> builder.toBuilder(method)));
        Function<Class<?>, ServiceBuilder> serviceBuilder = createThunk(clazz -> ServiceBuilder.serviceBuilder(clazz, methodBuilder), (serviceClass, builder) -> builder.toBuilder(serviceClass));

        return new ServicesBuilder(serviceBuilder);
    }

    public ServicesBuilder add(Class<?> serviceClass)
    {
        ModelService service = serviceBuilder.apply(serviceClass).build();
        services.add(service);

        return this;
    }

    public ModelServices build()
    {
        Set<ModelService> builtServices = services.build();
        Set<ModelDeprecation> deprecations = buildDeprecations(builtServices);
        Set<ModelResponse> responses = builtServices.stream()
                .flatMap(service -> service.methods().stream())
                .flatMap(method -> method.responses().stream())
                .collect(toImmutableSet());

        return new ModelServices(builtServices, responses, deprecations, ImmutableSet.of());
    }

    private static <K, V> Function<K, V> createThunk(Function<K, V> initial, BiFunction<K, V, V> next)
    {
        Map<K, V> holder = new ConcurrentHashMap<>();
        return key -> holder.compute(key, (k, v) -> (v == null) ? initial.apply(k) : next.apply(k, v));
    }
}
