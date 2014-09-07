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

import com.proofpoint.json.JsonCodec;

import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static com.proofpoint.json.JsonCodec.jsonCodec;

public class TestJsonBodyGenerator
    extends AbstractCodecBodyGeneratorTest
{
    @Override
    protected <T> BodyGenerator createBodyGenerator(JsonCodec<T> jsonCodec, T instance)
    {
        return jsonBodyGenerator(jsonCodec, instance);
    }

    @Override
    protected Object decodeBody(byte[] body)
    {
        return jsonCodec(Object.class).fromJson(body);
    }
}
