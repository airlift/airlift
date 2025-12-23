package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiId;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.model.ModelPolyResource;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResourceModifier;
import io.airlift.api.model.ModelResourceType;
import io.airlift.api.openapi.models.ArraySchema;
import io.airlift.api.openapi.models.BooleanSchema;
import io.airlift.api.openapi.models.DateSchema;
import io.airlift.api.openapi.models.DateTimeSchema;
import io.airlift.api.openapi.models.Discriminator;
import io.airlift.api.openapi.models.IntegerSchema;
import io.airlift.api.openapi.models.MapSchema;
import io.airlift.api.openapi.models.NumberSchema;
import io.airlift.api.openapi.models.Schema;
import io.airlift.api.openapi.models.StringSchema;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.api.internals.Strings.capitalize;
import static io.airlift.api.model.ModelResourceModifier.IS_UNWRAPPED;
import static io.airlift.api.model.ModelResourceModifier.MULTIPART_RESOURCE_IS_FIRST_ITEM;
import static io.airlift.api.model.ModelResourceModifier.RECURSIVE_REFERENCE;
import static io.airlift.api.openapi.OpenApiMetadata.TAG_MODEL_DEFINITIONS;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

class SchemaBuilder
{
    private final Map<SchemaKey, Schema<?>> schemas;
    private final boolean enumsAsStrings;
    private final String schemaTag;
    private final Map<Type, ModelResource> typeToResourceCache;
    private final Map<String, Schema<?>> allOfSchemas;

    SchemaBuilder(boolean enumsAsStrings)
    {
        this(TAG_MODEL_DEFINITIONS, new HashMap<>(), enumsAsStrings, new HashMap<>(), new HashMap<>());
    }

    private SchemaBuilder(String schemaTag, Map<SchemaKey, Schema<?>> schemas, boolean enumsAsStrings, Map<Type, ModelResource> typeToResourceCache, Map<String, Schema<?>> allOfSchemas)
    {
        this.schemaTag = requireNonNull(schemaTag, "schemaTag is null");
        // don't copy
        this.schemas = requireNonNull(schemas, "schemas is null");
        this.enumsAsStrings = enumsAsStrings;
        this.typeToResourceCache = requireNonNull(typeToResourceCache, "typeToResourceCache is null");  // don't copy
        this.allOfSchemas = requireNonNull(allOfSchemas, "allOfSchemas is null");  // don't copy
    }

    @SuppressWarnings("SameParameterValue")
    SchemaBuilder withSchemaTag(String schemaTag)
    {
        return new SchemaBuilder(schemaTag, schemas, enumsAsStrings, typeToResourceCache, allOfSchemas);
    }

    private record SchemaKey(Optional<String> parent, Type containerType, ModelResourceType resourceType, BuildSchemaMode mode)
    {
        private SchemaKey
        {
            requireNonNull(parent, "parent is null");
            requireNonNull(containerType, "containerType is null");
            requireNonNull(resourceType, "resourceType is null");
            requireNonNull(mode, "mode is null");
        }
    }

    enum BuildSchemaMode
    {
        STANDARD(""),
        PATCH("Patch"),
        ALL_OF(""),
        ALL_OF_PATCH("");

        private final String suffix;

        boolean isStandard()
        {
            return (this == STANDARD) || (this == ALL_OF);
        }

        boolean isPartialPatch()
        {
            return (this == PATCH) || (this == ALL_OF_PATCH);
        }

        boolean isAllOf()
        {
            return (this == ALL_OF) || (this == ALL_OF_PATCH);
        }

        BuildSchemaMode(String suffix)
        {
            this.suffix = suffix;
        }
    }

    Collection<Schema<?>> build()
    {
        return withMissingRefs();
    }

    Schema<?> buildMultiPartForm(ModelResource modelResource, BuildSchemaMode mode)
    {
        Schema<?> schema = new Schema<>().type("object");

        if (modelResource.modifiers().contains(MULTIPART_RESOURCE_IS_FIRST_ITEM)) {
            schema.addProperty(modelResource.name(), buildSchema(modelResource, mode));
        }

        schema.addProperty("files", new ArraySchema().items(new Schema<>().type("string").format("binary")).description("The files to upload"));

        return schema;
    }

    Schema<?> buildSchema(ModelResource modelResource, BuildSchemaMode mode)
    {
        if (modelResource.modifiers().contains(RECURSIVE_REFERENCE)) {
            ModelResource referencedModelResource = typeToResourceCache.get(modelResource.type());
            requireNonNull(referencedModelResource, "referencedModelResource could not be found for: " + modelResource.type());

            // recursive references are always lists
            return asList(modelResource, asRef(new Schema<>().name(schemaName(referencedModelResource, mode, Optional.empty()))));
        }

        typeToResourceCache.putIfAbsent(modelResource.type(), modelResource);

        Schema<?> schema = schemas.get(new SchemaKey(Optional.empty(), modelResource.containerType(), modelResource.resourceType(), mode));
        if (schema == null) {
            schema = switch (modelResource.resourceType()) {
                case LIST -> asList(modelResource, buildSchema(asResource(removeContainer(modelResource)), mode));
                case MAP -> new MapSchema().description(adjustedDescription(modelResource)).additionalProperties(buildSchema(asResource(removeContainer(modelResource)), mode));
                case PAGINATED_RESULT -> buildPaginatedSchema(modelResource);
                default -> buildBasicOrResourceSchema(modelResource, mode);
            };
        }
        else {
            schema = asRef(schema);
        }
        return schema;
    }

