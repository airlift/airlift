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

import javax.inject.Inject;
import javax.inject.Provider;

import java.lang.reflect.Type;

class JsonCodecProvider implements Provider<JsonCodec<?>>
{
    private final Type type;
    private JsonCodecFactory jsonCodecFactory;

    public JsonCodecProvider(Type type)
    {
        this.type = type;
    }

    @Inject
    public void setJsonCodecFactory(JsonCodecFactory jsonCodecFactory)
    {
        this.jsonCodecFactory = jsonCodecFactory;
    }

    @Override
    public JsonCodec<?> get()
    {
        return jsonCodecFactory.jsonCodec(type);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JsonCodecProvider that = (JsonCodecProvider) o;

        if (!type.equals(that.type)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return type.hashCode();
    }
}
