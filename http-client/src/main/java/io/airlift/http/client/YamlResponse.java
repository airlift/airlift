/*
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.net.MediaType;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public sealed interface YamlResponse<T>
{
    T yamlValue();

    Optional<Throwable> exception();

    Request request();

    Multimap<HeaderName, String> headers();

    default List<String> getHeader(HeaderName name)
    {
        return ImmutableList.copyOf(headers().get(name));
    }

    int statusCode();

    record YamlValue<T>(@Override Request request, @Override int statusCode, @Override Multimap<HeaderName, String> headers, @Override T yamlValue, long bytesRead)
            implements YamlResponse<T>
    {
        public YamlValue
        {
            requireNonNull(request, "request is null");
            requireNonNull(headers, "headers is null");
            requireNonNull(yamlValue, "yamlValue is null");
        }

        @Override
        public Optional<Throwable> exception()
        {
            return Optional.empty();
        }
    }

    record Exception<T>(@Override Request request, @Override int statusCode, @Override Multimap<HeaderName, String> headers, Throwable throwable)
            implements YamlResponse<T>
    {
        public Exception
        {
            requireNonNull(request, "request is null");
            requireNonNull(throwable, "throwable is null");
        }

        @Override
        public T yamlValue()
        {
            throw new IllegalStateException("Response does not contain a YAML value", throwable);
        }

        @Override
        public Optional<Throwable> exception()
        {
            return Optional.of(throwable);
        }
    }

    record NonYamlBytes<T>(@Override Request request, @Override int statusCode, @Override Multimap<HeaderName, String> headers, byte[] responseBytes, Throwable throwable)
            implements YamlResponse<T>
    {
        public NonYamlBytes
        {
            requireNonNull(request, "request is null");
            requireNonNull(headers, "headers is null");
            requireNonNull(responseBytes, "responseBytes is null");
            requireNonNull(throwable, "throwable is null");
        }

        @Override
        public T yamlValue()
        {
            throw new IllegalStateException("Could not decode response to YAML", throwable);
        }

        @Override
        public Optional<Throwable> exception()
        {
            return Optional.of(throwable);
        }

        public String stringValue()
        {
            return new String(responseBytes, charset());
        }

        public Charset charset()
        {
            List<String> values = getHeader(CONTENT_TYPE);
            if (!values.isEmpty()) {
                try {
                    return MediaType.parse(values.getFirst()).charset().or(UTF_8);
                }
                catch (RuntimeException ignored) {
                }
            }
            return UTF_8;
        }
    }
}
