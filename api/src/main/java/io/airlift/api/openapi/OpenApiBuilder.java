package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import io.airlift.api.ApiIdSupportsLookup;
import io.airlift.api.ApiTrait;
import io.airlift.api.model.ModelDeprecation;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelOptionalParameter;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResourceModifier;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.OpenApiMetadata.ServiceDetail;
import io.airlift.api.openapi.SchemaBuilder.BuildSchemaMode;
import io.airlift.api.openapi.models.ApiResponse;
import io.airlift.api.openapi.models.ApiResponses;
import io.airlift.api.openapi.models.ArraySchema;
import io.airlift.api.openapi.models.Content;
import io.airlift.api.openapi.models.HeaderParameter;
import io.airlift.api.openapi.models.Info;
import io.airlift.api.openapi.models.MediaType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Operation;
import io.airlift.api.openapi.models.Parameter;
import io.airlift.api.openapi.models.PathItem;
import io.airlift.api.openapi.models.PathParameter;
import io.airlift.api.openapi.models.Paths;
import io.airlift.api.openapi.models.QueryParameter;
import io.airlift.api.openapi.models.RequestBody;
import io.airlift.api.openapi.models.Schema;
import io.airlift.api.openapi.models.SecurityRequirement;
import io.airlift.api.openapi.models.SecurityScheme;
import io.airlift.api.openapi.models.StringSchema;
import io.airlift.api.openapi.models.Tag;
import io.airlift.api.openapi.models.TagGroup;
import io.airlift.api.validation.ValidationContext;
import jakarta.ws.rs.core.UriBuilder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.airlift.api.ApiServiceTrait.ENUMS_AS_STRINGS;
import static io.airlift.api.internals.Mappers.MethodPathMode.FOR_DISPLAY;
import static io.airlift.api.internals.Mappers.buildMethodPath;
import static io.airlift.api.internals.Mappers.buildResourceId;
import static io.airlift.api.internals.Mappers.buildServicePath;
import static io.airlift.api.internals.Strings.capitalize;
import static io.airlift.api.model.ModelResourceModifier.IS_MULTIPART_FORM;
import static io.airlift.api.model.ModelResourceModifier.IS_STREAMING_RESPONSE;
import static io.airlift.api.openapi.OpenApiMetadata.SECTION_HELP;
import static io.airlift.api.openapi.OpenApiMetadata.SECTION_MODELS;
import static io.airlift.api.openapi.OpenApiMetadata.SECTION_SERVICES;
import static io.airlift.api.openapi.OpenApiMetadata.TAG_MODEL_DEFINITIONS;
import static io.airlift.api.openapi.OpenApiMetadata.TAG_RESPONSE_DEFINITIONS;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

class OpenApiBuilder
{
    private final SchemaBuilder schemaBuilder;
    private final OpenApiExtensionFilter extensionFilter;
    private final Paths paths = new Paths();
    private final List<Tag> tags = new ArrayList<>();
    private final ModelServiceType serviceType;
    private final Map<Method, ModelDeprecation> deprecations;
    private final OpenApiMetadata metadata;
    private final Predicate<Method> methodFilter;
    private final Set<String> usedOperationIds = new TreeSet<>();
    private final Set<String> serviceTags = new LinkedHashSet<>();

    private static final StringSchema STRING_SCHEMA = new StringSchema();

    private static final String PATH = "path";

    private static final String NO_CONTENT = "204";
    private static final String SUCCESS = "200";

    static OpenApiBuilder builder(
            ModelServiceType serviceType,
            Collection<ModelDeprecation> deprecations,
            OpenApiMetadata metadata,
            Predicate<Method> methodFilter,
            OpenApiExtensionFilter extensionFilter)
    {
        return new OpenApiBuilder(serviceType, deprecations, metadata, methodFilter, extensionFilter);
    }

    enum JsonUriMode
    {
        TEMPLATE,
        EXTERNAL,
    }

    static String jsonUriBuilder(OpenApiMetadata metadata, String id, int version, JsonUriMode mode)
    {
        UriBuilder builder = UriBuilder.fromPath(metadata.baseBath());
        return switch (mode) {
            case TEMPLATE -> builder.path("{type:.*}")
                    .path("openapi")
                    .path("{version:.*}")
                    .path("json")
                    .toTemplate();
            case EXTERNAL -> builder.path(id)
                    .path("openapi")
                    .path("v" + version)
                    .path("json")
                    .build()
                    .toString();
        };
    }

