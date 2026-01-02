package io.airlift.api.compatability;

import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelOptionalParameter;
import io.airlift.api.model.ModelOptionalParameter.ExternalParameter;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResourceModifier;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.model.ModelServiceType;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.api.ApiServiceTrait.ENUMS_AS_STRINGS;
import static io.airlift.api.internals.Mappers.MethodPathMode.FOR_DISPLAY;
import static io.airlift.api.internals.Mappers.buildFullPath;
import static io.airlift.api.internals.Mappers.buildMethodPath;
import static io.airlift.api.internals.Mappers.buildResourceId;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

public class ApiCompatibilityUtil
{
    private ApiCompatibilityUtil() {}

    private static final String DELIMITER = "|";

    public static String methodSpec(ModelServiceMetadata service, ModelMethod modelMethod)
    {
        Set<String> alreadyVisited = new HashSet<>();
        return buildMethodPath(modelMethod, FOR_DISPLAY) + DELIMITER +
                service.type().id() + DELIMITER +
                service.type().version() + DELIMITER +
                modelMethod.httpMethod() + DELIMITER +
                resourceData(service.type(), modelMethod.returnType(), alreadyVisited, Mode.OUTPUT) + DELIMITER +
                modelMethod.requestBody().map(requestBody -> resourceData(service.type(), requestBody, alreadyVisited, Mode.INPUT)).orElse("") + DELIMITER +
                modelMethod.parameters().stream().map(ApiCompatibilityUtil::resourceMetadata).collect(joining(DELIMITER, "[", "]")) + DELIMITER +
                modelMethod.optionalParameters().stream().map(ApiCompatibilityUtil::optionalParameter).sorted().collect(joining(DELIMITER, "[", "]")) + DELIMITER +
                modelMethod.responses().stream().mapToInt(modelResponse -> modelResponse.status().code()).distinct().sorted().mapToObj(Integer::toString).collect(joining(DELIMITER, "[", "]"));
    }

