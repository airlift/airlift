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

import com.google.inject.TypeLiteral;
import com.proofpoint.json.ObjectMapperProvider;
import org.codehaus.jackson.map.ObjectMapper;

import static org.codehaus.jackson.map.SerializationConfig.Feature.INDENT_OUTPUT;

// This will be removed soon
@Deprecated
public class JsonCodecBuilder
{
    private final boolean prettyPrint;

    private JsonCodecBuilder()
    {
        prettyPrint = false;
    }

    private JsonCodecBuilder(boolean prettyPrint)
    {
        this.prettyPrint = prettyPrint;
    }

    public JsonCodecBuilder prettyPrint()
    {
        return new JsonCodecBuilder(true);
    }

    public <T> JsonCodec<T> build(Class<T> type)
    {
        return new JsonCodec<T>(createObjectMapper(), type);
    }

    public <T> JsonCodec<T> build(TypeLiteral<T> type)
    {
        return new JsonCodec<T>(createObjectMapper(), type.getType());
    }

    private ObjectMapper createObjectMapper()
    {
        ObjectMapper objectMapper = new ObjectMapperProvider().get();
        if (prettyPrint) {
            objectMapper.getSerializationConfig().enable(INDENT_OUTPUT);
        } else {
            objectMapper.getSerializationConfig().disable(INDENT_OUTPUT);
        }
        return objectMapper;
    }

}
