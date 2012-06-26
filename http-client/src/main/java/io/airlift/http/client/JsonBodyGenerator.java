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
package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import io.airlift.json.JsonCodec;

import java.io.OutputStream;

@Beta
public class JsonBodyGenerator<T> implements BodyGenerator
{
    public static <T> JsonBodyGenerator<T> jsonBodyGenerator(JsonCodec<T> jsonCodec, T instance)
    {
        return new JsonBodyGenerator<T>(jsonCodec, instance);
    }

    private byte[] json;

    private JsonBodyGenerator(JsonCodec<T> jsonCodec, T instance)
    {
        json = jsonCodec.toJson(instance).getBytes(Charsets.UTF_8);
    }

    @Override
    public void write(OutputStream out)
            throws Exception
    {
        out.write(json);
    }
}