    @SuppressWarnings("unchecked")
    private Schema<?> asList(ModelResource modelResource, Schema<?> schema)
    {
        return new ArraySchema().description(adjustedDescription(modelResource)).items(schema);
    }

    Optional<Schema<?>> buildBasicSchema(Class<?> clazz)
    {
        Schema<?> schema = null;
        if (boolean.class.isAssignableFrom(clazz)) {
            schema = new BooleanSchema().nullable(false);
        }
        else if (int.class.isAssignableFrom(clazz)) {
            schema = new IntegerSchema().nullable(false);
        }
        else if (long.class.isAssignableFrom(clazz)) {
            schema = new IntegerSchema().format("int64").nullable(false);
        }
        else if (double.class.isAssignableFrom(clazz) || BigDecimal.class.isAssignableFrom(clazz)) {
            schema = new NumberSchema().format("double").nullable(false);
        }
        else if (ApiResourceVersion.class.isAssignableFrom(clazz)) {
            schema = new StringSchema();
        }
        else if (String.class.isAssignableFrom(clazz) || ApiId.class.isAssignableFrom(clazz) || UUID.class.isAssignableFrom(clazz)) {
            schema = new StringSchema();
        }
        else if (Instant.class.isAssignableFrom(clazz)) {
            schema = new DateTimeSchema();
        }
        else if (LocalDate.class.isAssignableFrom(clazz)) {
            schema = new DateSchema();
        }
        else if (Enum.class.isAssignableFrom(clazz)) {
            schema = buildEnum(clazz);
        }
        return Optional.ofNullable(schema);
    }

    private List<Schema<?>> withMissingRefs()
    {
        List<Schema<?>> localSchemas = new ArrayList<>(schemas.values());

        // allOf schemas do not add the sub-schema to the main schema list because they get merged with the
        // parent. See the buildResourceSchema() method and the line "if (mode.isAllOf())".
        // However, recursive references may exist to these sub-schemas and the schemas may not
        // have been added anywhere else. So, add in any missing referenced allOf schemas here.
        HashMap<String, Schema<?>> localAllOfSchemas = new HashMap<>(allOfSchemas);
        // remove any allOf schemas that are already included
        schemas.values().stream().map(Schema::getName).forEach(localAllOfSchemas::remove);
        // add the missing schemas
        localSchemas.addAll(localAllOfSchemas.values());

        return localSchemas;
    }

    private Schema<?> buildEnum(Class<?> clazz)
    {
        StringSchema stringSchema = new StringSchema();
        if (!enumsAsStrings) {
            Stream.of(clazz.getEnumConstants()).forEach(o -> stringSchema.addEnumItem(o.toString()));
        }
        return stringSchema;
    }

    private static String resourceName(ModelResource modelResource)
    {
        return modelResource.openApiName().orElseGet(modelResource::name);
    }

    private ModelResource removeContainer(ModelResource modelResource)
    {
        return modelResource.withContainerType(modelResource.type());
    }

    private ModelResource asList(ModelResource modelResource)
    {
        return modelResource.asResourceType(ModelResourceType.LIST);
    }

    private ModelResource asResource(ModelResource modelResource)
    {
        // for these purposes resource and basic are interchangeable
        return modelResource.asResourceType(ModelResourceType.RESOURCE);
    }

    private Schema<?> buildPaginatedSchema(ModelResource modelResource)
    {
        Schema<?> modelSchema = buildSchema(asList(removeContainer(modelResource)), BuildSchemaMode.STANDARD);
        Schema<?> schema = newNamedSchema(schemaName(removeContainer(modelResource), BuildSchemaMode.STANDARD, Optional.of("Paginated")));
        schema.addProperty("nextPageToken", new StringSchema().description("The next page token to use or \"\" if there are no more pages."));
        schema.addProperty("result", modelSchema.description("A page of results."));
        schemas.put(new SchemaKey(Optional.empty(), modelResource.containerType(), modelResource.resourceType(), BuildSchemaMode.STANDARD), schema);
        return asRef(schema);
    }

    private String schemaName(ModelResource modelResource, BuildSchemaMode mode, Optional<String> prefix)
    {
        Set<String> usedNames = schemas.values().stream()
                .map(Schema::getName)
                .collect(toImmutableSet());

        int index = 0;
        String name;
        do {
            StringBuilder builder = new StringBuilder();

            prefix.ifPresent(builder::append);
            builder.append(capitalize(resourceName(modelResource)));
            builder.append(mode.suffix);

            ++index;
            if (index > 1) {
                builder.append(index);
            }

            name = builder.toString();
        } while (usedNames.contains(name));

        return name;
    }

