package io.airlift.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import io.airlift.api.validation.ValidationContext;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("unused")
public abstract class ApiId<RESOURCE, INTERNALID>
{
    protected final String id;

    private final Optional<Constructor<?>> internalIdCtor;

    private static final Map<Class<?>, Optional<Constructor<?>>> CONSTRUCTORS = new ConcurrentHashMap<>();
    private static final ValidationContext VALIDATION_UTILS = new ValidationContext("ApiId verification");

    public ApiId()
    {
        throw new UnsupportedOperationException("You must overload the default constructor to provide a default ID value");
    }

    protected ApiId(INTERNALID internalId)
    {
        this(internalId.toString());
    }

    protected ApiId(String id)
    {
        this.id = requireNonNull(id, "id is null");
        checkArgument(!id.isBlank(), "id is blank");

        //noinspection rawtypes
        Class<? extends ApiId> clazz = getClass();
        internalIdCtor = CONSTRUCTORS.computeIfAbsent(clazz, __ -> validateAndGetInternalConstructor(clazz));
    }

    @JsonIgnore
    public INTERNALID toInternal()
            throws RuntimeException
    {
        try {
            //noinspection unchecked
            return (INTERNALID) internalIdCtor.orElseThrow(UnsupportedOperationException::new).newInstance(id);
        }
        catch (Exception e) {
            throw badRequest("Invalid id: " + id);
        }
    }

    @JsonValue
    @Override
    public String toString()
    {
        return id;
    }

    @Override
    public final boolean equals(Object obj)
    {
        return (obj instanceof ApiId<?, ?> abstractId) && id.equals(abstractId.id);
    }

    @Override
    public final int hashCode()
    {
        return Objects.hash(id);
    }

    @SuppressWarnings("rawtypes")
    private Optional<Constructor<?>> validateAndGetInternalConstructor(Class<? extends ApiId> clazz)
    {
        if (ApiEnumId.class.isAssignableFrom(clazz) || ApiStringId.class.isAssignableFrom(clazz)) {
            return Optional.empty();
        }

        VALIDATION_UTILS.validateId(clazz);
        Class<?> internalIdType = VALIDATION_UTILS.extractGenericParameterAsClass(clazz.getGenericSuperclass(), 1);
        try {
            return Optional.of(internalIdType.getConstructor(String.class));
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(e);  // should never get here
        }
    }
}
