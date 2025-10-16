package io.airlift.api.validation;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiEnumId;
import io.airlift.api.ApiId;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiStreamResponse.ApiByteStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiOutputStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.ApiStringId;
import io.airlift.api.model.ModelResource;
import io.airlift.log.Logger;
import jakarta.ws.rs.core.MediaType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Throwables.getRootCause;
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.api.internals.Generics.typeResolver;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static java.util.Objects.requireNonNull;

public class ValidationContext
{
    private static final Logger log = Logger.get(ValidationContext.class);

    private final Optional<String> contextDescription;
    private final Multimap<String, String> mutableErrors;
    private final Set<Class<?>> resourcesWithUnwrappedComponents;
    private final Set<Class<?>> polyResources;
    private final Set<String> usedOpenApiNames;
    private final Set<Type> activeValidatingResources;
    private final Set<Type> validatedResources;
    private final Set<Class<?>> needsSerializationValidation;

    /*
        Thank you chat-gpt for the regex (modified)

        - ^: Asserts the start of the string.
        - [a-zA-Z]: Matches the first character, which must be a letter (lowercase or uppercase).
        - [a-zA-Z0-9]*: Matches zero or more occurrences of letters (lowercase or uppercase) or digits
        - $: Asserts the end of the string.
     */
    private static final Pattern STANDARD_NAMING = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern ENUM_NAMING = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");   // same as STANDARD_NAMING but first letter must be uppercase
    private static final Pattern ID_NAMING = Pattern.compile("^[a-z0-9][a-zA-Z0-9]*$");  // same as STANDARD_NAMING but first letter may be a number

    private static final Set<Type> forcedReadOnly = ImmutableSet.of(ApiResourceVersion.class);

