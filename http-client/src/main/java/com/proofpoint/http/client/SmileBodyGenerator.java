/*
 * Copyright 2014 Proofpoint, Inc.
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
package com.proofpoint.http.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.google.common.annotations.Beta;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.ObjectMapperProvider;

import java.io.OutputStream;
import java.lang.reflect.Type;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

@Beta
public class SmileBodyGenerator<T>
        implements BodyGenerator
{
    private static final Supplier<ObjectMapper> OBJECT_MAPPER_SUPPLIER = Suppliers.memoize(new Supplier<ObjectMapper>()
    {
        @Override
        public ObjectMapper get()
        {
            return new ObjectMapperProvider().get();
        }
    });

    private final Type genericType;
    private final T instance;

    public static <T> SmileBodyGenerator<T> smileBodyGenerator(JsonCodec<T> jsonCodec, T instance)
    {
        return new SmileBodyGenerator<>(jsonCodec.getType(), instance);
    }

    private SmileBodyGenerator(Type genericType, T instance)
    {
        this.genericType = genericType;
        this.instance = instance;
    }

    @Override
    public void write(OutputStream out)
            throws Exception
    {
        ObjectMapper objectMapper = OBJECT_MAPPER_SUPPLIER.get();
        JsonGenerator jsonGenerator = new SmileFactory().createGenerator(out);

        // Important: we are NOT to close the underlying stream after
        // mapping, so we need to instruct generator:
        jsonGenerator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        // 04-Mar-2010, tatu: How about type we were given? (if any)
        JavaType rootType = null;
        if (genericType != null && instance != null) {
            // 10-Jan-2011, tatu: as per [JACKSON-456], it's not safe to just force root
            // type since it prevents polymorphic type serialization. Since we really
            // just need this for generics, let's only use generic type if it's truly
            // generic.
            if (genericType.getClass() != Class.class) { // generic types are other implementations of 'java.lang.reflect.Type'
                // This is still not exactly right; should root type be further
                // specialized with 'value.getClass()'? Let's see how well this works before
                // trying to come up with more complete solution.
                rootType = objectMapper.getTypeFactory().constructType(genericType);
                // 26-Feb-2011, tatu: To help with [JACKSON-518], we better recognize cases where
                // type degenerates back into "Object.class" (as is the case with plain TypeVariable,
                // for example), and not use that.
                //
                if (rootType.getRawClass() == Object.class) {
                    rootType = null;
                }
            }
        }

        if (rootType != null) {
            objectMapper.writerWithType(rootType).writeValue(jsonGenerator, instance);
        }
        else {
            objectMapper.writeValue(jsonGenerator, instance);
        }
    }
}