    OpenAPI build()
    {
        OpenAPI openAPI = new OpenAPI();

        Info info = new Info()
                .title(serviceType.title())
                .version("v" + serviceType.version());

        List<TagGroup> tagGroups = buildTagGroups();

        openAPI.info(info);
        openAPI.paths(paths);
        openAPI.tags(tags);
        openAPI.tagGroups(tagGroups);

        buildSecurity().ifPresent(security -> {
            openAPI.schemaRequirement(security.getKey(), security.getValue());
            openAPI.security(ImmutableList.of(new SecurityRequirement().addList(security.getKey())));
        });

        schemaBuilder.build().forEach(schema -> openAPI.schema(schema.getName(), schema));

        return openAPI;
    }

    void addService(ModelService modelService)
    {
        List<ModelMethod> methodsToAdd = modelService.methods()
                .stream()
                .filter(method -> !method.traits().contains(ApiTrait.PRIVATE))
                .filter(method -> methodFilter.test(method.method()))
                .toList();

        if (methodsToAdd.isEmpty()) {
            return;
        }

        tags.add(new Tag().name(makeServiceName(modelService)).description(modelService.service().generateDescriptionWithDocumentation()));

        methodsToAdd.forEach(method -> {
            String servicePath = buildServicePath(modelService.service());
            String methodPath = buildMethodPath(method, FOR_DISPLAY);
            String path = "/" + servicePath + "/" + methodPath;
            PathItem pathItem = paths.get(path);
            if (pathItem == null) {
                pathItem = new PathItem();
                paths.put(path, pathItem);
            }

            Operation operation = buildOperation(modelService, method);

            switch (method.methodType()) {
                case GET, LIST -> pathItem.get(operation);
                case CREATE -> pathItem.post(operation);
                case DELETE -> pathItem.delete(operation);
                case UPDATE -> {
                    if (method.isPatch()) {
                        pathItem.patch(operation);
                    }
                    else {
                        pathItem.put(operation);
                    }
                }
            }
        });
    }

    private List<TagGroup> buildTagGroups()
    {
        List<TagGroup> tagGroups = new ArrayList<>();

        String about = """
                %s
                
                <a href="%s" style="text-decoration: none !important" download="openApi.json">⬇️ Download OpenAPI specification</a>
                """.formatted(serviceType.description(), jsonUriBuilder(metadata, serviceType.id(), serviceType.version(), JsonUriMode.EXTERNAL));
        ServiceDetail descriptionServiceDetail = new ServiceDetail(SECTION_HELP, "About", about);

        Set<String> orderedSections = new LinkedHashSet<>();
        Map<String, List<ServiceDetail>> bySection = Streams.concat(Stream.of(descriptionServiceDetail), metadata.serviceDetails().stream())
                .peek(serviceDetail -> orderedSections.add(serviceDetail.section()))
                .collect(Collectors.groupingBy(ServiceDetail::section));

        bySection.forEach((section, serviceDetails) ->
                serviceDetails.forEach(serviceDetail -> tags.add(new Tag().name(serviceDetail.label()).description(serviceDetail.description()))));

        orderedSections.forEach(sectionName -> {
            List<String> labels = bySection.get(sectionName).stream().map(ServiceDetail::label).collect(toImmutableList());
            if (sectionName.equals(SECTION_SERVICES)) {
                serviceTags.addAll(labels);
            }
            else {
                tagGroups.add(new TagGroup(sectionName, labels));
            }
        });

        tags.add(new Tag().name(TAG_MODEL_DEFINITIONS).description(""));
        tags.add(new Tag().name(TAG_RESPONSE_DEFINITIONS).description(""));

        tagGroups.add(new TagGroup(SECTION_SERVICES, ImmutableList.copyOf(serviceTags)));
        tagGroups.add(new TagGroup(SECTION_MODELS, ImmutableList.of(TAG_MODEL_DEFINITIONS, TAG_RESPONSE_DEFINITIONS)));

        return tagGroups;
    }

    private String makeServiceName(ModelService modelService)
    {
        return capitalize(modelService.service().name()) + " Service";
    }

