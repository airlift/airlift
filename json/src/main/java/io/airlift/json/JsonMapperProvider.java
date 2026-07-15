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
package io.airlift.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.jackson.BaseJacksonProvider;

import static java.util.Objects.requireNonNull;

public class JsonMapperProvider
        extends BaseJacksonProvider<JsonMapper, JsonMapper.Builder, JsonMapperProvider>
{
    public JsonMapperProvider()
    {
        this(new JsonFactoryBuilder());
    }

    public JsonMapperProvider(JsonFactory jsonFactory)
    {
        this(new JsonFactoryBuilder(requireNonNull(jsonFactory, "jsonFactory is null")));
    }

    private JsonMapperProvider(JsonFactoryBuilder jsonFactoryBuilder)
    {
        super(jsonFactoryBuilder, JsonMapper::builder);

        // When serialization fails in the middle, it's better to return a truncated (invalid) JSON
        // than something that could be interpreted as a valid (but incorrect) result.
        // This is especially applicable to server endpoints that return JSON responses.
        mapperBuilder().disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT);
    }

    @Override
    public JsonMapper get()
    {
        return create();
    }
}
