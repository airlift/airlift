package io.airlift.api.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiTrait;
import io.airlift.api.ApiType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public record ModelMethod(
        ApiType methodType,
        Method method,
        Optional<String> customVerb,
        Optional<ModelResource> requestBody,
        ModelResource returnType,
        List<ModelResource> parameters,
        List<ModelOptionalParameter> optionalParameters,
        String description,
        Set<ModelResponse> responses,
        Set<ApiTrait> traits,
        Set<String> quotas,
        Optional<String> openApiName)
{
    public ModelMethod
    {
        requireNonNull(methodType, "methodType is null");
        requireNonNull(returnType, "returnType is null");
        requireNonNull(method, "method is null");
        requireNonNull(customVerb, "customVerb is null");
        requireNonNull(requestBody, "requestBody is null");
        requireNonNull(description, "description is null");
        requireNonNull(openApiName, "openApiName is null");
        parameters = ImmutableList.copyOf(parameters);
        optionalParameters = ImmutableList.copyOf(optionalParameters);
        responses = ImmutableSet.copyOf(responses);
        traits = ImmutableSet.copyOf(traits);
        quotas = ImmutableSet.copyOf(quotas);
    }

    public ModelMethod withRequestBody(ModelResource requestBody)
    {
        return new ModelMethod(methodType, method, customVerb, Optional.of(requestBody), returnType, parameters, optionalParameters, description, responses, traits, quotas, openApiName);
    }

    public ModelMethod withAdditionalOptionalParameter(ModelOptionalParameter optionalParameter)
    {
        List<ModelOptionalParameter> optionalParameters = new ArrayList<>();
        optionalParameters.add(optionalParameter);
        return new ModelMethod(methodType, method, customVerb, requestBody, returnType, parameters, optionalParameters, description, responses, traits, quotas, openApiName);
    }

    public ModelMethod withDescription(String description)
    {
        return new ModelMethod(methodType, method, customVerb, requestBody, returnType, parameters, optionalParameters, description, responses, traits, quotas, openApiName);
    }

    public ModelMethod withQuotas(Set<String> quotas)
    {
        return new ModelMethod(methodType, method, customVerb, requestBody, returnType, parameters, optionalParameters, description, responses, traits, quotas, openApiName);
    }

    public boolean isPatch()
    {
        return requestBody.stream().anyMatch(modelResource -> modelResource.modifiers().contains(ModelResourceModifier.PATCH));
    }

    public String httpMethod()
    {
        return methodType.httpMethod(isPatch());
    }
}