    private Operation buildOperation(ModelService modelService, ModelMethod modelMethod)
    {
        ModelDeprecation modelDeprecation = deprecations.get(modelMethod.method());
        Operation operation = new Operation().operationId(buildOperationId(modelService.service(), modelMethod));

        String tagName = makeServiceName(modelService);
        operation.tags(ImmutableList.of(tagName));
        serviceTags.add(tagName);

        if (modelDeprecation != null) {
            addDeprecation(operation, modelDeprecation, modelMethod.description());
        }
        else {
            operation.description(modelMethod.description());
        }

        ApiResponses apiResponses = new ApiResponses();
        ApiResponse apiResponse = new ApiResponse();
        if ((modelMethod.returnType().type() == Void.TYPE)) {
            apiResponse.description("Success");
            apiResponses.addApiResponse(NO_CONTENT, apiResponse);
        }
        else if (modelMethod.returnType().modifiers().contains(IS_STREAMING_RESPONSE)) {
            ValidationContext tempValidationContext = new ValidationContext();
            MediaType item = new MediaType().schema(new StringSchema());
            apiResponse.description("Success").setContent(new Content().addMediaType(tempValidationContext.streamingResponseMediaType(modelMethod.returnType()).toString(), item));
            apiResponses.addApiResponse(SUCCESS, apiResponse);
        }
        else {
            MediaType item = new MediaType().schema(schemaBuilder.buildSchema(modelMethod.returnType(), BuildSchemaMode.STANDARD));
            apiResponse.description("Success").setContent(new Content().addMediaType(APPLICATION_JSON, item));
            apiResponses.addApiResponse(SUCCESS, apiResponse);
        }
        modelMethod.responses().forEach(modelResponse -> {
            ApiResponse apiErrorResponse = new ApiResponse();
            MediaType item = new MediaType().schema(schemaBuilder.withSchemaTag(TAG_RESPONSE_DEFINITIONS).buildSchema(modelResponse.resource(), BuildSchemaMode.STANDARD));
            apiErrorResponse.description(modelResponse.responseClass().getSimpleName()).setContent(new Content().addMediaType(APPLICATION_JSON, item));
            apiResponses.addApiResponse(Integer.toString(modelResponse.status().code()), apiErrorResponse);
        });
        operation.responses(apiResponses);

        modelMethod.requestBody().ifPresent(requestBody -> {
            boolean isMultiPartForm = requestBody.modifiers().contains(IS_MULTIPART_FORM);
            BuildSchemaMode buildSchemaMode = (requestBody.modifiers().contains(ModelResourceModifier.PATCH)) ? BuildSchemaMode.PATCH : BuildSchemaMode.STANDARD;
            MediaType item = new MediaType().schema(isMultiPartForm ? schemaBuilder.buildMultiPartForm(requestBody, buildSchemaMode) : schemaBuilder.buildSchema(requestBody, buildSchemaMode));
            String requestBodyType = isMultiPartForm ? MULTIPART_FORM_DATA : APPLICATION_JSON;
            operation.setRequestBody(new RequestBody().required(true).content(new Content().addMediaType(requestBodyType, item)));
        });

        operation.parameters(buildParameters(modelMethod));

        return extensionFilter.apply(modelService, modelMethod, operation);
    }

    private String buildMethodName(ModelMethod modelMethod)
    {
        StringBuilder name = new StringBuilder();

        String methodName = modelMethod.customVerb().map(String::toLowerCase).orElseGet(() -> modelMethod.methodType().name().toLowerCase());

        if (modelMethod.isPatch()) {
            name.append("patch").append(capitalize(methodName));
        }
        else {
            name.append(methodName);
        }

        ModelResource mainResource;
        if (modelMethod.returnType().type() != Void.TYPE) {
            mainResource = modelMethod.returnType();
        }
        else if (modelMethod.requestBody().isPresent()) {
            mainResource = modelMethod.requestBody().get();
        }
        else {
            mainResource = modelMethod.parameters().isEmpty() ? null : modelMethod.parameters().getFirst();
        }

        for (int i = 0; i < modelMethod.parameters().size(); ++i) {
            ModelResource modelResource = modelMethod.parameters().get(i);
            if ((mainResource == null) || !mainResource.name().equals(modelResource.name())) {
                if (modelResource.limitedValues().size() == 1) {
                    name.append(capitalize(modelResource.limitedValues().iterator().next()));
                }
                else {
                    name.append(capitalize(modelResource.name()));
                }
            }
        }

        if (mainResource != null) {
            name.append(capitalize(mainResource.name()));
        }

        return name.toString();
    }

    private String buildOperationId(ModelServiceMetadata metadata, ModelMethod modelMethod)
    {
        String methodName = modelMethod.openApiName().orElseGet(() -> buildMethodName(modelMethod));
        for (int i = 0; /* no test */ ; ++i) {
            String name = switch (i) {
                case 0 -> methodName;
                case 1 -> metadata.name() + "." + methodName;
                default -> metadata.name() + "." + methodName + i;
            };
            if (usedOperationIds.add(name)) {
                return name;
            }
        }
    }

