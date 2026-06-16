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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.net.MediaType;
import io.airlift.http.client.FullYamlResponseHandler.YamlResponse;
import io.airlift.yaml.YamlCodec;
import jakarta.annotation.Nullable;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static io.airlift.http.client.ResponseHandlerUtils.isYamlContent;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class FullYamlResponseHandler<T>
        implements ResponseHandler<YamlResponse<T>, RuntimeException>
{
    public static <T> FullYamlResponseHandler<T> createFullYamlResponseHandler(YamlCodec<T> yamlCodec)
    {
        return new FullYamlResponseHandler<>(yamlCodec);
    }

    private final YamlCodec<T> yamlCodec;

    private FullYamlResponseHandler(YamlCodec<T> yamlCodec)
    {
        this.yamlCodec = yamlCodec;
    }

    @Override
    public YamlResponse<T> handleException(Request request, Exception exception)
    {
        throw propagate(request, exception);
    }

    @Override
    public YamlResponse<T> handle(Request request, Response response)
    {
        byte[] bytes = getResponseBytes(request, response);
        if (!isYamlContent(response)) {
            return new YamlResponse<>(response.getStatusCode(), response.getHeaders(), bytes);
        }
        return new YamlResponse<>(response.getStatusCode(), response.getHeaders(), yamlCodec, bytes);
    }

    public static class YamlResponse<T>
    {
        private final int statusCode;
        private final ListMultimap<HeaderName, String> headers;
        private final boolean hasValue;
        private final byte[] yamlBytes;
        private final byte[] responseBytes;
        private final T value;
        private final IllegalArgumentException exception;

        public YamlResponse(int statusCode, ListMultimap<HeaderName, String> headers, byte[] responseBytes)
        {
            this.statusCode = statusCode;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.hasValue = false;
            this.yamlBytes = null;
            this.responseBytes = requireNonNull(responseBytes, "responseBytes is null");
            this.value = null;
            this.exception = null;
        }

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        public YamlResponse(int statusCode, ListMultimap<HeaderName, String> headers, YamlCodec<T> yamlCodec, byte[] yamlBytes)
        {
            this.statusCode = statusCode;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.yamlBytes = requireNonNull(yamlBytes, "yamlBytes is null");
            this.responseBytes = requireNonNull(yamlBytes, "responseBytes is null");

            T value = null;
            IllegalArgumentException exception = null;
            try {
                value = yamlCodec.fromYaml(yamlBytes);
            }
            catch (IllegalArgumentException e) {
                exception = new IllegalArgumentException("Unable to create %s from YAML response:\n[%s]".formatted(yamlCodec.getType(), getYaml()), e);
            }
            this.hasValue = (exception == null);
            this.value = value;
            this.exception = exception;
        }

        public int getStatusCode()
        {
            return statusCode;
        }

        @Nullable
        @Deprecated
        public String getHeader(String name)
        {
            return getHeader(HeaderName.of(name)).orElse(null);
        }

        public Optional<String> getHeader(HeaderName name)
        {
            List<String> values = getHeaders().get(name);
            return values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.getFirst());
        }

        public List<String> getHeaders(HeaderName name)
        {
            return headers.get(name);
        }

        @Deprecated
        public List<String> getHeaders(String name)
        {
            return getHeaders(HeaderName.of(name));
        }

        public ListMultimap<HeaderName, String> getHeaders()
        {
            return headers;
        }

        public boolean hasValue()
        {
            return hasValue;
        }

        public T getValue()
        {
            if (!hasValue) {
                throw new IllegalStateException("Response does not contain a YAML value", exception);
            }
            return value;
        }

        public int getResponseSize()
        {
            return responseBytes.length;
        }

        public byte[] getResponseBytes()
        {
            return responseBytes.clone();
        }

        public String getResponseBody()
        {
            return new String(responseBytes, getCharset());
        }

        public byte[] getYamlBytes()
        {
            return (yamlBytes == null) ? null : yamlBytes.clone();
        }

        public String getYaml()
        {
            return (yamlBytes == null) ? null : new String(yamlBytes, UTF_8);
        }

        public IllegalArgumentException getException()
        {
            return exception;
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("statusCode", statusCode)
                    .add("headers", headers)
                    .add("hasValue", hasValue)
                    .add("value", value)
                    .toString();
        }

        private Charset getCharset()
        {
            try {
                return getHeader(CONTENT_TYPE)
                        .map(MediaType::parse)
                        .map(MediaType::charset)
                        .flatMap(optional -> optional.toJavaUtil())
                        .orElse(UTF_8);
            }
            catch (RuntimeException e) {
                return UTF_8;
            }
        }
    }
}
