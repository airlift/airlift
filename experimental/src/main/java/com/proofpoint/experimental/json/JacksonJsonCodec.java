/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.experimental.json;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;

import java.io.IOException;
import java.lang.reflect.Type;

import static org.codehaus.jackson.map.DeserializationConfig.Feature.AUTO_DETECT_SETTERS;
import static org.codehaus.jackson.map.DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.codehaus.jackson.map.SerializationConfig.Feature.AUTO_DETECT_GETTERS;
import static org.codehaus.jackson.map.SerializationConfig.Feature.AUTO_DETECT_IS_GETTERS;
import static org.codehaus.jackson.map.SerializationConfig.Feature.INDENT_OUTPUT;
import static org.codehaus.jackson.map.SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS;
import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

class JacksonJsonCodec<T> implements JsonCodec<T>
{
    private final ObjectMapper mapper;
    private final JavaType javaType;

    JacksonJsonCodec(Type type, boolean prettyPrint)
    {
        javaType = TypeFactory.type(type);

        mapper = new ObjectMapper();

        mapper.getDeserializationConfig().disable(DeserializationConfig.Feature.AUTO_DETECT_FIELDS);
        mapper.getDeserializationConfig().disable(AUTO_DETECT_SETTERS);
        mapper.getDeserializationConfig().disable(FAIL_ON_UNKNOWN_PROPERTIES);

        mapper.getSerializationConfig().disable(SerializationConfig.Feature.AUTO_DETECT_FIELDS);
        mapper.getSerializationConfig().disable(AUTO_DETECT_GETTERS);
        mapper.getSerializationConfig().disable(AUTO_DETECT_IS_GETTERS);
        mapper.getSerializationConfig().disable(WRITE_DATES_AS_TIMESTAMPS);

        mapper.getSerializationConfig().setSerializationInclusion(NON_NULL);

        if (prettyPrint) {
            mapper.getSerializationConfig().enable(INDENT_OUTPUT);
        }
        else {
            mapper.getSerializationConfig().disable(INDENT_OUTPUT);
        }

    }

    @Override
    public T fromJson(String json)
            throws IllegalArgumentException
    {
        try {
            return (T) mapper.readValue(json, javaType);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(String.format("Invalid %s json string", javaType.getRawClass().getSimpleName()), e);
        }
    }

    @Override
    public String toJson(T farmdResponse)
            throws IllegalArgumentException
    {
        try {
            return mapper.writeValueAsString(farmdResponse);
        }
        catch (IOException e) {
            throw new IllegalArgumentException(String.format("%s could not be converted to json", javaType.getRawClass().getSimpleName()), e);
        }
    }
}
