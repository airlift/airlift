package io.airlift.api.validation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.binding.ApiModule;
import io.airlift.api.binding.PolyResourceModule;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.json.JsonModule;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static io.airlift.api.builders.ResourceBuilder.resourceBuilder;
import static io.airlift.api.internals.Mappers.buildHeaderName;
import static io.airlift.api.model.ModelResourceModifier.RECURSIVE_REFERENCE;
import static io.airlift.api.validation.ResourceValidator.validateResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestConforming
{
    private static final ModelServiceMetadata METADATA = new ModelServiceMetadata("dummy", new ModelServiceType("dummy", 1, "dummy", "dummy", ImmutableSet.copyOf(ApiServiceTrait.values())), "dummy", ImmutableList.of());

    @Test
    public void testDuplicateMethods()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithDuplicateMethods.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("Service with duplicate URI"));
    }

    @Test
    public void testDuplicateSameUriDifferentHttp()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithDuplicateMethodsDifferentHttp.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testBadVersionName()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithBadVersion.class).build().modelServices();
        assertThat(services.errors()).anyMatch(s -> s.contains("ApiResourceVersion fields must be named"));
    }

    @Test
    public void testNoVersion()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithNoVersion.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("missing an ApiResourceVersion"));
    }

    @Test
    public void testReadOnlyResource()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithReadOnlyResources.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testDisallowedBothFieldMaskAndPatch()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServicePatchWithPatchAndPatch.class).build().modelServices();
        assertThat(services.errors()).withFailMessage(() -> services.errors().toString()).hasSize(1).first().matches(s -> s.contains("has more than one request body parameter"));
    }

    @Test
    public void testBadPatch()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithBadPatch.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("not a valid resource type"));
    }

    @Test
    public void testValidCreate()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(FoodServiceValid.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testInvalidCreate()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(FoodServiceInvalid.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("resource parameter has an ApiResourceVersion"));
    }

    @Test
    public void testValidDocumentationLinks()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithValidLinks.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testDocumentationLinksInvalid()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithNoUriLinks.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("Illegal character") && s.contains("starburst-galaxy-link1-invalid-[]"));
    }

    @Test
    public void testHeaderNames()
    {
        assertThat(buildHeaderName("foo")).isEqualTo("X-FOO");
        assertThat(buildHeaderName("fooBar")).isEqualTo("X-FOO-BAR");
        assertThat(buildHeaderName("Bar")).isEqualTo("X-BAR");
        assertThat(buildHeaderName("Bar")).isEqualTo("X-BAR");
        assertThat(buildHeaderName("Bim_Bam_Boom")).isEqualTo("X-BIM-BAM-BOOM");
    }

    @Test
    public void testBoxedOptionals()
    {
        ValidationContext validationContext = new ValidationContext();
        ModelResource modelResource = resourceBuilder(BoxedOptionals.class).build();
        validateResource(validationContext, METADATA, modelResource);

        assertThat(validationContext.errors()).isEmpty();
        assertThat(modelResource.components().get(0).type()).isEqualTo(boolean.class);
        assertThat(modelResource.components().get(1).type()).isEqualTo(int.class);
        assertThat(modelResource.components().get(2).type()).isEqualTo(long.class);
        assertThat(modelResource.components().get(3).type()).isEqualTo(double.class);
    }

    @Test
    public void testAllowAcceptableCollections()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithAcceptableLists.class).build().modelServices();
        assertThat(services.errors()).hasSize(0);
    }

    @Test
    public void testAllTypes()
    {
        // do a thorough test by creating a real ApiModule. This will do full validation including serialization validation
        ModelApi modelApi = ApiBuilder.apiBuilder().add(ServiceWithAllTypes.class).build();
        Module module = ApiModule.builder().addApi(modelApi).build();
        Guice.createInjector(module, new JsonModule());
    }

    @Test
    public void testBadLookup()
    {
        ModelApi modelApi = ApiBuilder.apiBuilder().add(ServiceWithBadLookup.class).build();
        Module module = ApiModule.builder().addApi(modelApi).build();
        assertThatThrownBy(() -> Guice.createInjector(module)).hasMessageContaining("There are Ids used in service methods that are annotated with ApiIdSupportsLookup but missing a binding of ApiIdLookup");
    }

    @Test
    public void testPolyResourceWithReusedKey()
    {
        ValidationContext validationContext = new ValidationContext();

        ModelResource modelResource = resourceBuilder(PolyResourceWithReusedKey.class).build();
        validateResource(validationContext, METADATA, modelResource);

        assertThat(validationContext.errors())
                .anyMatch(s -> s.contains("ItsNotOk is a sub-resource of ApiPolyResource and has a component that is the same as the ApiPolyResource key: dontReuse"));
    }

    @Test
    public void testStreamResponse()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithStreamResponse.class).build().modelServices();
        assertThat(services.errors()).hasSize(0);

        services = ApiBuilder.apiBuilder().add(BadServiceWithStreamResponse1.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first()
                .matches(s -> s.startsWith("ApiStreamResponse is only allowed for @ApiGet"));

        services = ApiBuilder.apiBuilder().add(BadServiceWithStreamResponse2.class).build().modelServices();
        assertThat(services.errors()).anyMatch(s -> s.startsWith("ApiStreamResponse cannot be a parameter"));
    }

    @Test
    public void testMultiPartForm()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithMultiPartForm.class).build().modelServices();
        assertThat(services.errors()).hasSize(0);

        services = ApiBuilder.apiBuilder().add(BadServiceWithMultiPartForm.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first()
                .matches(s -> s.startsWith("ApiMultiPartForm is not allowed for method returns"));
    }

    @Test
    public void testRecursiveResource()
    {
        ValidationContext validationContext = new ValidationContext();
        ModelResource modelResource = resourceBuilder(RecursiveModel.class).build();

        validateResource(validationContext, METADATA, modelResource);

        modelResource.components().forEach(component -> {
            if (component.type().equals(RecursiveModel.class)) {
                assertThat(component.modifiers()).contains(RECURSIVE_REFERENCE);
            }
            else {
                List<ModelResource> subTypeComponents = component.components();
                assertThat(subTypeComponents).hasSize(1);
                assertThat(subTypeComponents.getFirst().modifiers()).contains(RECURSIVE_REFERENCE);
            }
        });

        ValidationContext validationContext2 = new ValidationContext();
        validationContext2.inContext("", _ -> validateResource(validationContext2, METADATA, resourceBuilder(BadRecursiveModel1.class).build()));
        assertThat(validationContext2.errors()).isNotEmpty().allMatch(s -> s.startsWith("Recursive resources are only allowed in collections"));

        ValidationContext validationContext3 = new ValidationContext();
        validationContext3.inContext("", _ -> validateResource(validationContext3, METADATA, resourceBuilder(BadRecursiveModel2.class).build()));
        assertThat(validationContext3.errors()).isNotEmpty().allMatch(s -> s.startsWith("Recursive resources are only allowed in collections"));

        ValidationContext validationContext4 = new ValidationContext();
        validationContext4.inContext("", _ -> validateResource(validationContext4, METADATA, resourceBuilder(BadRecursiveModel3.class).build()));
        assertThat(validationContext4.errors()).isNotEmpty().allMatch(s -> s.startsWith("Recursive resources are only allowed in collections"));

        ValidationContext validationContext5 = new ValidationContext();
        validationContext5.inContext("", _ -> validateResource(validationContext5, METADATA, resourceBuilder(BadRecursiveModel4.class).build()));
        assertThat(validationContext5.errors()).isNotEmpty().allMatch(s -> s.startsWith("Maps in resources must be Map<String, String>"));
    }

    @Test
    public void testRecursiveSerialization()
    {
        PolyResourceModule.Builder builder = PolyResourceModule.builder();
        builder.addPolyResource(RecursiveResourceBase.class);
        Module module = builder.build();
        Injector injector = Guice.createInjector(module, new JsonModule());
        ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);

        ResourceSerializationValidator serializationValidator = new ResourceSerializationValidator(ImmutableSet.of(RecursiveResource.class));
        serializationValidator.validateSerialization(objectMapper);
    }
}
