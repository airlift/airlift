package io.airlift.api.internals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiJson;
import io.airlift.api.ApiJsonList;
import io.airlift.api.ApiJsonNode;
import io.airlift.api.ApiJsonObject;

import java.lang.reflect.Type;

import static io.airlift.api.internals.Generics.typeResolver;

public final class ApiJsonTypes
{
    private ApiJsonTypes() {}

    public static boolean isApiJsonType(Type type)
    {
        Class<?> clazz = rawClass(type);
        return ApiJson.class.isAssignableFrom(clazz);
    }

    public static String apiJsonResourceName(Type type)
    {
        Class<?> clazz = rawClass(type);
        if (ApiJsonNode.class.isAssignableFrom(clazz)) {
            return "json";
        }
        if (ApiJsonObject.class.isAssignableFrom(clazz)) {
            return "jsonObject";
        }
        if (ApiJsonList.class.isAssignableFrom(clazz)) {
            return "jsonList";
        }
        throw unsupportedType(clazz);
    }

    public static String apiJsonResourceDescription(Type type)
    {
        Class<?> clazz = rawClass(type);
        if (ApiJsonNode.class.isAssignableFrom(clazz)) {
            return "An arbitrary JSON value.";
        }
        if (ApiJsonObject.class.isAssignableFrom(clazz)) {
            return "A JSON object.";
        }
        if (ApiJsonList.class.isAssignableFrom(clazz)) {
            return "A JSON array.";
        }
        throw unsupportedType(clazz);
    }

    public static Class<? extends JsonNode> jacksonJsonType(Type type)
    {
        Class<?> clazz = rawClass(type);
        if (ApiJsonNode.class.isAssignableFrom(clazz)) {
            return JsonNode.class;
        }
        if (ApiJsonObject.class.isAssignableFrom(clazz)) {
            return ObjectNode.class;
        }
        if (ApiJsonList.class.isAssignableFrom(clazz)) {
            return ArrayNode.class;
        }
        throw unsupportedType(clazz);
    }

    private static IllegalArgumentException unsupportedType(Class<?> clazz)
    {
        return new IllegalArgumentException("Unsupported ApiJson type: " + clazz.getName());
    }

    private static Class<?> rawClass(Type type)
    {
        return TypeToken.of(typeResolver.resolveType(type)).getRawType();
    }
}