    private void addDeprecation(Operation operation, ModelDeprecation modelDeprecation, String baseDescription)
    {
        StringBuilder description = new StringBuilder(baseDescription).append(" - DEPRECATED - ").append(modelDeprecation.information());
        modelDeprecation.deprecationDate().ifPresent(date -> description.append(" - Deprecation Date: ").append(date));
        modelDeprecation.newImplementation().ifPresent(link -> description.append(" - New Implementation: ").append(link));

        operation.deprecated(true);
        operation.description(description.toString());
    }

    private List<Parameter> buildParameters(ModelMethod modelMethod)
    {
        Stream<Parameter> parameters = modelMethod.parameters()
                .stream()
                .filter(resource -> resource.limitedValues().size() != 1)   // don't include single-limited values. They don't need a parameter
                .map(resource -> new PathParameter().name(buildResourceId(resource.type())).in(PATH).schema(STRING_SCHEMA).required(true).description(parameterDescription(resource)));
        Stream<Parameter> queryParameters = modelMethod.optionalParameters().stream()
                .flatMap(optionalParameter -> buildOptionalParameters(optionalParameter)
                        .collect(toImmutableList())
                        .stream());
        return Stream.concat(parameters, queryParameters).collect(toImmutableList());
    }

    private String parameterDescription(ModelResource resource)
    {
        return resource.supportsIdLookup()
                .map(modifier -> """
                        - %s
                        - This parameter can be looked up using `%s` instead of its Id. Use `%s%svalue` instead of an Id to lookup/search using the `value`. `value` must be encoded ([see RFC](https://www.rfc-editor.org/rfc/rfc3986#section-2.2) including `=`)
                        """.formatted(resource.description(), modifier.prefix(), modifier.prefix(), ApiIdSupportsLookup.LOOKUP_SEPARATOR))
                .orElseGet(resource::description);
    }

    private Stream<Parameter> buildOptionalParameters(ModelOptionalParameter optionalParameter)
    {
        return optionalParameter.externalParameters().stream().map(externalParameter -> {
            Parameter parameter = (optionalParameter.location() == ModelOptionalParameter.Location.QUERY) ? new QueryParameter() : new HeaderParameter();
            Optional<Schema<?>> schema;
            if (externalParameter.externalType().isArray()) {
                schema = schemaBuilder.buildBasicSchema(externalParameter.externalType().getComponentType()).map(s -> new ArraySchema().items(s));
                parameter.setExplode(true);
            }
            else {
                schema = schemaBuilder.buildBasicSchema(externalParameter.externalType());
            }
            if (schema.isEmpty()) {
                throw new RuntimeException("Unsupported external type: " + externalParameter.externalType());
            }
            parameter.name(externalParameter.name()).description(externalParameter.description()).schema(schema.get()).required(false);
            return parameter;
        });
    }

    private Optional<Map.Entry<String, SecurityScheme>> buildSecurity()
    {
        return metadata.security().map(securityScheme -> {
            SecurityScheme accessTokenSecurityScheme = new SecurityScheme();
            return switch (securityScheme) {
                case BEARER_ACCESS_TOKEN ->
                        Map.entry("accessToken", accessTokenSecurityScheme.type(SecurityScheme.Type.HTTP).name("Authorization").in(SecurityScheme.In.HEADER).scheme("bearer").bearerFormat("Access token"));
                case BEARER_JWT ->
                        Map.entry("bearer", accessTokenSecurityScheme.type(SecurityScheme.Type.HTTP).name("Authorization").in(SecurityScheme.In.HEADER).scheme("bearer").bearerFormat("JWT"));
                case BASIC -> Map.entry("Basic", accessTokenSecurityScheme.scheme("basic"));
            };
        });
    }

    private OpenApiBuilder(ModelServiceType serviceType, Collection<ModelDeprecation> deprecations, OpenApiMetadata metadata, Predicate<Method> methodFilter, OpenApiExtensionFilter extensionFilter)
    {
        this.serviceType = requireNonNull(serviceType, "serviceType is null");
        this.deprecations = deprecations.stream().collect(toImmutableMap(ModelDeprecation::method, identity()));
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.methodFilter = requireNonNull(methodFilter, "methodFilter is null");
        this.schemaBuilder = new SchemaBuilder(serviceType.serviceTraits().contains(ENUMS_AS_STRINGS));
        this.extensionFilter = requireNonNull(extensionFilter, "extensionFilter is null");
    }
}