    private Schema<?> asRef(Schema<?> schema)
    {
        return new Schema<>().$ref(schema.getName());
    }

    private Schema<?> buildBasicOrResourceSchema(ModelResource modelResource, BuildSchemaMode mode)
    {
        return buildBasicSchema(modelResource).orElseGet(() -> buildResourceSchema(modelResource, mode));
    }

    private Schema<?> buildResourceSchema(ModelResource modelResource, BuildSchemaMode mode)
    {
        // prime the cache early to handle recursive references to themselves - Issue 1689
        typeToResourceCache.putIfAbsent(modelResource.type(), modelResource);

        Schema<?> schema = newNamedSchema(schemaName(modelResource, mode, Optional.empty())).description(adjustedDescription(modelResource));
        return buildResourceSchema(schema, modelResource, mode);
    }

    private Schema<?> buildResourceSchema(Schema<?> schema, ModelResource modelResource, BuildSchemaMode mode)
    {
        modelResource.polyResource().ifPresentOrElse(polyResource -> buildPolySchema(schema, polyResource, mode), () -> modelResource.components()
                .stream()
                .filter(component -> mode.isStandard() || !ModelResourceModifier.hasReadOnly(component.modifiers()))
                .forEach(component -> buildComponentSchema(mode, schema, component)));

        if (mode.isAllOf()) {
            // we don't save allOf schemas. They get merged with the parent.
            // However, keep track of them in case there are recursive references to them
            // that need to be added prior to building.
            allOfSchemas.put(schema.getName(), schema);
            return schema;
        }

        schemas.put(new SchemaKey(Optional.empty(), modelResource.containerType(), modelResource.resourceType(), mode), schema);
        return asRef(schema);
    }

    private void buildComponentSchema(BuildSchemaMode mode, Schema<?> schema, ModelResource component)
    {
        if (component.modifiers().contains(IS_UNWRAPPED)) {
            buildResourceSchema(schema, component, allOfMode(mode));
        }
        else {
            Schema<?> componentSchema = buildSchema(component, mode);
            schema.addProperty(component.name(), componentSchema);
            if (!mode.isPartialPatch() && !component.modifiers().contains(ModelResourceModifier.OPTIONAL)) {
                schema.addRequiredItem(component.name());
            }
        }
    }

    private Optional<Schema<?>> buildBasicSchema(ModelResource modelResource)
    {
        Optional<Schema<?>> schema = buildBasicSchema(TypeToken.of(modelResource.type()).getRawType());
        schema.ifPresent(s -> s.description(adjustedDescription(modelResource)));
        return schema;
    }

    private void buildPolySchema(Schema<?> schema, ModelPolyResource polyResource, BuildSchemaMode mode)
    {
        // add the discriminator key property to the main schema
        schema.addProperty(polyResource.key(), new StringSchema());
        schema.addRequiredItem(polyResource.key());

        // add the discriminator itself to the main schema
        Discriminator discriminator = new Discriminator().propertyName(polyResource.key());
        schema.discriminator(discriminator);

        // need to use asRef(schema) because it hasn't been added yet
        Schema<?> mainSchemaAsRef = asRef(schema);

        polyResource.subResources().forEach(subResource -> {
            // build the sub-resource but don't save it
            Schema<?> allOfSchema = buildBasicOrResourceSchema(subResource, allOfMode(mode));

            // create the sub-schema which will have allOf refs to the main schema and the contents of the allOf schema
            Schema<?> subSchema = newNamedSchema(schemaName(subResource, mode, Optional.of(schema.getName())));
            subSchema.addAllOfItem(mainSchemaAsRef);
            subSchema.addAllOfItem(allOfSchema);

            // add a mapping in the discriminator - always use the subresource name and not the OpenAPI override
            discriminator.mapping(subResource.name(), asRef(subSchema).get$ref());

            // save the sub-schema
            schemas.put(new SchemaKey(Optional.of(schema.getName()), subResource.containerType(), subResource.resourceType(), mode), subSchema);
        });
    }

    private Schema<?> newNamedSchema(String name)
    {
        return new Schema<>().name(name).tags(ImmutableList.of(schemaTag));
    }

    private BuildSchemaMode allOfMode(BuildSchemaMode currentMode)
    {
        return switch (currentMode) {
            case STANDARD -> BuildSchemaMode.ALL_OF;
            case PATCH -> BuildSchemaMode.ALL_OF_PATCH;
            case ALL_OF, ALL_OF_PATCH -> currentMode;
        };
    }

    private String adjustedDescription(ModelResource modelResource)
    {
        String description = modelResource.description();
        if (ModelResourceModifier.hasReadOnly(modelResource.modifiers())) {
            description += " (read only)";    // Swagger UI removes read only fields which is not what we want
        }
        return description + modelResource.enumDescriptions().map(enumDescriptions ->
                        enumDescriptions.entrySet().stream()
                                .map(entry -> "<li><code>\"%s\"</code>: %s</li>".formatted(entry.getKey(), entry.getValue()))
                                .collect(joining("\n", "\n<ul>", "</ul>\n"))).orElse("");
    }
}
