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
import io.swagger.v3.oas.models.parameters.Parameter;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.CodegenSecurity;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.JavaClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static org.openapitools.codegen.utils.StringUtils.camelize;

public class AirliftHttpClientCodegen
        extends JavaClientCodegen
{
    private static final Pattern STATUS_CODE = Pattern.compile("\\d{3}");
    private static final Pattern STATUS_CODE_RANGE = Pattern.compile("[1-5][Xx]{2}");

    public static final String GENERATOR_NAME = "airlift-http-client";
    public static final String IDEMPOTENCY_EXTENSION = "idempotencyExtension";
    private static final String DEFAULT_IDEMPOTENCY_EXTENSION = "x-airlift-idempotency";

    // whether an array parameter is sent one value per occurrence; recorded per parameter because openapi-generator keeps only an explicit flag
    private static final String EXPLODE_EXTENSION = "x_explode";

    private String idempotencyExtension = DEFAULT_IDEMPOTENCY_EXTENSION;

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

        if (additionalProperties.containsKey(IDEMPOTENCY_EXTENSION)) {
            idempotencyExtension = String.valueOf(additionalProperties.get(IDEMPOTENCY_EXTENSION));
            checkArgument(idempotencyExtension.startsWith("x-"), "%s must name an x- extension: %s", IDEMPOTENCY_EXTENSION, idempotencyExtension);
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
    public CodegenParameter fromParameter(Parameter parameter, Set<String> imports)
    {
        CodegenParameter codegenParameter = super.fromParameter(parameter, imports);
        // form style parameters explode unless the contract says otherwise; openapi-generator records only an explicit flag
        boolean formStyle = parameter.getStyle() == null || parameter.getStyle() == Parameter.StyleEnum.FORM;
        boolean explode = parameter.getExplode() != null ? parameter.getExplode() : formStyle;
        codegenParameter.vendorExtensions.put(EXPLODE_EXTENSION, explode);
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
        boolean usesSafeJsonResponse = false;
        boolean usesSuccessfulJsonResponse = false;
        boolean usesStatusResponse = false;
        boolean usesSuccessfulStatusResponse = false;
        boolean usesQueryParams = false;
        boolean usesArrayParams = false;
        boolean usesJoinedArrayParams = false;
        boolean hasAuth = false;
        boolean hasBearerAuth = false;
        boolean hasNonBearerAuth = false;

        List<CodegenOperation> ops = operations.getOperation();
        for (CodegenOperation operation : ops) {
            String httpMethod = operation.httpMethod.toUpperCase(Locale.ENGLISH);
            operation.vendorExtensions.put("x_http_method", httpMethod);
            List<String> successCodes = operation.responses.stream()
                    .filter(AirliftHttpClientCodegen::isSuccessResponse)
                    .map(response -> response.code)
                    .toList();
            List<String> explicitSuccessCodes = successCodes.stream().filter(code -> STATUS_CODE.matcher(code).matches()).toList();
            boolean acceptsAny2xx = explicitSuccessCodes.isEmpty() || successCodes.stream().anyMatch(code -> STATUS_CODE_RANGE.matcher(code).matches());
            operation.vendorExtensions.put("x_success_response_codes", String.join(", ", explicitSuccessCodes));
            operation.vendorExtensions.put("x_accepts_any_2xx", acceptsAny2xx);

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
            // the method signature declares every parameter, so any array parameter needs the List import
            usesArrayParams |= operation.allParams.stream().anyMatch(parameter -> parameter.isArray);
            usesJoinedArrayParams |= operation.queryParams.stream()
                    .anyMatch(parameter -> parameter.isArray && !Boolean.TRUE.equals(parameter.vendorExtensions.get(EXPLODE_EXTENSION)));

            boolean hasBody = operation.bodyParam != null;
            operation.vendorExtensions.put("x_has_body", hasBody);
            if (hasBody) {
                usesBody = true;
            }

            boolean returnsVoid = "void".equals(operation.returnType) || operation.returnType == null;
            operation.vendorExtensions.put("x_returns_void", returnsVoid);
            if (returnsVoid) {
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

            if (operation.hasAuthMethods) {
                hasAuth = true;
                boolean operationHasBearerAuth = false;
                boolean operationHasNonBearerAuth = false;
                for (CodegenSecurity auth : operation.authMethods) {
                    if (Boolean.TRUE.equals(auth.isBasicBearer)) {
                        hasBearerAuth = true;
                        operationHasBearerAuth = true;
                    }
                    else {
                        hasNonBearerAuth = true;
                        operationHasNonBearerAuth = true;
                    }
                }
                operation.vendorExtensions.put("x_has_bearer_auth", operationHasBearerAuth);
                operation.vendorExtensions.put("x_has_non_bearer_auth", operationHasNonBearerAuth);
            }

            // Build deduplicated codec references
            if (!returnsVoid) {
                String codecName = toCodecName(operation.returnType, operation.returnContainer, operation.returnBaseType);
                operation.vendorExtensions.put("x_response_codec", codecName);
                codecs.computeIfAbsent(codecName, _ -> buildCodecEntry(
                        codecName,
                        operation.returnType,
                        operation.returnContainer,
                        operation.returnBaseType));
            }

            if (hasBody) {
                String bodyType = operation.bodyParam.dataType;
                String codecName = toCodecName(bodyType, null, null);
                operation.vendorExtensions.put("x_request_codec", codecName);
                codecs.computeIfAbsent(codecName, _ -> buildCodecEntry(codecName, bodyType, null, null));
            }

            List<Map<String, String>> errorResponses = operation.responses.stream()
                    .filter(response -> !isSuccessResponse(response))
                    .filter(response -> response.isDefault || STATUS_CODE.matcher(response.code).matches() || STATUS_CODE_RANGE.matcher(response.code).matches())
                    .filter(response -> response.dataType != null || response.baseType != null)
                    .sorted(Comparator.comparingInt(AirliftHttpClientCodegen::responseSpecificity))
                    .map(response -> buildErrorResponse(response, codecs))
                    .toList();
            operation.vendorExtensions.put("x_error_responses", errorResponses);

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
        objs.put("x_uses_safe_json_response", usesSafeJsonResponse);
        objs.put("x_uses_successful_json_response", usesSuccessfulJsonResponse);
        objs.put("x_uses_status_response", usesStatusResponse);
        objs.put("x_uses_successful_status_response", usesSuccessfulStatusResponse);
        objs.put("x_uses_query_params", usesQueryParams);
        objs.put("x_uses_joined_array_params", usesJoinedArrayParams);
        objs.put("x_uses_list_type", usesListCodec || usesArrayParams);
        objs.put("x_uses_list_codec", usesListCodec);
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
            additionalProperties.put("hasAuthMethods", true);
        }
        if (hasBearerAuth) {
            additionalProperties.put("hasBearerAuthMethods", true);
        }

        return objs;
    }

    private static boolean isSuccessResponse(CodegenResponse response)
    {
        return response.is2xx;
    }

    private static int responseSpecificity(CodegenResponse response)
    {
        if (STATUS_CODE.matcher(response.code).matches()) {
            return 0;
        }
        if (STATUS_CODE_RANGE.matcher(response.code).matches()) {
            return 1;
        }
        return 2;
    }

    private Map<String, String> buildErrorResponse(CodegenResponse response, Map<String, Map<String, String>> codecs)
    {
        String responseType = response.dataType != null ? response.dataType : response.baseType;
        String codecName = toCodecName(responseType, response.containerType, response.baseType);
        codecs.computeIfAbsent(codecName, _ -> buildCodecEntry(codecName, responseType, response.containerType, response.baseType));
        return Map.of(
                "condition", response.isDefault ? "true" : responseCondition(response.code),
                "codec", codecName);
    }

    private static String responseCondition(String responseCode)
    {
        if (STATUS_CODE.matcher(responseCode).matches()) {
            return "statusCode == " + responseCode;
        }
        if (STATUS_CODE_RANGE.matcher(responseCode).matches()) {
            int lowerBound = Character.digit(responseCode.charAt(0), 10) * 100;
            return "statusCode >= %d && statusCode < %d".formatted(lowerBound, lowerBound + 100);
        }
        return "true";
    }

    private void configureIdempotency(CodegenOperation operation)
    {
        Object extension = operation.vendorExtensions.get(idempotencyExtension);
        if (extension == null) {
            operation.vendorExtensions.put("x_idempotency_key", "null");
            return;
        }
        if (!(extension instanceof Map<?, ?> values) || !(values.get("header") instanceof String headerName) || headerName.isBlank()) {
            throw new IllegalArgumentException("%s must contain a nonblank string header".formatted(idempotencyExtension));
        }

        CodegenParameter header = operation.headerParams.stream()
                .filter(parameter -> headerName.equalsIgnoreCase(parameter.baseName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "%s header '%s' is not an operation header parameter".formatted(idempotencyExtension, headerName)));
        if (!header.isString) {
            throw new IllegalArgumentException(
                    "%s header '%s' must be a string operation header parameter".formatted(idempotencyExtension, headerName));
        }
        operation.vendorExtensions.put("x_idempotency_key", header.paramName);
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

    private static void validateParameterSerialization(CodegenOperation operation)
    {
        checkArgument(operation.cookieParams.isEmpty(), "Operation '%s' uses unsupported cookie parameters", operation.operationId);
        operation.pathParams.stream()
                .filter(parameter -> parameter.isArray)
                .forEach(parameter -> {
                    throw new IllegalArgumentException("Path parameter '%s' in operation '%s' must not be an array".formatted(parameter.baseName, operation.operationId));
                });
        // arrays are sent one value per query parameter (form style, exploded) or comma-joined
        // (form style, not exploded); other styles have no generated form
        operation.queryParams.stream()
                .filter(parameter -> parameter.isArray)
                .forEach(parameter -> checkArgument(
                        parameter.style == null || "form".equals(parameter.style),
                        "Query parameter '%s' in operation '%s' uses unsupported array style '%s'",
                        parameter.baseName,
                        operation.operationId,
                        parameter.style));
    }
}