    public ValidationContext()
    {
        this(Optional.empty(), HashMultimap.create(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    public ValidationContext(String initialContext)
    {
        this(Optional.of(initialContext), HashMultimap.create(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    private ValidationContext(
            Optional<String> contextDescription,
            Multimap<String, String> mutableErrors,
            Set<Class<?>> resourcesWithUnwrappedComponents,
            Set<Class<?>> polyResources,
            Set<String> usedOpenApiNames,
            Set<Type> activeValidatingResources,
            Set<Type> validatedResources,
            Set<Class<?>> needsSerializationValidation)
    {
        this.contextDescription = requireNonNull(contextDescription, "context is null");
        this.mutableErrors = requireNonNull(mutableErrors, "mutableErrors is null");

        // don't copy - use passed in values
        this.resourcesWithUnwrappedComponents = requireNonNull(resourcesWithUnwrappedComponents, "resourcesWithUnwrappedComponents is null");
        this.polyResources = requireNonNull(polyResources, "polyResources is null");
        this.usedOpenApiNames = requireNonNull(usedOpenApiNames, "usedOpenApiNames is null");
        this.activeValidatingResources = requireNonNull(activeValidatingResources, "activeValidatingResources is null");
        this.validatedResources = requireNonNull(validatedResources, "validatedResources is null");
        this.needsSerializationValidation = requireNonNull(needsSerializationValidation, "needsSerializationValidation is null");
    }

    public MediaType streamingResponseMediaType(ModelResource resource)
    {
        return streamingResponseMediaType(resource.containerType());
    }

    public MediaType streamingResponseMediaType(Type type)
    {
        TypeToken<?> typeToken = TypeToken.of(typeResolver.resolveType(type));
        if (typeToken.isSubtypeOf(ApiByteStreamResponse.class) || typeToken.isSubtypeOf(ApiOutputStreamResponse.class)) {
            return APPLICATION_OCTET_STREAM_TYPE;
        }
        if (typeToken.isSubtypeOf(ApiTextStreamResponse.class)) {
            return TEXT_PLAIN_TYPE;
        }
        throw new ValidatorException("Unexpected streaming type: %s".formatted(type));
    }

    public Class<?> extractGenericParameterAsClass(Type type, int index)
    {
        Type parameter = extractGenericParameter(type, index);
        if (parameter instanceof Class<?> clazz) {
            return clazz;
        }
        throw new ValidatorException("Expected %s's type argument to be a class".formatted(type));
    }

    public Class<?> genericParameterAsClass(Type parameter)
    {
        if (parameter instanceof Class<?> clazz) {
            return clazz;
        }
        throw new ValidatorException("Expected type %s to be a class".formatted(parameter));
    }

    public Type extractGenericParameter(Type type, int index)
    {
        if (type instanceof ParameterizedType parameterizedType) {
            if (parameterizedType.getRawType().equals(ApiStringId.class) && (index == 1)) {
                return String.class;
            }

            if (parameterizedType.getActualTypeArguments().length <= index) {
                throw new ValidatorException("%s does not have expected (%d) number of parameters".formatted(type, index + 1));
            }
            return typeResolver.resolveType(parameterizedType.getActualTypeArguments()[index]);
        }
        throw new ValidatorException("Expected %s to be parameterized type".formatted(type));
    }

    public static boolean isForcedReadOnly(Type type)
    {
        return forcedReadOnly.contains(type);
    }

    public Set<String> errors()
    {
        return mutableErrors.asMap()
                .entrySet()
                .stream()
                .map(entry -> {
                    String message = entry.getKey();
                    Collection<String> contexts = entry.getValue();
                    return message + " - Contexts: " + contexts.stream().map(context -> "[" + context + "]").collect(Collectors.joining(", "));
                })
                .collect(toImmutableSet());
    }

    public void error(String message, Object... args)
    {
        String formattedMessage = message.formatted(args);
        String context = contextDescription.orElse("n/a");
        mutableErrors.put(formattedMessage, context);
    }

    public enum NameType
    {
        STANDARD(STANDARD_NAMING),
        ENUM(ENUM_NAMING),
        ID(ID_NAMING);

        private final Pattern pattern;

        NameType(Pattern pattern)
        {
            this.pattern = pattern;
        }
    }

    public void validateName(String name, NameType nameType)
    {
        if ((name == null) || !nameType.pattern.matcher(name).matches()) {
            throw new ValidatorException("\"%s\" is not a valid name".formatted(name));
        }
    }

    public void validateDocumentationLinks(List<URI> documentationLinks)
    {
        if (documentationLinks.isEmpty()) {
            log.warn("Documentation links for: [%s] are empty", contextDescription.orElse("n/a"));
        }
    }

    public void validateId(Class<?> idClass)
    {
        if (!ApiId.class.isAssignableFrom(idClass)) {
            throw new ValidatorException("Not an ID: %s".formatted(idClass.getName()));
        }

        Class<?> resourceType = extractGenericParameterAsClass(idClass.getGenericSuperclass(), 0);

        if (resourceType.isInterface() && resourceType.isSealed() && resourceType.isAnnotationPresent(ApiPolyResource.class)) {
            return;
        }

        if (!resourceType.isRecord() || (resourceType.getAnnotation(ApiResource.class) == null)) {
            throw new ValidatorException("ID's resource type parameter is not a valid resource type: %s".formatted(resourceType));
        }

        if (!ApiEnumId.class.isAssignableFrom(idClass)) {
            Class<?> internalIdType = extractGenericParameterAsClass(idClass.getGenericSuperclass(), 1);
            try {
                internalIdType.getConstructor(String.class);
            }
            catch (NoSuchMethodException e) {
                throw new ValidatorException("ID's internal ID type parameter does not have a public constructor with a single String argument: %s".formatted(idClass.getGenericSuperclass()));
            }
        }
    }

    public void validateOpenApiName(ModelResource modelResource)
    {
        String name = modelResource.openApiName()
                .map(openApiName -> {
                    validateName(openApiName, NameType.STANDARD);
                    return openApiName;
                })
                .orElseGet(modelResource::name);
        if (!usedOpenApiNames.add(name)) {
            throw new ValidatorException("Duplicate OpenApi name: \"%s\"".formatted(name));
        }
    }

    public void registerNeedsSerialization(Class<?> clazz)
    {
        needsSerializationValidation.add(clazz);
    }

    public boolean resourceHasBeenValidated(Type type)
    {
        return validatedResources.contains(type);
    }

    public void markResourceAsValidated(Type type)
    {
        validatedResources.add(type);
    }

    public void registerResourcesWithUnwrappedComponents(Class<?> clazz)
    {
        resourcesWithUnwrappedComponents.add(clazz);
    }

    public Set<Class<?>> resourcesWithUnwrappedComponents()
    {
        return ImmutableSet.copyOf(resourcesWithUnwrappedComponents);
    }

    public void registerPolyResource(Class<?> clazz)
    {
        polyResources.add(clazz);
    }

    public Set<Class<?>> polyResources()
    {
        return ImmutableSet.copyOf(polyResources);
    }

    public Set<Class<?>> needsSerializationValidation()
    {
        return needsSerializationValidation;
    }

    public void addValidatingResources(Type resource)
    {
        activeValidatingResources.add(resource);
    }

    public void removeValidatingResources(Type resource)
    {
        activeValidatingResources.remove(resource);
    }

    public boolean isActiveValidatingResource(Type resource)
    {
        return activeValidatingResources.contains(resource);
    }

    public interface ContextRunnable
    {
        void run(ValidationContext subContext)
                throws RuntimeException;
    }

    public interface ContextCallable<T>
    {
        T call(ValidationContext subContext)
                throws RuntimeException;
    }

    public void inContext(String subContext, ContextRunnable runnable)
    {
        withContext(subContext, subValidationContext -> {
            runnable.run(subValidationContext);
            return null;
        });
    }

    @SuppressWarnings("ThrowableNotThrown")
    public <T> Optional<T> withContext(String subContext, ContextCallable<T> callable)
    {
        String adjustedContextDescription = this.contextDescription.map(old -> String.join(", ", old, subContext)).orElse(subContext);
        ValidationContext subValidationContext = new ValidationContext(Optional.of(adjustedContextDescription), mutableErrors, resourcesWithUnwrappedComponents, polyResources, usedOpenApiNames, activeValidatingResources, validatedResources, needsSerializationValidation);
        try {
            return Optional.ofNullable(callable.call(subValidationContext));
        }
        catch (Throwable t) {
            switch (getRootCause(t)) {
                case ValidatorException e -> e.messages().forEach(subValidationContext::error);
                case InterruptedException _ -> {
                    Thread.currentThread().interrupt();
                    subValidationContext.error("Interrupted:");
                }
                default -> subValidationContext.error(t.getMessage() + "\n" + getStackTraceAsString(t));
            }
        }

        return Optional.empty();
    }
}
