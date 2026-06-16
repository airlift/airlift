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
import com.google.common.io.CountingInputStream;
import io.airlift.yaml.YamlCodec;

import java.io.InputStream;

import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseStream;
import static io.airlift.http.client.ResponseHandlerUtils.isYamlContent;
import static java.util.Objects.requireNonNull;

public class StreamingYamlResponseHandler<T>
        implements ResponseHandler<YamlResponse<T>, RuntimeException>
{
    private final YamlCodec<T> codec;

    public StreamingYamlResponseHandler(YamlCodec<T> codec)
    {
        this.codec = requireNonNull(codec, "codec is null");
    }

    public static <T> StreamingYamlResponseHandler<T> streamingYamlResponseHandler(YamlCodec<T> yamlCodec)
    {
        return new StreamingYamlResponseHandler<>(yamlCodec);
    }

    @Override
    public YamlResponse<T> handleException(Request request, Exception exception)
            throws RuntimeException
    {
        return new YamlResponse.Exception<>(request, -1, ImmutableListMultimap.of(), exception);
    }

    @Override
    public YamlResponse<T> handle(Request request, Response response)
            throws RuntimeException
    {
        int statusCode = response.getStatusCode();

        try {
            if (response.getHeader(CONTENT_TYPE).isEmpty()) {
                return new YamlResponse.NonYamlBytes<>(
                        request,
                        statusCode,
                        response.getHeaders(),
                        getResponseBytes(request, response),
                        new UnexpectedResponseException("Content-Type is not set for response", request, response));
            }

            if (isYamlContent(response)) {
                try (InputStream stream = getResponseStream(response); CountingInputStream countingInputStream = new CountingInputStream(stream)) {
                    return new YamlResponse.YamlValue<>(request, statusCode, response.getHeaders(), codec.fromYaml(countingInputStream), countingInputStream.getCount());
                }
            }

            return new YamlResponse.NonYamlBytes<>(
                    request,
                    statusCode,
                    response.getHeaders(),
                    getResponseBytes(request, response),
                    new UnexpectedResponseException("Expected YAML response from server but got %s".formatted(response.getHeader(CONTENT_TYPE).orElseThrow()), request, response));
        }
        catch (Exception e) {
            return new YamlResponse.Exception<>(request, statusCode, response.getHeaders(), e);
        }
    }
}
