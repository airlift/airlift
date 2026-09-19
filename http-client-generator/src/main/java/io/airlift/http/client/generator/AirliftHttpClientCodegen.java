/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.client.generator;

import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenDiscriminator;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.JavaClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;
import static org.openapitools.codegen.utils.StringUtils.camelize;

public class AirliftHttpClientCodegen
        extends JavaClientCodegen
{
    // RFC 9110 token: the characters allowed in an HTTP field name
    private static final Pattern HTTP_TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    public static final String GENERATOR_NAME = "airlift-http-client";
    private static final String EVENT_STREAM_MEDIA_TYPE = "text/event-stream";
    private static final String EVENT_SCHEMA_EXTENSION = "x-airlift-event-schema";

    private final Map<String, String> serverSentEventTypes = new LinkedHashMap<>();
    private final Set<String> rawResponseOperations = new LinkedHashSet<>();

    private final Map<String, AuthenticationScheme> authenticationSchemes = new LinkedHashMap<>();
    private List<SecurityRequirement> defaultSecurityRequirements = List.of();

    public AirliftHttpClientCodegen()
    {
        outputFolder = "generated-code" + File.separator + "airlift";
        templateDir = "airlift-http-client";
        embeddedTemplateDir = "airlift-http-client";

        setUseOneOfInterfaces(true);

        typeMapping.put("DateTime", "Instant");
        typeMapping.put("date-time", "Instant");

        importMapping.put("Instant", "java.time.Instant");
        importMapping.put("HttpClient", "io.airlift.http.client.HttpClient");
        importMapping.put("Request", "io.airlift.http.client.Request");
        importMapping.put("JsonCodec", "io.airlift.json.JsonCodec");
        importMapping.put("URI", "java.net.URI");

        apiTemplateFiles.clear();
        apiTemplateFiles.put("api.mustache", ".java");

        modelTemplateFiles.clear();
        modelTemplateFiles.put("model.mustache", ".java");

        apiTestTemplateFiles.clear();
        modelTestTemplateFiles.clear();
        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();
    }

    @Override
    public CodegenType getTag()
    {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName()
    {
        return GENERATOR_NAME;
    }

    @Override
    public String getHelp()
    {
        return "Generates a Java client using Airlift's HttpClient library.";
    }

    @Override
    public String toApiName(String name)
    {
        if (name.isEmpty()) {
            return "DefaultClient";
        }
        return camelize(name) + "Client";
    }

    @Override
    public void processOpts()
    {
        if (!additionalProperties.containsKey("library")) {
            setLibrary("native");
        }

        super.processOpts();

        if (additionalProperties.containsKey("invokerPackage")) {
            invokerPackage = (String) additionalProperties.get("invokerPackage");
        }
        else if (apiPackage != null && !apiPackage.isEmpty()) {
            int lastDot = apiPackage.lastIndexOf('.');
            invokerPackage = lastDot > 0 ? apiPackage.substring(0, lastDot) : apiPackage;
            additionalProperties.put("invokerPackage", invokerPackage);
        }

        String projectName = (String) additionalProperties.getOrDefault("projectName", "api");

        String clientName;
        if (additionalProperties.containsKey("clientName")) {
            clientName = (String) additionalProperties.get("clientName");
        }
        else {
            clientName = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, projectName.replace('_', '-').replace(' ', '-'));
        }
        additionalProperties.put("clientName", clientName);
        additionalProperties.put("clientNameLower", projectName.toLowerCase(Locale.ENGLISH).replace("-", ""));

        String invokerFolder = (sourceFolder + File.separator + invokerPackage).replace(".", File.separator);

        supportingFiles.clear();
        supportingFiles.add(new SupportingFile("apiException.mustache", invokerFolder, "ApiException.java"));
        supportingFiles.add(new SupportingFile("retryPolicy.mustache", invokerFolder, "RetryPolicy.java"));
        supportingFiles.add(new SupportingFile("clientModule.mustache", invokerFolder, clientName + "ClientModule.java"));
        supportingFiles.add(new SupportingFile("clientConfig.mustache", invokerFolder, clientName + "ClientConfig.java"));
        supportingFiles.add(new SupportingFile("httpClientAnnotation.mustache", invokerFolder, "For" + clientName + ".java"));
        supportingFiles.add(new SupportingFile("pom.mustache", "", "pom.xml"));
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI)
    {
        super.preprocessOpenAPI(openAPI);

        serverSentEventTypes.clear();
        rawResponseOperations.clear();

        Map<String, Set<String>> requiredScopes = collectRequiredScopes(openAPI);
        authenticationSchemes.clear();
        if (openAPI.getComponents() != null && openAPI.getComponents().getSecuritySchemes() != null) {
            validateAuthenticationSchemeNames(openAPI.getComponents().getSecuritySchemes().keySet());
            // Only schemes referenced by a security requirement can be applied by generated operations,
            // so declared but unused schemes must not shape the client or weaken its configuration.
            openAPI.getComponents().getSecuritySchemes().forEach((name, scheme) -> {
                if (requiredScopes.containsKey(name)) {
                    authenticationSchemes.put(name, authenticationScheme(name, scheme, List.copyOf(requiredScopes.get(name))));
                }
            });
        }
        defaultSecurityRequirements = openAPI.getSecurity() == null ? List.of() : List.copyOf(openAPI.getSecurity());

        List<Map<String, Object>> schemes = authenticationSchemes.values().stream()
                .map(AuthenticationScheme::templateData)
                .toList();
        additionalProperties.put("x_auth_schemes", schemes);
        // openapi-generator sets its own hasAuthMethods whenever a scheme is declared, so the configuration
        // template keys off a generator-owned property that only counts schemes an operation requires
        additionalProperties.put("x_has_auth_methods", !schemes.isEmpty());
        boolean hasOAuth2ClientCredentialsMethods = authenticationSchemes.values().stream().anyMatch(scheme -> scheme.type() == AuthenticationType.OAUTH2_CLIENT_CREDENTIALS);
        additionalProperties.put("hasOAuth2ClientCredentialsMethods", hasOAuth2ClientCredentialsMethods);

        if (hasOAuth2ClientCredentialsMethods) {
            String clientName = (String) additionalProperties.get("clientName");
            String invokerFolder = (sourceFolder + File.separator + invokerPackage).replace(".", File.separator);
            supportingFiles.add(new SupportingFile("oauth2.mustache", invokerFolder, clientName + "OAuth2.java"));
        }

        if (openAPI.getPaths() != null) {
            openAPI.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
                validateFormRequestBody(openAPI, path, operation);
                validateMediaTypes(openAPI, path, operation);
            }));

            boolean openApi32 = openAPI.getOpenapi() != null && openAPI.getOpenapi().startsWith("3.2");
            openAPI.getPaths().values().stream()
                    .flatMap(path -> path.readOperations().stream())
                    .forEach(operation -> discoverServerSentEventType(openAPI, operation, openApi32));
        }
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers)
    {
        CodegenOperation codegenOperation = super.fromOperation(path, httpMethod, operation, servers);
        List<SecurityRequirement> requirements = operation.getSecurity() == null ? defaultSecurityRequirements : operation.getSecurity();
        configureAuthentication(codegenOperation, requirements == null ? List.of() : requirements);
        return codegenOperation;
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs)
    {
        objs = super.postProcessAllModels(objs);
        // JSON wire names go into Java string literals; Mustache would HTML-escape them otherwise
        for (ModelsMap modelsMap : objs.values()) {
            for (ModelMap modelMap : modelsMap.getModels()) {
                CodegenModel model = modelMap.getModel();
                model.vars.forEach(property -> property.vendorExtensions.put("x_java_base_name", javaString(property.baseName)));
                if (model.discriminator != null) {
                    model.vendorExtensions.put("x_java_discriminator_property", javaString(model.discriminator.getPropertyBaseName()));
                    List<Map<String, String>> mappedModels = new ArrayList<>();
                    Set<CodegenDiscriminator.MappedModel> discriminatorModels = model.discriminator.getMappedModels();
                    for (CodegenDiscriminator.MappedModel mappedModel : discriminatorModels == null ? Set.<CodegenDiscriminator.MappedModel>of() : discriminatorModels) {
                        mappedModels.add(Map.of(
                                "modelName", mappedModel.getModelName(),
                                "mappingName", javaString(mappedModel.getMappingName())));
                    }
                    model.vendorExtensions.put("x_java_mapped_models", List.copyOf(mappedModels));
                }
            }
        }
        return objs;
    }

    @Override
    public CodegenParameter fromParameter(Parameter parameter, Set<String> imports)
    {
        CodegenParameter codegenParameter = super.fromParameter(parameter, imports);
        // form style parameters explode unless the contract says otherwise; openapi-generator records only an explicit flag
        boolean formStyle = parameter.getStyle() == null || parameter.getStyle() == Parameter.StyleEnum.FORM;
        boolean explode = parameter.getExplode() != null ? parameter.getExplode() : formStyle;
        codegenParameter.vendorExtensions.put("x_explode", explode);
        return codegenParameter;
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels)
    {
        objs = super.postProcessOperationsWithModels(objs, allModels);

        OperationMap operations = objs.getOperations();
        if (operations == null) {
            return objs;
        }

        Map<String, Map<String, String>> codecs = new LinkedHashMap<>();

        boolean usesGet = false;
        boolean usesPost = false;
        boolean usesPut = false;
        boolean usesDelete = false;
        boolean usesPatch = false;
        boolean usesBody = false;
        boolean usesJsonBody = false;
        boolean usesFormBody = false;
        boolean usesListType = false;
        boolean usesSafeJsonResponse = false;
        boolean usesSuccessfulJsonResponse = false;
        boolean usesStatusResponse = false;
        boolean usesSuccessfulStatusResponse = false;
        boolean usesQueryParams = false;
        boolean usesJoinedArrayParams = false;
        boolean usesServerSentEvents = false;
        boolean usesRawResponse = false;
        boolean hasAuth = false;
        boolean hasBearerAuth = false;
        boolean hasNonBearerAuth = false;

        List<CodegenOperation> ops = operations.getOperation();
        for (CodegenOperation operation : ops) {
            String configuredEventType = serverSentEventTypes.get(operation.operationIdOriginal);
            if (configuredEventType == null) {
                configuredEventType = serverSentEventTypes.get(operation.operationId);
            }
            String serverSentEventType = configuredEventType == null ? null : toModelName(configuredEventType);
            boolean serverSentEvents = serverSentEventType != null;
            operation.vendorExtensions.put("x_server_sent_events", serverSentEvents);
            if (serverSentEvents) {
                usesServerSentEvents = true;
                operation.vendorExtensions.put("x_server_sent_event_type", serverSentEventType);
                operation.returnType = "ServerSentEventStream<%s>".formatted(serverSentEventType);
                addModelImport(objs, serverSentEventType);
            }

            boolean rawResponse = rawResponseOperations.contains(operation.operationIdOriginal) || rawResponseOperations.contains(operation.operationId);
            operation.vendorExtensions.put("x_raw_response", rawResponse);
            if (rawResponse) {
                usesRawResponse = true;
                operation.returnType = "StreamingResponse";
            }

            String httpMethod = operation.httpMethod.toUpperCase(Locale.ENGLISH);
            operation.vendorExtensions.put("x_http_method", httpMethod);
            List<String> successCodes = operation.responses.stream()
                    .filter(AirliftHttpClientCodegen::isSuccessResponse)
                    .map(response -> response.code)
                    .toList();
            List<String> explicitSuccessCodes = successCodes.stream().filter(code -> code.matches("\\d+")).toList();
            boolean acceptsAny2xx = explicitSuccessCodes.isEmpty() || successCodes.stream().anyMatch(code -> code.matches("2[Xx]{2}"));
            operation.vendorExtensions.put("x_success_response_codes", String.join(", ", explicitSuccessCodes));
            operation.vendorExtensions.put("x_accepts_any_2xx", acceptsAny2xx);
            operation.vendorExtensions.put("x_success_response_condition", acceptsAny2xx
                    ? "statusCode >= 200 && statusCode < 300"
                    : explicitSuccessCodes.stream()
                      .map(code -> "statusCode == " + code)
                      .collect(Collectors.joining(" || ")));

            String methodLower = httpMethod.toLowerCase(Locale.ENGLISH);
            String prepareMethod = "prepare" + Character.toUpperCase(methodLower.charAt(0)) + methodLower.substring(1) + "()";
            operation.vendorExtensions.put("x_prepare_method", prepareMethod);

            switch (httpMethod) {
                case "GET" -> usesGet = true;
                case "POST" -> usesPost = true;
                case "PUT" -> usesPut = true;
                case "DELETE" -> usesDelete = true;
                case "PATCH" -> usesPatch = true;
                default -> throw new IllegalArgumentException("Operation '%s' uses unsupported HTTP method %s".formatted(operation.operationId, httpMethod));
            }

            validateParameterSerialization(operation);
            // wire names go into Java string literals; Mustache would HTML-escape them otherwise
            // (openapi-generator copies parameters into each per-location list, so mark every list)
            Stream.of(operation.allParams, operation.pathParams, operation.queryParams, operation.headerParams, operation.formParams)
                    .flatMap(List::stream)
                    .forEach(parameter -> parameter.vendorExtensions.put("x_java_base_name", javaString(parameter.baseName)));
            usesListType |= operation.queryParams.stream().anyMatch(parameter -> parameter.isArray) ||
                    operation.headerParams.stream().anyMatch(parameter -> parameter.isArray);
            usesJoinedArrayParams |= operation.headerParams.stream().anyMatch(parameter -> parameter.isArray) ||
                    operation.queryParams.stream().anyMatch(parameter -> parameter.isArray && !Boolean.TRUE.equals(parameter.vendorExtensions.get("x_explode")));

            boolean hasJsonBody = operation.bodyParam != null;
            boolean hasFormBody = operation.getHasFormParams();
            checkArgument(!(hasJsonBody && hasFormBody), "Operation '%s' has both JSON and form request bodies", operation.operationId);

            if (hasFormBody) {
                validateFormParameters(operation);
                usesListType |= operation.formParams.stream().anyMatch(parameter -> parameter.isArray);
            }

            boolean hasBody = hasJsonBody || hasFormBody;
            operation.vendorExtensions.put("x_has_body", hasBody);
            operation.vendorExtensions.put("x_has_json_body", hasJsonBody);
            operation.vendorExtensions.put("x_has_form_body", hasFormBody);
            if (hasBody) {
                usesBody = true;
            }
            if (hasJsonBody) {
                usesJsonBody = true;
            }
            if (hasFormBody) {
                usesFormBody = true;
            }

            boolean returnsVoid = "void".equals(operation.returnType) || operation.returnType == null;
            // one handler serves every accepted success status; a bodyless status beside a marker body
            // (API Builder's Accepted on a void method) becomes a void call, but a real payload cannot
            List<CodegenResponse> successResponses = operation.responses.stream().filter(AirliftHttpClientCodegen::isSuccessResponse).toList();
            boolean hasBodylessSuccess = successResponses.stream().anyMatch(response -> response.dataType == null && response.baseType == null);
            if (!returnsVoid && !rawResponse && hasBodylessSuccess) {
                boolean markerBodiesOnly = successResponses.stream()
                        .filter(response -> response.dataType != null || response.baseType != null)
                        .allMatch(response -> "Object".equals(response.dataType) && !response.isArray && !response.isMap);
                checkArgument(markerBodiesOnly, "Operation '%s' mixes success responses with and without a body", operation.operationId);
                operation.returnType = null;
                returnsVoid = true;
            }
            operation.vendorExtensions.put("x_returns_void", returnsVoid);
            if (serverSentEvents) {
                String codecName = toCodecName(serverSentEventType, null, null);
                operation.vendorExtensions.put("x_server_sent_event_codec", codecName);
                codecs.computeIfAbsent(codecName, _ -> buildCodecEntry(codecName, serverSentEventType, null, null));
            }
            else if (rawResponse) {
                // the caller reads the body; only the status is checked here
            }
            else if (returnsVoid) {
                if (acceptsAny2xx) {
                    usesSuccessfulStatusResponse = true;
                }
                else {
                    usesStatusResponse = true;
                }
            }
            else {
                if (acceptsAny2xx) {
                    usesSuccessfulJsonResponse = true;
                }
                else {
                    usesSafeJsonResponse = true;
                }
            }

            if (operation.getHasQueryParams()) {
                usesQueryParams = true;
            }

            if (Boolean.TRUE.equals(operation.vendorExtensions.get("x_has_auth_requirements"))) {
                hasAuth = true;
                boolean operationHasBearerAuth = Boolean.TRUE.equals(operation.vendorExtensions.get("x_has_bearer_auth"));
                boolean operationHasNonBearerAuth = Boolean.TRUE.equals(operation.vendorExtensions.get("x_has_non_bearer_auth"));
                hasBearerAuth |= operationHasBearerAuth;
                hasNonBearerAuth |= operationHasNonBearerAuth;
                operation.vendorExtensions.put("x_has_bearer_auth", operationHasBearerAuth);
                operation.vendorExtensions.put("x_has_non_bearer_auth", operationHasNonBearerAuth);
            }

            // Build deduplicated codec references
            if (!returnsVoid && !serverSentEvents && !rawResponse) {
                String codecName = toCodecName(operation.returnType, operation.returnContainer, operation.returnBaseType);
                operation.vendorExtensions.put("x_response_codec", codecName);
                codecs.computeIfAbsent(codecName, _ -> buildCodecEntry(
                        codecName,
                        operation.returnType,
                        operation.returnContainer,
                        operation.returnBaseType));
            }

            if (hasJsonBody) {
                String bodyType = operation.bodyParam.dataType;
                String codecName = toCodecName(bodyType, null, null);
                operation.vendorExtensions.put("x_request_codec", codecName);
                codecs.computeIfAbsent(codecName, _ -> buildCodecEntry(codecName, bodyType, null, null));
            }

            List<Map<String, String>> errorResponses = new ArrayList<>(operation.responses.stream()
                    .filter(response -> !isSuccessResponse(response))
                    .filter(response -> response.isDefault || response.code.matches("\\d{3}|[1-5][Xx]{2}|default"))
                    .filter(response -> response.dataType != null || response.baseType != null)
                    .sorted(Comparator.comparingInt(AirliftHttpClientCodegen::responseSpecificity))
                    .map(response -> buildErrorResponse(response, codecs))
                    .toList());
            if (operation.getHasDefaultResponse() &&
                    operation.defaultResponse != null &&
                    !operation.defaultResponse.isBlank() &&
                    !"null".equals(operation.defaultResponse) &&
                    !"void".equals(operation.defaultResponse) &&
                    errorResponses.stream().noneMatch(response -> "true".equals(response.get("condition")))) {
                errorResponses.add(buildErrorResponse("true", operation.defaultResponse, null, null, codecs));
            }
            operation.vendorExtensions.put("x_error_responses", List.copyOf(errorResponses));

            configureIdempotency(operation);
        }

        // Check for list/map codecs
        boolean usesListCodec = false;
        boolean usesMapCodec = false;
        for (Map<String, String> codec : codecs.values()) {
            String init = codec.get("init");
            if (init.startsWith("listJsonCodec")) {
                usesListCodec = true;
            }
            if (init.startsWith("mapJsonCodec")) {
                usesMapCodec = true;
            }
        }

        operations.put("x_codecs", List.copyOf(codecs.values()));

        // Feature flags on objs for import section (outside {{#operations}})
        objs.put("x_uses_get", usesGet);
        objs.put("x_uses_post", usesPost);
        objs.put("x_uses_put", usesPut);
        objs.put("x_uses_delete", usesDelete);
        objs.put("x_uses_patch", usesPatch);
        objs.put("x_uses_body", usesBody);
        objs.put("x_uses_json_body", usesJsonBody);
        objs.put("x_uses_form_body", usesFormBody);
        objs.put("x_uses_safe_json_response", usesSafeJsonResponse);
        objs.put("x_uses_successful_json_response", usesSuccessfulJsonResponse);
        objs.put("x_uses_status_response", usesStatusResponse);
        objs.put("x_uses_successful_status_response", usesSuccessfulStatusResponse);
        objs.put("x_uses_query_params", usesQueryParams);
        objs.put("x_uses_joined_array_params", usesJoinedArrayParams);
        objs.put("x_uses_json_codec", !codecs.isEmpty());
        objs.put("x_uses_server_sent_events", usesServerSentEvents);
        objs.put("x_uses_raw_response", usesRawResponse);
        objs.put("x_uses_streaming_response", usesServerSentEvents || usesRawResponse);
        objs.put("x_uses_list_codec", usesListCodec);
        objs.put("x_uses_list_type", usesListType || usesListCodec);
        objs.put("x_uses_map_codec", usesMapCodec);
        objs.put("x_has_auth", hasAuth);
        objs.put("x_has_bearer_auth", hasBearerAuth);
        objs.put("x_has_non_bearer_auth", hasNonBearerAuth);

        // Feature flags on operations for class body (inside {{#operations}})
        operations.put("x_has_auth", hasAuth);
        operations.put("x_has_bearer_auth", hasBearerAuth);
        operations.put("x_has_non_bearer_auth", hasNonBearerAuth);

        // Set global auth flag for supporting file templates (clientConfig, etc.)
        if (hasAuth) {
            additionalProperties.put("x_has_auth_methods", true);
        }
        if (hasBearerAuth) {
            additionalProperties.put("hasBearerAuthMethods", true);
        }

        return objs;
    }

    private void configureAuthentication(CodegenOperation operation, List<SecurityRequirement> requirements)
    {
        // an empty requirement object is a valid public alternative; when every alternative is empty
        // the operation is public and must not depend on credentials the configuration does not generate
        if (requirements.stream().allMatch(Map::isEmpty)) {
            operation.vendorExtensions.put("x_has_auth_requirements", false);
            operation.vendorExtensions.put("x_auth_requirements", List.of());
            return;
        }

        // the alternatives are a disjunction, so their order is free; an empty alternative is always satisfied
        // and must be tried last, otherwise the client would call anonymously despite configured credentials
        requirements.forEach(requirement -> requireNonNull(requirement, "security requirement is null"));
        List<SecurityRequirement> orderedRequirements = new ArrayList<>();
        requirements.stream().filter(requirement -> !requirement.isEmpty()).forEach(orderedRequirements::add);
        requirements.stream().filter(Map::isEmpty).forEach(orderedRequirements::add);

        boolean hasBearer = false;
        boolean hasNonBearer = false;
        List<Map<String, Object>> alternatives = new ArrayList<>();
        for (int index = 0; index < orderedRequirements.size(); index++) {
            SecurityRequirement requirement = orderedRequirements.get(index);
            List<Map<String, Object>> schemes = new ArrayList<>();
            List<String> conditions = new ArrayList<>();
            int authorizationSchemes = 0;
            String bearerSchemeName = null;
            for (Map.Entry<String, List<String>> entry : requirement.entrySet()) {
                AuthenticationScheme scheme = authenticationSchemes.get(entry.getKey());
                checkArgument(scheme != null, "Security requirement references unknown scheme '%s'", entry.getKey());
                if (!entry.getValue().isEmpty() && scheme.type() != AuthenticationType.OAUTH2_CLIENT_CREDENTIALS) {
                    throw new IllegalArgumentException("Scopes are only supported for OAuth2 bearer scheme '%s'".formatted(scheme.name()));
                }
                if (scheme.isBearer() || scheme.type() == AuthenticationType.BASIC) {
                    authorizationSchemes++;
                }
                if (scheme.isBearer()) {
                    bearerSchemeName = scheme.name();
                    hasBearer = true;
                }
                else {
                    hasNonBearer = true;
                }
                schemes.add(scheme.templateData());
                conditions.add(scheme.configuredCondition());
            }
            checkArgument(authorizationSchemes <= 1, "Security requirement for operation '%s' contains multiple Authorization header schemes", operation.operationId);
            // every credential is applied with setHeader, so two schemes on one header name would drop one of them
            Set<String> headerNames = new LinkedHashSet<>();
            for (String name : requirement.keySet()) {
                AuthenticationScheme scheme = authenticationSchemes.get(name);
                String header = scheme.type() == AuthenticationType.HEADER_API_KEY ? scheme.headerName() : "Authorization";
                checkArgument(headerNames.add(header.toLowerCase(Locale.ENGLISH)),
                        "Security requirement for operation '%s' applies several schemes to the %s header",
                        operation.operationId,
                        header);
            }

            Map<String, Object> alternative = new LinkedHashMap<>();
            alternative.put("index", index);
            alternative.put("first", index == 0);
            alternative.put("condition", conditions.isEmpty() ? "true" : String.join(" && ", conditions));
            alternative.put("schemes", List.copyOf(schemes));
            alternative.put("hasBearer", bearerSchemeName != null);
            alternative.put("bearerSchemeName", bearerSchemeName == null ? "" : javaString(bearerSchemeName));
            alternatives.add(Map.copyOf(alternative));
        }

        operation.hasAuthMethods = true;
        operation.vendorExtensions.put("x_has_auth_requirements", true);
        operation.vendorExtensions.put("x_auth_requirements", List.copyOf(alternatives));
        operation.vendorExtensions.put("x_has_bearer_auth", hasBearer);
        operation.vendorExtensions.put("x_has_non_bearer_auth", hasNonBearer);
    }

    private static Map<String, Set<String>> collectRequiredScopes(OpenAPI openAPI)
    {
        Map<String, Set<String>> requiredScopes = new LinkedHashMap<>();
        List<SecurityRequirement> defaultRequirements = openAPI.getSecurity() == null ? List.of() : openAPI.getSecurity();
        // the default requirements only apply to operations that do not declare their own,
        // so a default every operation overrides must not shape the client configuration
        if (openAPI.getPaths() != null) {
            openAPI.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    addRequiredScopes(requiredScopes, operation.getSecurity() == null ? defaultRequirements : operation.getSecurity())));
        }
        return requiredScopes;
    }

    private static void addRequiredScopes(Map<String, Set<String>> requiredScopes, List<SecurityRequirement> requirements)
    {
        if (requirements == null) {
            return;
        }
        requirements.forEach(requirement -> requirement.forEach((name, scopes) -> {
            Set<String> operationScopes = new LinkedHashSet<>(scopes);
            Set<String> previousScopes = requiredScopes.putIfAbsent(name, operationScopes);
            checkArgument(
                    previousScopes == null || previousScopes.equals(operationScopes),
                    "Security scheme '%s' is used with inconsistent required scopes: %s and %s",
                    name,
                    previousScopes,
                    operationScopes);
        }));
    }

    private static AuthenticationScheme authenticationScheme(String name, SecurityScheme scheme, List<String> requiredScopes)
    {
        requireNonNull(name, "security scheme name is null");
        requireNonNull(scheme, "security scheme is null");
        if (scheme.getType() == SecurityScheme.Type.HTTP && "bearer".equalsIgnoreCase(scheme.getScheme())) {
            checkArgument(requiredScopes.isEmpty(), "Non-OAuth2 bearer scheme '%s' cannot require scopes", name);
            return new AuthenticationScheme(name, AuthenticationType.BEARER, null, null, requiredScopes);
        }
        if (scheme.getType() == SecurityScheme.Type.HTTP && "basic".equalsIgnoreCase(scheme.getScheme())) {
            checkArgument(requiredScopes.isEmpty(), "Basic scheme '%s' cannot require scopes", name);
            return new AuthenticationScheme(name, AuthenticationType.BASIC, null, null, requiredScopes);
        }
        if (scheme.getType() == SecurityScheme.Type.APIKEY) {
            checkArgument(requiredScopes.isEmpty(), "API key scheme '%s' cannot require scopes", name);
            checkArgument(scheme.getIn() == SecurityScheme.In.HEADER, "API key scheme '%s' uses unsupported location '%s'; only header API keys are supported", name, scheme.getIn());
            checkArgument(scheme.getName() != null && !scheme.getName().isBlank(), "Header API key scheme '%s' has no header name", name);
            checkArgument(HTTP_TOKEN.matcher(scheme.getName()).matches(), "Header API key scheme '%s' has an invalid header name '%s'", name, scheme.getName());
            return new AuthenticationScheme(name, AuthenticationType.HEADER_API_KEY, scheme.getName(), null, requiredScopes);
        }
        if (scheme.getType() == SecurityScheme.Type.OAUTH2 && scheme.getFlows() != null && scheme.getFlows().getClientCredentials() != null) {
            String tokenUrl = scheme.getFlows().getClientCredentials().getTokenUrl();
            checkArgument(tokenUrl != null && !tokenUrl.isBlank(), "OAuth2 client-credentials scheme '%s' has no token URL", name);
            Map<String, String> availableScopes = scheme.getFlows().getClientCredentials().getScopes();
            requiredScopes.forEach(scope -> checkArgument(
                    availableScopes != null && availableScopes.containsKey(scope),
                    "OAuth2 client-credentials scheme '%s' requires undeclared scope '%s'",
                    name,
                    scope));
            Object authenticationMethod = scheme.getExtensions() == null ? null : scheme.getExtensions().get("x-airlift-token-endpoint-authentication-method");
            checkArgument(authenticationMethod == null || "client_secret_basic".equals(authenticationMethod),
                    "OAuth2 client-credentials scheme '%s' uses unsupported token endpoint authentication method",
                    name);
            return new AuthenticationScheme(name, AuthenticationType.OAUTH2_CLIENT_CREDENTIALS, null, tokenUrl, requiredScopes);
        }
        throw new IllegalArgumentException("Unsupported security scheme '%s'".formatted(name));
    }

    private static void validateAuthenticationSchemeNames(Set<String> names)
    {
        // the property name is always suffixed, so a keyword is harmless but a leading digit is not an identifier
        names.forEach(name -> checkArgument(
                !Character.isDigit(AuthenticationScheme.toPropertyName(name).charAt(0)),
                "Security scheme name '%s' does not generate a valid Java identifier",
                name));
        validateDistinctNormalizedNames(names, "Java property", AuthenticationScheme::toPropertyName);
        validateDistinctNormalizedNames(names, "Java method", AuthenticationScheme::toMethodName);
        validateDistinctNormalizedNames(names, "configuration property", AuthenticationScheme::toConfigName);
    }

    private static void validateDistinctNormalizedNames(Set<String> names, String identifierType, Function<String, String> normalizer)
    {
        Map<String, String> normalizedNames = new LinkedHashMap<>();
        for (String name : names) {
            String normalizedName = normalizer.apply(name);
            String previous = normalizedNames.putIfAbsent(normalizedName, name);
            checkArgument(
                    previous == null || previous.equals(name),
                    "Security scheme names '%s' and '%s' generate the same %s '%s'",
                    previous,
                    name,
                    identifierType,
                    normalizedName);
        }
    }

    private static String javaString(String value)
    {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private enum AuthenticationType
    {
        BEARER,
        BASIC,
        HEADER_API_KEY,
        OAUTH2_CLIENT_CREDENTIALS,
    }

    private record AuthenticationScheme(String name, AuthenticationType type, String headerName, String tokenUrl, List<String> requiredScopes)
    {
        private AuthenticationScheme
        {
            requireNonNull(name, "name is null");
            requireNonNull(type, "type is null");
            requiredScopes = List.copyOf(requiredScopes);
        }

        private boolean isBearer()
        {
            return type == AuthenticationType.BEARER || type == AuthenticationType.OAUTH2_CLIENT_CREDENTIALS;
        }

        private String configuredCondition()
        {
            return switch (type) {
                case BEARER, OAUTH2_CLIENT_CREDENTIALS -> "credentials.hasBearerToken(\"%s\")".formatted(javaString(name));
                case BASIC -> "credentials.hasBasicAuth(\"%s\")".formatted(javaString(name));
                case HEADER_API_KEY -> "credentials.hasHeaderApiKey(\"%s\")".formatted(javaString(name));
            };
        }

        private Map<String, Object> templateData()
        {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", javaString(name));
            data.put("propertyName", toPropertyName(name));
            data.put("methodName", toMethodName(name));
            data.put("configName", toConfigName(name));
            data.put("headerName", headerName == null ? "" : javaString(headerName));
            data.put("tokenUrl", tokenUrl == null ? "" : javaString(tokenUrl));
            data.put("requiredScopes", requiredScopes.stream()
                    .map(scope -> Map.of("value", javaString(scope)))
                    .toList());
            data.put("hasRequiredScopes", !requiredScopes.isEmpty());
            data.put("isBearer", isBearer());
            data.put("isBasic", type == AuthenticationType.BASIC);
            data.put("isHeaderApiKey", type == AuthenticationType.HEADER_API_KEY);
            data.put("isOAuth2ClientCredentials", type == AuthenticationType.OAUTH2_CLIENT_CREDENTIALS);
            return Map.copyOf(data);
        }

        private static String toPropertyName(String name)
        {
            String camelized = camelize(name.replaceAll("[^A-Za-z0-9]+", "_"), LOWERCASE_FIRST_LETTER);
            return camelized.isEmpty() ? "credentials" : camelized;
        }

        private static String toMethodName(String name)
        {
            String camelized = camelize(name.replaceAll("[^A-Za-z0-9]+", "_"));
            return camelized.isEmpty() ? "Credentials" : camelized;
        }

        private static String toConfigName(String name)
        {
            return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                    .replaceAll("[^A-Za-z0-9]+", "-")
                    .replaceAll("^-|-$", "")
                    .toLowerCase(Locale.ENGLISH);
        }
    }

    private void discoverServerSentEventType(OpenAPI openAPI, Operation operation, boolean openApi32)
    {
        if (operation.getResponses() == null) {
            return;
        }

        operation.getResponses().entrySet().stream()
                .filter(entry -> entry.getKey().matches("2\\d{2}|2[Xx]{2}"))
                .map(entry -> ModelUtils.getReferencedApiResponse(openAPI, entry.getValue()))
                .filter(response -> response.getContent() != null)
                .flatMap(response -> response.getContent().entrySet().stream())
                .filter(entry -> isEventStreamMediaType(entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(mediaType -> discoverServerSentEventType(operation, mediaType, openApi32));
    }

    private void discoverServerSentEventType(Operation operation, MediaType mediaType, boolean openApi32)
    {
        Object eventSchema = mediaType.getExtensions() == null ? null : mediaType.getExtensions().get(EVENT_SCHEMA_EXTENSION);
        if (eventSchema == null) {
            if (openApi32 && mediaType.getSchema() == null) {
                throw new IllegalArgumentException(
                        "OpenAPI 3.2 itemSchema metadata for operation '%s' is not available; typed server-sent events cannot be generated"
                                .formatted(operation.getOperationId()));
            }
            // an untyped event stream would otherwise be generated as a JSON operation with the wrong wire contract
            throw new IllegalArgumentException(
                    "Operation '%s' returns text/event-stream without %s; typed server-sent events cannot be generated"
                            .formatted(operation.getOperationId(), EVENT_SCHEMA_EXTENSION));
        }

        String reference = switch (eventSchema) {
            case Schema<?> schema -> schema.get$ref();
            case Map<?, ?> values when values.get("$ref") instanceof String value -> value;
            default -> null;
        };
        if (reference == null || reference.isBlank() || !reference.contains("/")) {
            throw new IllegalArgumentException(
                    "%s for operation '%s' must be a schema reference".formatted(EVENT_SCHEMA_EXTENSION, operation.getOperationId()));
        }
        if (operation.getOperationId() == null || operation.getOperationId().isBlank()) {
            throw new IllegalArgumentException("Typed server-sent event operations must declare an operationId");
        }

        String modelName = reference.substring(reference.lastIndexOf('/') + 1);
        String previous = serverSentEventTypes.putIfAbsent(operation.getOperationId(), modelName);
        if (previous != null && !previous.equals(modelName)) {
            throw new IllegalArgumentException(
                    "Operation '%s' declares multiple server-sent event schemas".formatted(operation.getOperationId()));
        }
    }

    private void addModelImport(OperationsMap objs, String modelName)
    {
        Map<String, String> modelImport = Map.of("import", toModelImport(modelName));
        List<Map<String, String>> imports = new ArrayList<>(objs.getImports());
        if (!imports.contains(modelImport)) {
            imports.add(modelImport);
            objs.setImports(imports);
        }
    }

    private static boolean isSuccessResponse(CodegenResponse response)
    {
        return response.is2xx || response.code.matches("2\\d{2}|2[Xx]{2}");
    }

    private static int responseSpecificity(CodegenResponse response)
    {
        if (response.code.matches("\\d{3}")) {
            return 0;
        }
        if (response.code.matches("[1-5][Xx]{2}")) {
            return 1;
        }
        return 2;
    }

    private Map<String, String> buildErrorResponse(CodegenResponse response, Map<String, Map<String, String>> codecs)
    {
        String responseType = response.dataType != null ? response.dataType : response.baseType;
        return buildErrorResponse(response.isDefault ? "true" : responseCondition(response.code), responseType, response.containerType, response.baseType, codecs);
    }

    private Map<String, String> buildErrorResponse(
            String condition,
            String responseType,
            String containerType,
            String baseType,
            Map<String, Map<String, String>> codecs)
    {
        String codecName = toCodecName(responseType, containerType, baseType);
        codecs.computeIfAbsent(codecName, _ -> buildCodecEntry(codecName, responseType, containerType, baseType));
        return Map.of(
                "condition", condition,
                "codec", codecName);
    }

    private static String responseCondition(String responseCode)
    {
        if (responseCode.matches("\\d{3}")) {
            return "statusCode == " + responseCode;
        }
        if (responseCode.matches("[1-5][Xx]{2}")) {
            int lowerBound = Character.digit(responseCode.charAt(0), 10) * 100;
            return "statusCode >= %d && statusCode < %d".formatted(lowerBound, lowerBound + 100);
        }
        return "true";
    }

    private static void configureIdempotency(CodegenOperation operation)
    {
        Object extension = operation.vendorExtensions.get("x-airlift-idempotency");
        if (extension == null) {
            operation.vendorExtensions.put("x_idempotency_key", "null");
            return;
        }
        if (!(extension instanceof Map<?, ?> values) || !(values.get("header") instanceof String headerName) || headerName.isBlank()) {
            throw new IllegalArgumentException("x-airlift-idempotency must contain a nonblank string header");
        }

        CodegenParameter header = operation.headerParams.stream()
                .filter(parameter -> headerName.equalsIgnoreCase(parameter.baseName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "x-airlift-idempotency header '%s' is not an operation header parameter".formatted(headerName)));
        if (!header.isString) {
            throw new IllegalArgumentException(
                    "x-airlift-idempotency header '%s' must be a string operation header parameter".formatted(headerName));
        }
        operation.vendorExtensions.put("x_idempotency_key", header.paramName);
    }

    // generated clients send JSON or form bodies and decode JSON or typed event streams; any other
    // success body is handed back as the raw streaming response, and any other request body is refused
    private void validateMediaTypes(OpenAPI openAPI, String path, Operation operation)
    {
        String operationName = operation.getOperationId() == null ? path : operation.getOperationId();

        RequestBody requestBody = ModelUtils.getReferencedRequestBody(openAPI, operation.getRequestBody());
        if (requestBody != null && requestBody.getContent() != null && !requestBody.getContent().isEmpty()) {
            Set<String> mediaTypes = requestBody.getContent().keySet();
            checkArgument(mediaTypes.stream().anyMatch(type -> isJsonMediaType(type) || isFormMediaType(type)),
                    "Operation '%s' declares unsupported request body media types %s",
                    operationName,
                    mediaTypes);
        }

        if (operation.getResponses() == null) {
            return;
        }
        List<ApiResponse> successResponses = operation.getResponses().entrySet().stream()
                .filter(entry -> entry.getKey().matches("2\\d{2}|2[Xx]{2}"))
                .map(entry -> ModelUtils.getReferencedApiResponse(openAPI, entry.getValue()))
                .toList();
        boolean json = false;
        boolean eventStream = false;
        boolean raw = false;
        Set<Schema<?>> jsonSchemas = new LinkedHashSet<>();
        for (ApiResponse response : successResponses) {
            if (response.getContent() == null || response.getContent().isEmpty()) {
                continue;
            }
            boolean decodable = false;
            for (Map.Entry<String, MediaType> entry : response.getContent().entrySet()) {
                if (isJsonMediaType(entry.getKey())) {
                    json = true;
                    decodable = true;
                    jsonSchemas.add(entry.getValue().getSchema());
                }
                else if (isEventStreamMediaType(entry.getKey())) {
                    eventStream = true;
                    decodable = true;
                }
            }
            if (!decodable) {
                raw = true;
            }
        }
        boolean decodable = json || eventStream;
        checkArgument(!(decodable && raw), "Operation '%s' mixes decodable and raw success response media types", operationName);
        checkArgument(!(json && eventStream), "Operation '%s' mixes JSON and event stream success responses", operationName);
        // one codec decodes every accepted success status, so the declared bodies must share a schema
        checkArgument(jsonSchemas.size() <= 1, "Operation '%s' declares differing success response schemas", operationName);
        if (raw) {
            checkArgument(operation.getOperationId() != null && !operation.getOperationId().isBlank(),
                    "Operation %s returns a raw response and must declare an operationId",
                    path);
            rawResponseOperations.add(operation.getOperationId());
        }
    }

    // the runtime JSON handlers accept application/json in UTF-8 only, so that is the only decodable body here
    private static boolean isJsonMediaType(String mediaType)
    {
        if (!baseMediaType(mediaType).equals("application/json")) {
            return false;
        }
        String charset = mediaTypeParameter(mediaType, "charset");
        return charset == null || charset.equalsIgnoreCase("utf-8") || charset.equalsIgnoreCase("utf8");
    }

    private static String mediaTypeParameter(String mediaType, String name)
    {
        String[] parts = mediaType.split(";");
        for (int index = 1; index < parts.length; index++) {
            String[] parameter = parts[index].trim().split("=", 2);
            if (parameter.length == 2 && parameter[0].trim().equalsIgnoreCase(name)) {
                return parameter[1].trim().replace("\"", "");
            }
        }
        return null;
    }

    private static boolean isEventStreamMediaType(String mediaType)
    {
        return baseMediaType(mediaType).equals(EVENT_STREAM_MEDIA_TYPE);
    }

    private static String baseMediaType(String mediaType)
    {
        return mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ENGLISH);
    }

    private static boolean isFormMediaType(String mediaType)
    {
        return baseMediaType(mediaType).equals("application/x-www-form-urlencoded");
    }

    private static void validateFormRequestBody(OpenAPI openAPI, String path, Operation operation)
    {
        RequestBody requestBody = ModelUtils.getReferencedRequestBody(openAPI, operation.getRequestBody());
        if (requestBody == null || requestBody.getContent() == null) {
            return;
        }

        List<Map.Entry<String, MediaType>> formContent = requestBody.getContent().entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ENGLISH).startsWith("application/x-www-form-urlencoded"))
                .toList();
        if (formContent.isEmpty()) {
            return;
        }

        String operationName = operation.getOperationId() == null ? path : operation.getOperationId();
        checkArgument(requestBody.getContent().size() == 1 &&
                        "application/x-www-form-urlencoded".equalsIgnoreCase(formContent.getFirst().getKey()),
                "Form operation '%s' must declare only application/x-www-form-urlencoded content",
                operationName);

        MediaType mediaType = formContent.getFirst().getValue();
        checkArgument(mediaType.getEncoding() == null || mediaType.getEncoding().isEmpty(),
                "Form operation '%s' uses unsupported custom encoding",
                operationName);

        Schema<?> schema = ModelUtils.getReferencedSchema(openAPI, mediaType.getSchema());
        checkArgument(schema != null, "Form operation '%s' has no request schema", operationName);
        checkArgument(ModelUtils.isObjectSchema(schema) && !ModelUtils.isMapSchema(schema),
                "Form operation '%s' must use a fixed object schema",
                operationName);
        checkArgument(!ModelUtils.isComposedSchema(schema),
                "Form operation '%s' uses an unsupported composed request schema",
                operationName);
        checkArgument(schema.getAdditionalProperties() == null || Boolean.FALSE.equals(schema.getAdditionalProperties()),
                "Form operation '%s' uses unsupported additionalProperties",
                operationName);

        if (schema.getProperties() != null) {
            schema.getProperties().forEach((name, property) -> validateFormProperty(openAPI, operationName, name, property));
        }
    }

    private static void validateFormProperty(OpenAPI openAPI, String operationName, String name, Schema<?> property)
    {
        Schema<?> schema = ModelUtils.getReferencedSchema(openAPI, property);
        checkArgument(schema != null, "Form field '%s' in operation '%s' has no schema", name, operationName);
        checkArgument(!ModelUtils.isComposedSchema(schema),
                "Form field '%s' in operation '%s' uses an unsupported composed schema",
                name,
                operationName);

        if (ModelUtils.isArraySchema(schema)) {
            Schema<?> items = ModelUtils.getReferencedSchema(openAPI, schema.getItems());
            checkArgument(items != null, "Form array field '%s' in operation '%s' has no item schema", name, operationName);
            validateFormScalar(openAPI, operationName, name, items);
            return;
        }
        validateFormScalar(openAPI, operationName, name, schema);
    }

    private static void validateFormScalar(OpenAPI openAPI, String operationName, String name, Schema<?> schema)
    {
        checkArgument(!ModelUtils.isArraySchema(schema) &&
                        !ModelUtils.isMapSchema(schema) &&
                        !ModelUtils.isObjectSchema(schema) &&
                        !ModelUtils.isComposedSchema(schema) &&
                        !ModelUtils.isBinarySchema(schema) &&
                        !ModelUtils.isByteArraySchema(schema) &&
                        !ModelUtils.isFileSchema(schema) &&
                        !ModelUtils.isFreeFormObject(schema, openAPI) &&
                        !ModelUtils.isAnyType(schema),
                "Form field '%s' in operation '%s' must be a scalar or an array of scalars",
                name,
                operationName);
    }

    private static void validateParameterSerialization(CodegenOperation operation)
    {
        checkArgument(operation.cookieParams.isEmpty(), "Operation '%s' uses unsupported cookie parameters", operation.operationId);
        operation.pathParams.stream()
                .filter(parameter -> parameter.isArray)
                .forEach(parameter -> {
                    throw new IllegalArgumentException("Path parameter '%s' in operation '%s' must not be an array".formatted(parameter.baseName, operation.operationId));
                });
        // arrays are sent one value per query parameter (form style, exploded) or comma-joined
        // (form style, not exploded; simple style headers); other styles have no generated form
        operation.queryParams.stream()
                .filter(parameter -> parameter.isArray)
                .forEach(parameter -> checkArgument(
                        parameter.style == null || "form".equals(parameter.style),
                        "Query parameter '%s' in operation '%s' uses unsupported array style '%s'",
                        parameter.baseName,
                        operation.operationId,
                        parameter.style));
        operation.headerParams.stream()
                .filter(parameter -> parameter.isArray)
                .forEach(parameter -> checkArgument(
                        parameter.style == null || "simple".equals(parameter.style),
                        "Header parameter '%s' in operation '%s' uses unsupported array style '%s'",
                        parameter.baseName,
                        operation.operationId,
                        parameter.style));
    }

    private static void validateFormParameters(CodegenOperation operation)
    {
        checkArgument(!operation.isMultipart, "Operation '%s' uses unsupported multipart form data", operation.operationId);
        operation.formParams.forEach(parameter -> {
            if (parameter.isArray) {
                checkArgument(parameter.items != null && isSupportedFormScalar(parameter.items),
                        "Form field '%s' in operation '%s' must contain scalar values",
                        parameter.baseName,
                        operation.operationId);
            }
            else {
                checkArgument(isSupportedFormScalar(parameter),
                        "Form field '%s' in operation '%s' must be a scalar",
                        parameter.baseName,
                        operation.operationId);
            }
        });
    }

    private static boolean isSupportedFormScalar(CodegenParameter parameter)
    {
        return parameter.isEnum || parameter.isEnumRef ||
                (parameter.isPrimitiveType && !parameter.isBinary && !parameter.isByteArray && !parameter.isFile);
    }

    private static boolean isSupportedFormScalar(org.openapitools.codegen.CodegenProperty property)
    {
        return property.isEnum || property.isEnumRef ||
                (property.isPrimitiveType && !property.isBinary && !property.isByteArray && !property.isFile && !property.isArray && !property.isMap);
    }

    private Map<String, String> buildCodecEntry(String name, String type, String container, String baseType)
    {
        String init;
        if ("array".equals(container) || "list".equals(container)) {
            init = "listJsonCodec(jsonCodec(%s.class))".formatted(baseType);
        }
        else if ("map".equals(container)) {
            init = "mapJsonCodec(String.class, jsonCodec(%s.class))".formatted(baseType);
        }
        else {
            init = "jsonCodec(%s.class)".formatted(type);
        }

        return Map.of(
                "name", name,
                "type", type,
                "init", init);
    }

    private String toCodecName(String type, String container, String baseType)
    {
        String baseName;
        if (container != null && baseType != null) {
            baseName = baseType + "_" + container;
        }
        else {
            baseName = type;
        }
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, baseName) + "_CODEC";
    }
}
