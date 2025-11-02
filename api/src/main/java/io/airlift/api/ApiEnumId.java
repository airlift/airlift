package io.airlift.api;

import com.google.common.collect.ImmutableList;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.stream.Stream;

import static io.airlift.api.internals.Strings.camelCase;
import static io.airlift.api.responses.ApiException.badRequest;

public abstract class ApiEnumId<RESOURCE, INTERNALID extends Enum<INTERNALID>>
        extends ApiId<RESOURCE, INTERNALID>
{
    public ApiEnumId(INTERNALID defaultValue)
    {
        super(camelCase(defaultValue.name()));
    }

    protected ApiEnumId(String id)
    {
        super(id);
    }

    @SuppressWarnings("unchecked")
    @Override
    public INTERNALID toInternal()
    {
        Type enumType = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        Class<?> enumClass = (Class<?>) enumType;

        return (INTERNALID) Stream.of(enumClass.getEnumConstants())
                .filter(value -> {
                    String constant = camelCase(String.valueOf(value));
                    return constant.equals(id);
                })
                .findFirst()
                .orElseThrow(() -> badRequest("Invalid id: " + id, ImmutableList.of("id")));
    }
}
