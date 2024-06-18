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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import org.junit.jupiter.api.Test;

import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;
import static org.assertj.core.api.Assertions.assertThat;

public class TestJsonCodecBinder
{
    @Test
    public void ignoresRepeatedBinding()
    {
        Injector injector = Guice.createInjector(binder -> {
            jsonCodecBinder(binder).bindJsonCodec(Integer.class);
            jsonCodecBinder(binder).bindJsonCodec(Integer.class);

            binder.bind(Dummy.class).in(Scopes.SINGLETON);
        });

        assertThat(injector.getInstance(Dummy.class).getCodec()).isNotNull();
    }

    private static class Dummy
    {
        private final JsonCodec<Integer> codec;

        @Inject
        public Dummy(JsonCodec<Integer> codec)
        {
            this.codec = codec;
        }

        public JsonCodec<Integer> getCodec()
        {
            return codec;
        }
    }
}
