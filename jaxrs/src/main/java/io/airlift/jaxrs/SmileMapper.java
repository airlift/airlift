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
package io.airlift.jaxrs;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

@Provider
@Consumes("application/x-jackson-smile")
@Produces("application/x-jackson-smile; qs=0.1")
public class SmileMapper
        extends AbstractJacksonMapper
{
    @Inject
    public SmileMapper(ObjectMapper objectMapper)
    {
        super(objectMapper);
    }

    @Override
    protected JsonFactory getReaderJsonFactory()
    {
        return new SmileFactory();
    }

    @Override
    protected void write(Object value,
            Optional<JavaType> rootType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream outputStream)
            throws IOException
    {
        JsonGenerator jsonGenerator = new SmileFactory().createGenerator(outputStream);

        // Do not close underlying stream after mapping
        jsonGenerator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        rootType.map(objectMapper::writerFor)
                .orElseGet(objectMapper::writer)
                .writeValue(jsonGenerator, value);
    }
}
