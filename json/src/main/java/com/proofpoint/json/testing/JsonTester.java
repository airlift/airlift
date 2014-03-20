/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.json.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.ObjectMapperProvider;

import java.io.IOException;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.google.common.base.Throwables.propagate;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static org.testng.Assert.assertEquals;

public class JsonTester
{
    private JsonTester()
    {
    }

    public static <T> void assertJsonEncode(T pojo, Object expected)
    {
        assertJsonEncode(pojo, expected, null);
    }

    public static <T> void assertJsonEncode(T pojo, Object expected, String message)
    {
        try {
            String json = new ObjectMapperProvider().get().enable(INDENT_OUTPUT).writeValueAsString(pojo);
            StringBuilder builder = new StringBuilder();
            if (message != null) {
                builder.append(message).append(' ');
            }
            builder.append("JSON encoding ").append(json);
            assertEquals(new ObjectMapper().readValue(json, Object.class), expected, builder.toString());
        }
        catch (IOException e) {
            throw propagate(e);
        }
    }

    public static <T> T decodeJson(Class<T> tClass, Object value)
    {
        return decodeJson(jsonCodec(tClass), value);
    }

    public static <T> T decodeJson(JsonCodec<T> codec, Object value)
    {
        final String json;
        try {
            json = new ObjectMapperProvider().get().writeValueAsString(value);
        }
        catch (IOException e) {
            throw propagate(e);
        }
        return codec.fromJson(json);
    }
}
