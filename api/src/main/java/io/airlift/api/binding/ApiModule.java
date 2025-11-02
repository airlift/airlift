package io.airlift.api.binding;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import io.airlift.api.ApiId;
import io.airlift.api.ApiIdLookup;
import io.airlift.api.ApiIdSupportsLookup;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiRequestFilter;
import io.airlift.api.ApiResponseFilter;
import io.airlift.api.binding.JaxrsQuotaFilter.ApiQuotaControllerProxy;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.compatability.ApiCompatibility;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.api.binding.JaxrsResourceBuilder.jaxrsResourceBuilder;
import static java.util.Objects.requireNonNull;

public class ApiModule
        implements Module
{
    private final ModelApi modelApi;
    private final Set<Builder.RequestFilter> requestFilters;
    private final Set<Builder.ResponseFilter> responseFilters;
    private final Map<Class<? extends ApiId<?, ?>>, Consumer<LinkedBindingBuilder<ApiIdLookup<? extends ApiId<?, ?>>>>> idLookupBindings;
    private final Optional<OpenApiMetadata> openApiMetadata;
    private final Consumer<AnnotatedBindingBuilder<OpenApiFilter>> openApiFilterBinding;
    private final OpenApiExtensionFilter extensionFilter;
    private final boolean withCompatibilityTester;
    private final boolean withApiLogging;
    private final ApiMode apiMode;

    private ApiModule(
            ModelApi modelApi,
            Set<Builder.RequestFilter> requestFilters,
            Set<Builder.ResponseFilter> responseFilters,
            Map<Class<? extends ApiId<?, ?>>, Consumer<LinkedBindingBuilder<ApiIdLookup<? extends ApiId<?, ?>>>>> idLookupBindings,
            Optional<OpenApiMetadata> openApiMetadata,
            Consumer<AnnotatedBindingBuilder<OpenApiFilter>> openApiFilterBinding,
            OpenApiExtensionFilter extensionFilter,
            boolean withCompatibilityTester,
            boolean withApiLogging,
            ApiMode apiMode)
    {
        this.modelApi = requireNonNull(modelApi, "api is null");
        this.requestFilters = ImmutableSet.copyOf(requestFilters);
        this.responseFilters = ImmutableSet.copyOf(responseFilters);
        this.idLookupBindings = ImmutableMap.copyOf(idLookupBindings);
        this.openApiMetadata = requireNonNull(openApiMetadata, "openApiMetadata is null");
        this.openApiFilterBinding = requireNonNull(openApiFilterBinding, "openApiFilterBinding is null");
        this.extensionFilter = requireNonNull(extensionFilter, "extensionFilter is null");
        this.withCompatibilityTester = withCompatibilityTester;
        this.withApiLogging = withApiLogging;
        this.apiMode = requireNonNull(apiMode, "apiMode is null");
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private final ImmutableSet.Builder<ModelApi> modelApis = ImmutableSet.builder();
        private final ImmutableSet.Builder<RequestFilter> requestFilters = ImmutableSet.builder();
        private final ImmutableSet.Builder<ResponseFilter> responseFilters = ImmutableSet.builder();
        private final ImmutableMap.Builder<Class<? extends ApiId<?, ?>>, Consumer<LinkedBindingBuilder<ApiIdLookup<? extends ApiId<?, ?>>>>> idLookupBindings = ImmutableMap.builder();
        private Optional<OpenApiMetadata> openApiMetadata = Optional.empty();
        private OpenApiExtensionFilter extensionFilter;
        private Consumer<AnnotatedBindingBuilder<OpenApiFilter>> openApiFilterBinding;
        private boolean withCompatibilityTester;
        private boolean withApiLogging;
        private ApiMode apiMode = ApiMode.DEBUG;

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
            ApiBuilder apiBuilder = ApiBuilder.apiBuilder();
            consumer.accept(apiBuilder);

            modelApis.add(apiBuilder.build());
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

        public Builder withOpenApiFilterBinding(Consumer<AnnotatedBindingBuilder<OpenApiFilter>> binding)
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

        public Builder withCompatibilityTester()
        {
            withCompatibilityTester = true;
            return this;
        }

        public Builder withApiLogging()
        {
            withApiLogging = true;
            return this;
        }

        public Builder forProduction()
        {
            this.apiMode = ApiMode.PRODUCTION;
            return this;
        }

        public Module build()
        {
            Consumer<AnnotatedBindingBuilder<OpenApiFilter>> localFilterBinding = (openApiFilterBinding != null) ? openApiFilterBinding : binding -> binding.toInstance(ignore -> _ -> true);
            OpenApiExtensionFilter localExtensionFilter = (extensionFilter != null) ? extensionFilter : (_, _, operation) -> operation;

            return new ApiModule(
                    mergeApis(),
                    requestFilters.build(),
                    responseFilters.build(),
                    idLookupBindings.build(),
                    openApiMetadata,
                    localFilterBinding,
                    localExtensionFilter,
                    withCompatibilityTester,
                    withApiLogging,
                    apiMode);
        }

        private ModelApi mergeApis()
        {
            return modelApis.build()
                    .stream()
                    .reduce(ModelApi::mergeWith)
                    .orElseGet(() -> new ModelApi(new ModelServices(ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of()), ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of()));
        }
    }

    @Override
    public void configure(Binder binder)
    {
        bindApi(binder);

        newOptionalBinder(binder, ApiQuotaController.class).setBinding().toInstance(new ApiQuotaControllerProxy());

        validateIdLookups(idLookupBindings.keySet());

        MapBinder<Class<? extends ApiId<?, ?>>, ApiIdLookup<? extends ApiId<?, ?>>> idLookupBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {});
        idLookupBindings.forEach((idClass, binding) -> binding.accept(idLookupBinder.addBinding(idClass)));

        binder.bind(ResourceSerializationValidator.class).toInstance(new ResourceSerializationValidator(modelApi.needsSerializationValidation()));
        binder.bind(SerializationValidator.class).asEagerSingleton();
        binder.bind(ApiMode.class).toInstance(apiMode);

        bindUnwrapped(binder, modelApi.unwrappedResources());
        bindPolyResources(binder, modelApi.polyResources());

        if (withCompatibilityTester) {
            binder.bind(ApiCompatibility.class).asEagerSingleton();
        }
    }

    private void bindApi(Binder binder)
    {
        Map<ModelServiceType, List<ModelService>> servicesByType = modelApi.modelServices().services().stream().collect(Collectors.groupingBy(modelService -> modelService.service().type()));
        binder.bind(new TypeLiteral<Set<ModelServiceType>>() {}).toInstance(servicesByType.keySet());

        JaxrsResourceBuilder jaxrsResourceBuilder = jaxrsResourceBuilder(binder, withApiLogging);
        MapBinder<Method, ModelDeprecation> deprecationBinder = MapBinder.newMapBinder(binder, Method.class, ModelDeprecation.class);

        binder.bind(ModelServices.class).toInstance(modelApi.modelServices());
        modelApi.modelServices().errors().forEach(binder::addError);
        modelApi.modelServices().services().forEach(jaxrsResourceBuilder::bindService);
        jaxrsResourceBuilder.bindFeatures();

        openApiMetadata.ifPresent(openApi -> binder.install(new OpenApiModule(modelApi.modelServices(), openApi, openApiFilterBinding, extensionFilter)));

        modelApi.modelServices().deprecations().forEach(modelDeprecation -> deprecationBinder.addBinding(modelDeprecation.method()).toInstance(modelDeprecation));

        bindContainerFilters(binder);
    }

    private void bindContainerFilters(Binder binder)
    {
        MapBinder<Predicate<ModelMethod>, ContainerRequestFilter> requestFilterBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {});
        MapBinder<Predicate<ModelMethod>, ContainerResponseFilter> responseFilterBinder = MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>() {});

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
        @Inject
        SerializationValidator(ResourceSerializationValidator validator, ObjectMapper objectMapper)
        {
            requireNonNull(validator, "validator is null");
            requireNonNull(objectMapper, "objectMapper is null");

            objectMapper.disable(FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS);
            validator.validateSerialization(objectMapper);
        }
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
