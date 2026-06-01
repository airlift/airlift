package io.airlift.api;

import java.util.List;

public interface ApiEnumValueResolver
{
    List<String> values(Class<?> enumClass);

    String value(Enum<?> value);
}