    public static void validateMethodSpec(File directory, ModelServiceMetadata service, ModelMethod modelMethod)
    {
        checkArgument(directory.isDirectory(), "%s is not a directory".formatted(directory));
        File file = directory.toPath().resolve(specFileName(service, modelMethod)).toFile();
        checkArgument(file.exists(), "Method compatibility file %s not found".formatted(file));
        try {
            String specFromFile;
            try (Stream<String> lines = Files.lines(file.toPath(), UTF_8)) {
                specFromFile = lines.findFirst().orElseThrow(() -> new IllegalStateException("Could not read spec from first line of: " + file));
            }
            String currentSpec = methodSpec(service, modelMethod);
            if (!currentSpec.equals(specFromFile)) {
                throw new IllegalStateException("""
                        API change detected.

                        The spec has changed from it's previous specification. Published APIs should not change. Instead consider
                        a new version or an alternate method that is backward compatible with the older method.

                        Source: %s
                        Compatibility File: %s
                        File spec: %s
                        New spec: %s
                        """.formatted(modelMethod.method().getDeclaringClass().getName() + "#" + modelMethod.method().getName(), file, specFromFile, currentSpec));
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not read method compatibility file %s".formatted(file), e);
        }
    }

    public static boolean specFileExists(File directory, ModelServiceMetadata service, ModelMethod modelMethod)
    {
        File file = directory.toPath().resolve(specFileName(service, modelMethod)).toFile();
        return file.isFile();
    }

    public static void writeMethodSpecFile(File directory, ModelServiceMetadata service, ModelMethod modelMethod)
    {
        checkArgument(directory.isDirectory(), "%s is not a directory".formatted(directory));
        File file = directory.toPath().resolve(specFileName(service, modelMethod)).toFile();
        try {
            String contents = methodSpec(service, modelMethod) + "\n\n" + methodMetadata(service, modelMethod) + "\n";
            Files.writeString(file.toPath(), contents, StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Method compatibility file already exists: %s".formatted(file), e);
        }
    }

    public static String specFileName(ModelServiceMetadata service, ModelMethod modelMethod)
    {
        String fileName = buildFullPath(service, modelMethod, FOR_DISPLAY) + "-" + modelMethod.httpMethod();
        return fileName.replace("/", "-").replace(":", ";");
    }

    private static String methodMetadata(ModelServiceMetadata service, ModelMethod modelMethod)
    {
        return "Metadata:\n" +
                modelMethod.httpMethod() + '\n' +
                modelMethod.method().getDeclaringClass().getName() + '\n' +
                modelMethod.method().getName() + '\n' +
                buildFullPath(service, modelMethod, FOR_DISPLAY) + '\n' +
                "\n\nReturn:\n" +
                modelMethod.returnType() +
                "\n\nParameter(s):\n" +
                (modelMethod.parameters().isEmpty() ? "none" : modelMethod.parameters().stream().map(ApiCompatibilityUtil::resourceMetadata).collect(joining("\n"))) +
                "\n\nOptional Parameter(s):\n" +
                (modelMethod.optionalParameters().isEmpty() ? "none" : modelMethod.optionalParameters().stream().sorted().map(optionalParameter -> optionalParameter.type().getTypeName()).collect(joining("\n"))) +
                "\n\nBody:\n" +
                modelMethod.requestBody().map(ModelResource::toString).orElse("none") +
                "\n\nResponse(s):\n" +
                (modelMethod.responses().isEmpty() ? "none" : modelMethod.responses().stream().mapToInt(modelResponse -> modelResponse.status().code()).sorted().mapToObj(Integer::toString).collect(joining("\n")));
    }

    private static String resourceMetadata(ModelResource modelResource)
    {
        if (modelResource.limitedValues().isEmpty()) {
            return buildResourceId(modelResource.type());
        }
        return buildResourceId(modelResource.type()) + ":" + String.join(DELIMITER, modelResource.limitedValues());
    }

    private enum Mode
    {
        INPUT,
        OUTPUT
    }

    private static String resourceData(ModelServiceType modelServiceType, ModelResource modelResource, Set<String> alreadyVisited, Mode mode)
    {
        List<String> parts = new ArrayList<>();
        appendResource(modelServiceType, parts, modelResource, alreadyVisited, mode, true);
        return "resource(" + String.join(DELIMITER, parts) + ")";
    }

    private static void appendResource(ModelServiceType modelServiceType, List<String> parts, ModelResource modelResource, Set<String> alreadyVisited, Mode mode, boolean isRoot)
    {
        if ((mode == Mode.OUTPUT) && modelResource.modifiers().contains(ModelResourceModifier.OPTIONAL)) {
            return; // optional fields can be ignored for GET/LIST purposes but not for CREATE/UPDATE purposes
        }
        parts.add(modelResource.name());
        appendModifiers(parts, modelResource);

        if (alreadyVisited.add(modelResource.name() + DELIMITER + mode)) {
            parts.add(modelResource.resourceType().name());
            if (!isRoot) {
                if (modelServiceType.serviceTraits().contains(ENUMS_AS_STRINGS) && (modelResource.type() instanceof Class<?> clazz) && clazz.isEnum()) {
                    parts.add(String.class.getName());
                }
                else {
                    parts.add(modelResource.type().getTypeName());
                }
            }
            appendPossibleEnum(modelServiceType, parts, modelResource.type());
            modelResource.components().forEach(component -> appendResource(modelServiceType, parts, component, alreadyVisited, mode, false));
        }
    }

    private static void appendModifiers(List<String> parts, ModelResource modelResource)
    {
        if (modelResource.modifiers().contains(ModelResourceModifier.IS_UNWRAPPED)) {
            parts.add("unwrapped");
        }
    }

    private static void appendPossibleEnum(ModelServiceType modelServiceType, List<String> parts, Type type)
    {
        if (modelServiceType.serviceTraits().contains(ENUMS_AS_STRINGS)) {
            return;
        }

        if ((type instanceof Class<?> clazz) && clazz.isEnum()) {
            Stream.of(clazz.getEnumConstants()).forEach(e -> parts.add(e.toString().toUpperCase(Locale.ROOT)));
        }
    }

    private static String optionalParameter(ModelOptionalParameter optionalParameter)
    {
        return optionalParameter.location().name() + DELIMITER +
                optionalParameter.metadata().contains(ModelOptionalParameter.Metadata.MULTIPLE_ALLOWED) + DELIMITER +
                limitedValues(optionalParameter) +
                optionalParameter.externalParameters().stream().map(ApiCompatibilityUtil::externalParameter).collect(joining(DELIMITER, "[", "]"));
    }

    private static String externalParameter(ExternalParameter externalParameter)
    {
        return externalParameter.name() + DELIMITER + externalParameter.externalType().getSimpleName();
    }

    private static String limitedValues(ModelOptionalParameter optionalParameter)
    {
        if (optionalParameter.limitedValues().isEmpty()) {
            return "";
        }
        return optionalParameter.limitedValues().stream().collect(joining(DELIMITER, "[", "]")) + DELIMITER;
    }
}
