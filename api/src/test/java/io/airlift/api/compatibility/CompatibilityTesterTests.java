package io.airlift.api.compatibility;

import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.compatability.ApiCompatibilityTester;
import io.airlift.api.compatibility.unwrapped.UnwrappedService;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResponse;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServices;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static io.airlift.api.model.ModelResourceModifier.IS_UNWRAPPED;
import static org.assertj.core.api.Assertions.assertThat;

public class CompatibilityTesterTests
{
    @Test
    public void testUnwrappedChanged()
            throws IOException
    {
        Path tempDir = Files.createTempDirectory("api");
        try {
            List<String> foundErrors = new ArrayList<>();

            ModelApi unwrappedApi = ApiBuilder.apiBuilder().add(UnwrappedService.class).build();

            ApiCompatibilityTester.newDefaultInstance(tempDir.toString()).test(unwrappedApi.modelServices(), true, foundErrors::addAll);
            // first pass creates the file
            assertThat(foundErrors).hasSize(1).matches(l -> l.getFirst().contains("Created new compatibility file"));
            foundErrors.clear();
            ApiCompatibilityTester.newDefaultInstance(tempDir.toString()).test(unwrappedApi.modelServices(), true, foundErrors::addAll);
            // second pass should be no errors
            assertThat(foundErrors).isEmpty();

            ApiCompatibilityTester.newDefaultInstance(tempDir.toString()).test(removeUnwrapped(unwrappedApi.modelServices()), true, foundErrors::addAll);
            assertThat(foundErrors).isNotEmpty();
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    // remove the unwrapped modifier from all resources in the service
    private ModelServices removeUnwrapped(ModelServices modelServices)
    {
        Set<ModelService> modifiedServices = modelServices.services().stream()
                .map(this::removeUnwrapped)
                .collect(toImmutableSet());
        Set<ModelResponse> modifiedResponses = modelServices.modelResponses().stream().map(this::removeUnwrapped).collect(toImmutableSet());
        return new ModelServices(modifiedServices, modifiedResponses, modelServices.deprecations(), modelServices.errors());
    }

    private ModelService removeUnwrapped(ModelService modelService)
    {
        List<ModelMethod> methods = modelService.methods().stream()
                .map(method -> {
                    Set<ModelResponse> responses = method.responses().stream().map(this::removeUnwrapped).collect(toImmutableSet());
                    ModelResource returnType = removeUnwrapped(method.returnType());
                    Optional<ModelResource> requestBody = method.requestBody().map(this::removeUnwrapped);
                    List<ModelResource> parameters = method.parameters().stream().map(this::removeUnwrapped).collect(toImmutableList());
                    return new ModelMethod(method.methodType(), method.method(), method.customVerb(), requestBody, returnType, parameters, method.optionalParameters(), method.description(), responses, method.traits(), method.quotas(), method.openApiName());
                }).collect(toImmutableList());
        return new ModelService(modelService.service(), modelService.serviceClass(), methods);
    }

    private ModelResponse removeUnwrapped(ModelResponse modelResponse)
    {
        ModelResource resoure = removeUnwrapped(modelResponse.resource());
        return new ModelResponse(modelResponse.status(), modelResponse.responseClass(), resoure);
    }

    private ModelResource removeUnwrapped(ModelResource modelResource)
    {
        List<ModelResource> components = modelResource.components().stream().map(resource -> resource.withModifierRemoved(IS_UNWRAPPED)).collect(toImmutableList());
        return modelResource.withComponents(components);
    }
}
