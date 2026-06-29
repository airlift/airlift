package io.airlift.api.binding;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiCancellation;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.ApiId;
import io.airlift.api.ApiIdLookup;
import io.airlift.api.ApiIdSupportsLookup;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiRequestFilter;
import io.airlift.api.ApiResponseFilter;
import io.airlift.api.binding.JaxrsQuotaFilter.ApiQuotaControllerProxy;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.compatability.ApiCompatibility;
import io.airlift.api.compatability.ApiCompatibilityTester;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelDeprecation;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.api.model.ModelSupportsIdLookup;
import io.airlift.api.openapi.OpenApiExtensionFilter;
import io.airlift.api.openapi.OpenApiFilter;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiModule;
import io.airlift.api.validation.ResourceSerializationValidator;
import io.airlift.api.validation.ValidatorException;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.api.binding.ApiBindingKeys.annotatedKey;
import static io.airlift.api.binding.ApiMapBinders.newMapBinder;
import static io.airlift.api.binding.JaxrsResourceBuilder.jaxrsResourceBuilder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.util.Objects.requireNonNull;

public class ApiModule
        implements Module
{
    private final ModelApi modelApi;
    private final Optional<Class<? extends Annotation>> bindingAnnotation;
    private final Set<Builder.RequestFilter> requestFilters;
    private final Set<Builder.ResponseFilter> responseFilters;
    private final Map<Class<? extends ApiId<?, ?>>, Consumer<LinkedBindingBuilder<ApiIdLookup<? extends ApiId<?, ?>>>>> idLookupBindings;
    private final Optional<OpenApiMetadata> openApiMetadata;
    private final Consumer<LinkedBindingBuilder<OpenApiFilter>> openApiFilterBinding;
    private final OpenApiExtensionFilter extensionFilter;
    private final Optional<ApiCompatibilityTester> compatibilityTester;
    private final boolean withApiLogging;
    private final ApiMode apiMode;
    private final ApiEnumValueResolver enumValueResolver;
    private final Optional<ExecutorService> cancellableRequestExecutor;

    private ApiModule(
            ModelApi modelApi,
            Optional<Class<? extends Annotation>> bindingAnnotation,
            Set<Builder.RequestFilter> requestFilters,
            Set<Builder.ResponseFilter> responseFilters,
            Map<Class<? extends ApiId<?, ?>>, Consumer<LinkedBindingBuilder<ApiIdLookup<? extends ApiId<?, ?>>>>> idLookupBindings,
            Optional<OpenApiMetadata> openApiMetadata,
            Consumer<LinkedBindingBuilder<OpenApiFilter>> openApiFilterBinding,
            OpenApiExtensionFilter extensionFilter,
            Optional<ApiCompatibilityTester> compatibilityTester,
            boolean withApiLogging,
            ApiMode apiMode,
            ApiEnumValueResolver enumValueResolver,
            Optional<ExecutorService> cancellableRequestExecutor)
    {
        this.modelApi = requireNonNull(modelApi, "api is null");
        this.bindingAnnotation = requireNonNull(bindingAnnotation, "bindingAnnotation is null");
        this.requestFilters = ImmutableSet.copyOf(requestFilters);
        this.responseFilters = ImmutableSet.copyOf(responseFilters);
        this.idLookupBindings = ImmutableMap.copyOf(idLookupBindings);
        this.openApiMetadata = requireNonNull(openApiMetadata, "openApiMetadata is null");
        this.openApiFilterBinding = requireNonNull(openApiFilterBinding, "openApiFilterBinding is null");
        this.extensionFilter = requireNonNull(extensionFilter, "extensionFilter is null");
        this.compatibilityTester = requireNonNull(compatibilityTester, "compatibilityTester is null");
        this.withApiLogging = withApiLogging;
        this.apiMode = requireNonNull(apiMode, "apiMode is null");
        this.enumValueResolver = requireNonNull(enumValueResolver, "enumValueResolver is null");
        this.cancellableRequestExecutor = requireNonNull(cancellableRequestExecutor, "cancellableRequestExecutor is null");
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private final ImmutableSet.Builder<ModelApi> modelApis = ImmutableSet.builder();
        private Optional<Class<? extends Annotation>> bindingAnnotation = Optional.empty();
        private final ImmutableList.Builder<Consumer<ApiBuilder>> apiConsumers = ImmutableList.builder();
        private final ImmutableSet.Builder<RequestFilter> requestFilters = ImmutableSet.builder();
        private final ImmutableSet.Builder<ResponseFilter> responseFilters = ImmutableSet.builder();
        private final ImmutableMap.Builder<Class<? extends ApiId<?, ?>>, Consumer<LinkedBindingBuilder<ApiIdLookup<? extends ApiId<?, ?>>>>> idLookupBindings = ImmutableMap.builder();
        private Optional<OpenApiMetadata> openApiMetadata = Optional.empty();
        private OpenApiExtensionFilter extensionFilter;
        private Consumer<LinkedBindingBuilder<OpenApiFilter>> openApiFilterBinding;
        private Optional<ApiCompatibilityTester> compatibilityTester = Optional.empty();
        private boolean withApiLogging;
        private ApiMode apiMode = ApiMode.DEBUG;
        private ApiEnumValueResolver enumValueResolver = ApiBuilderConfig.jackson().enumValueResolver();
        private Optional<ExecutorService> cancellableRequestExecutor = Optional.empty();

        private Builder() {}

        private record RequestFilter(Predicate<ModelMethod> predicate, Class<? extends ContainerRequestFilter> filterClass)
        {
            RequestFilter
            {
                requireNonNull(predicate, "predicate is null");
                requireNonNull(filterClass, "filterClass is null");
            }
        }

        private record ResponseFilter(Predicate<ModelMethod> predicate, Class<? extends ContainerResponseFilter> filterClass)
        {
            ResponseFilter
            {
                requireNonNull(predicate, "predicate is null");
                requireNonNull(filterClass, "filterClass is null");
            }
        }

        public Builder addApi(ModelApi api)
        {
            modelApis.add(api);
            return this;
        }

        public Builder addApi(Consumer<ApiBuilder> consumer)
        {
            apiConsumers.add(requireNonNull(consumer, "consumer is null"));
            return this;
        }

        public Builder withApiBuilderConfig(ApiBuilderConfig config)
        {
            this.enumValueResolver = requireNonNull(config, "config is null").enumValueResolver();
            return this;
        }

        public Builder addRequestFilter(Predicate<ModelMethod> predicate, Class<? extends ContainerRequestFilter> filter)
        {
            requestFilters.add(new RequestFilter(predicate, filter));
            return this;
        }

        public Builder addResponseFilter(Predicate<ModelMethod> predicate, Class<? extends ContainerResponseFilter> filter)
        {
            responseFilters.add(new ResponseFilter(predicate, filter));
            return this;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends ApiId<?, ?>> Builder addIdLookupBinding(Class<T> idClass, Consumer<LinkedBindingBuilder<ApiIdLookup<T>>> binding)
        {
            // raw map is type-safe here because addIdLookupBinding is type-safe
            ((ImmutableMap.Builder) idLookupBindings).put(idClass, binding);
            return this;
        }

        public Builder withOpenApiFilterBinding(Consumer<LinkedBindingBuilder<OpenApiFilter>> binding)
        {
            checkArgument(this.openApiFilterBinding == null, "openApiFilterBinding is already set");

            this.openApiFilterBinding = requireNonNull(binding, "binding is null");
            return this;
        }

        public Builder withOpenApiMetadata(OpenApiMetadata openApiMetadata)
        {
            checkArgument(this.openApiMetadata.isEmpty(), "openApiMetadata is already set");

            this.openApiMetadata = Optional.of(openApiMetadata);
            return this;
        }

        public Builder withOpenApiExtensionFilter(OpenApiExtensionFilter extensionFilter)
        {
            checkArgument(this.extensionFilter == null, "OpenApiExtensionFilter is already set");

            this.extensionFilter = requireNonNull(extensionFilter, "extensionFilter is null");
            return this;
        }

        public Builder withCompatibilityTester(ApiCompatibilityTester compatibilityTester)
        {
            this.compatibilityTester = Optional.of(compatibilityTester);
            return this;
        }

        public Builder withApiLogging()
        {
            withApiLogging = true;
            return this;
        }

        public Builder withCancellableRequestExecutor(ExecutorService executor)
        {
            checkArgument(this.cancellableRequestExecutor.isEmpty(), "cancellable request executor is already set");

            this.cancellableRequestExecutor = Optional.of(requireNonNull(executor, "executor is null"));
            return this;
        }

        public Builder forProduction()
        {
            this.apiMode = ApiMode.PRODUCTION;
            return this;
        }

        public Builder annotatedWith(Class<? extends Annotation> annotationType)
        {
            checkArgument(this.bindingAnnotation.isEmpty(), "annotation is already set");

            this.bindingAnnotation = Optional.of(requireNonNull(annotationType, "annotationType is null"));
            return this;
        }

        public Module build()
        {
            Consumer<LinkedBindingBuilder<OpenApiFilter>> localFilterBinding = (openApiFilterBinding != null) ? openApiFilterBinding : binding -> binding.toInstance(_ -> _ -> true);
            OpenApiExtensionFilter localExtensionFilter = (extensionFilter != null) ? extensionFilter : (_, _, operation) -> operation;

            return new ApiModule(
                    mergeApis(),
                    bindingAnnotation,
                    requestFilters.build(),
                    responseFilters.build(),
                    idLookupBindings.build(),
                    openApiMetadata,
                    localFilterBinding,
                    localExtensionFilter,
                    compatibilityTester,
                    withApiLogging,
                    apiMode,
                    enumValueResolver,
                    cancellableRequestExecutor);
        }

        private ModelApi mergeApis()
        {
            ImmutableSet.Builder<ModelApi> apis = ImmutableSet.builder();
            apis.addAll(modelApis.build());
            apiConsumers.build().stream()
                    .map(consumer -> {
                        ApiBuilder apiBuilder = ApiBuilder.apiBuilder(enumValueResolver);
                        consumer.accept(apiBuilder);
                        return apiBuilder.build();
                    })
                    .forEach(apis::add);

            return apis.build()
                    .stream()
                    .reduce(ModelApi::mergeWith)
                    .orElseGet(() -> new ModelApi(new ModelServices(ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of()), ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of()));
        }
    }

    @Override
    public void configure(Binder binder)
    {
        boolean hasCancellableMethods = hasCancellableMethods();
        if (hasCancellableMethods && cancellableRequestExecutor.isEmpty()) {
            binder.addError(
                    "API methods with @Context %s require a cancellable request executor. Call %s.%s().%s(...).",
                    ApiCancellation.class.getSimpleName(),
                    ApiModule.class.getSimpleName(),
                    "builder",
                    "withCancellableRequestExecutor");
        }

        bindApi(binder);

        newOptionalBinder(binder, annotatedKey(ApiQuotaController.class, bindingAnnotation)).setBinding().toInstance(new ApiQuotaControllerProxy());

        validateIdLookups(idLookupBindings.keySet());

        MapBinder<Class<? extends ApiId<?, ?>>, ApiIdLookup<? extends ApiId<?, ?>>> idLookupBinder = newMapBinder(
                binder,
                new TypeLiteral<>() {},
                new TypeLiteral<>() {},
                bindingAnnotation);
        idLookupBindings.forEach((idClass, binding) -> binding.accept(idLookupBinder.addBinding(idClass)));

        binder.bind(annotatedKey(ResourceSerializationValidator.class, bindingAnnotation)).toInstance(new ResourceSerializationValidator(modelApi.needsSerializationValidation()));
        binder.bind(annotatedKey(SerializationValidator.class, bindingAnnotation)).toProvider(serializationValidatorProvider(binder)).asEagerSingleton();
        binder.bind(annotatedKey(ApiMode.class, bindingAnnotation)).toInstance(apiMode);

        bindUnwrapped(binder, modelApi.unwrappedResources());
        bindPolyResources(binder, modelApi.polyResources());

        compatibilityTester.ifPresent(tester -> {
            binder.bind(annotatedKey(ApiCompatibilityTester.class, bindingAnnotation)).toInstance(tester);
            binder.bind(annotatedKey(ApiCompatibility.class, bindingAnnotation)).toProvider(apiCompatibilityProvider(binder)).asEagerSingleton();
        });
    }

    private void bindApi(Binder binder)
    {
        Map<ModelServiceType, List<ModelService>> servicesByType = modelApi.modelServices().services().stream().collect(Collectors.groupingBy(modelService -> modelService.service().type()));
        binder.bind(annotatedKey(new TypeLiteral<Set<ModelServiceType>>() {}, bindingAnnotation)).toInstance(servicesByType.keySet());

        JaxrsResourceBuilder jaxrsResourceBuilder = jaxrsResourceBuilder(binder, withApiLogging, bindingAnnotation);
        cancellableRequestExecutor.ifPresent(executor -> jaxrsBinder(binder, bindingAnnotation).bindInstance(new ApiCancellableRequestExecutorProvider(executor)));

        MapBinder<Method, ModelDeprecation> deprecationBinder = newMapBinder(
                binder,
                new TypeLiteral<>() {},
                new TypeLiteral<>() {},
                bindingAnnotation);

        binder.bind(annotatedKey(ModelServices.class, bindingAnnotation)).toInstance(modelApi.modelServices());
        modelApi.modelServices().errors().forEach(binder::addError);
        modelApi.modelServices().services().forEach(jaxrsResourceBuilder::bindService);
        jaxrsResourceBuilder.bindFeatures();

        openApiMetadata.ifPresent(openApi -> binder.install(new OpenApiModule(modelApi.modelServices(), bindingAnnotation, openApi, openApiFilterBinding, extensionFilter, enumValueResolver)));

        modelApi.modelServices().deprecations().forEach(modelDeprecation -> deprecationBinder.addBinding(modelDeprecation.method()).toInstance(modelDeprecation));

        bindContainerFilters(binder);
    }

    private boolean hasCancellableMethods()
    {
        return modelApi.modelServices().services().stream()
                .flatMap(service -> service.methods().stream())
                .anyMatch(JaxrsResourceBuilder::isCancellable);
    }

    private void bindContainerFilters(Binder binder)
    {
        MapBinder<Predicate<ModelMethod>, ContainerRequestFilter> requestFilterBinder = newMapBinder(
                binder,
                new TypeLiteral<>() {},
                new TypeLiteral<>() {},
                bindingAnnotation);
        MapBinder<Predicate<ModelMethod>, ContainerResponseFilter> responseFilterBinder = newMapBinder(
                binder,
                new TypeLiteral<>() {},
                new TypeLiteral<>() {},
                bindingAnnotation);

        modelApi.modelServices().services().forEach(modelService -> modelService.methods().forEach(modelMethod -> {
            ApiRequestFilter requestFilter = modelMethod.method().getAnnotation(ApiRequestFilter.class);
            if (requestFilter != null) {
                requestFilterBinder.addBinding(check -> check.equals(modelMethod)).to(requestFilter.value());
            }

            ApiResponseFilter responseFilter = modelMethod.method().getAnnotation(ApiResponseFilter.class);
            if (responseFilter != null) {
                responseFilterBinder.addBinding(check -> check.equals(modelMethod)).to(responseFilter.value());
            }
        }));

        requestFilters.forEach(filter -> requestFilterBinder.addBinding(filter.predicate).to(filter.filterClass).in(Scopes.SINGLETON));
        responseFilters.forEach(filter -> responseFilterBinder.addBinding(filter.predicate).to(filter.filterClass).in(Scopes.SINGLETON));
    }

    @SuppressWarnings("unused")
    static class SerializationValidator
    {
        SerializationValidator(ResourceSerializationValidator validator, JsonMapper jsonMapper)
        {
            requireNonNull(validator, "validator is null");
            jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null")
                    .rebuild()
                    .disable(FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)
                    .build();
            validator.validateSerialization(jsonMapper);
        }
    }

    private Provider<SerializationValidator> serializationValidatorProvider(Binder binder)
    {
        ApiBindingProvider bindingProvider = new ApiBindingProvider(binder, bindingAnnotation);
        Provider<ResourceSerializationValidator> validator = bindingProvider.get(ResourceSerializationValidator.class);
        Provider<JsonMapper> jsonMapper = bindingProvider.getUnqualified(JsonMapper.class);
        return () -> new SerializationValidator(validator.get(), jsonMapper.get());
    }

    private Provider<ApiCompatibility> apiCompatibilityProvider(Binder binder)
    {
        ApiBindingProvider bindingProvider = new ApiBindingProvider(binder, bindingAnnotation);
        Provider<ApiCompatibilityTester> tester = bindingProvider.get(ApiCompatibilityTester.class);
        Provider<ModelServices> modelServices = bindingProvider.get(ModelServices.class);
        return () -> new ApiCompatibility(tester.get(), modelServices.get());
    }

    private void bindUnwrapped(Binder binder, Set<Class<?>> resourcesWithUnwrappedComponents)
    {
        if (resourcesWithUnwrappedComponents.isEmpty()) {
            return;
        }

        MapBinder<Class<?>, JsonDeserializer<?>> mapBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {});
        resourcesWithUnwrappedComponents.forEach(clazz -> {
            UnwrappedDeserializer deserializer = new UnwrappedDeserializer(clazz);
            mapBinder.addBinding(clazz).toInstance(deserializer);
        });
    }

    private void bindPolyResources(Binder binder, Set<Class<?>> polyResources)
    {
        if (polyResources.isEmpty()) {
            return;
        }

        PolyResourceModule.Builder builder = PolyResourceModule.builder();
        polyResources.forEach(builder::addPolyResource);
        binder.install(builder.build());
    }

    private void validateIdLookups(Set<Class<? extends ApiId<?, ?>>> idClasses)
    {
        Set<Class<? extends ApiId<?, ?>>> expectedIdLookups = modelApi.modelServices().services()
                .stream()
                .flatMap(service -> service.methods().stream())
                .flatMap(modelMethod -> modelMethod.parameters().stream().flatMap(parameter -> parameter.supportsIdLookup().stream()))
                .map(ModelSupportsIdLookup::idClass)
                .collect(toImmutableSet());

        if (!idClasses.containsAll(expectedIdLookups)) {
            String missing = Sets.difference(expectedIdLookups, idClasses)
                    .stream()
                    .map(Class::getName)
                    .collect(Collectors.joining(", "));
            throw new ValidatorException("There are Ids used in service methods that are annotated with %s but missing a binding of %s. Id types: %s".formatted(ApiIdSupportsLookup.class.getSimpleName(), ApiIdLookup.class.getSimpleName(), missing));
        }
    }
}
