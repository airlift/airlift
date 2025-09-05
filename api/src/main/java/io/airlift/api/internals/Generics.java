package io.airlift.api.internals;

import com.google.common.reflect.TypeResolver;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiStringId;
import io.airlift.api.validation.ValidatorException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface Generics
{
    TypeResolver typeResolver = new TypeResolver();

    static Type extractGenericParameter(Type type, int index)
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

    static void validateMap(Type type)
    {
        TypeToken<?> typeToken = TypeToken.of(typeResolver.resolveType(type));

        Type keyType = extractGenericParameter(typeToken.getType(), 0);
        Type valueType = extractGenericParameter(typeToken.getType(), 1);
        if (!(keyType.equals(String.class)) || !(valueType.equals(String.class))) {
            throw new ValidatorException("Maps in resources must be Map<String, String>. %s is not".formatted(typeToken.getType()));
        }
    }
}
