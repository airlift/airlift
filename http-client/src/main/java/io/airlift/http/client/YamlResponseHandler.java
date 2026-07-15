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

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import io.airlift.yaml.YamlCodec;

import java.util.Set;

import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static io.airlift.http.client.ResponseHandlerUtils.isYamlContent;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.nio.charset.StandardCharsets.UTF_8;

public class YamlResponseHandler<T>
        implements ResponseHandler<T, RuntimeException>
{
    public static <T> YamlResponseHandler<T> createYamlResponseHandler(YamlCodec<T> yamlCodec)
    {
        return new YamlResponseHandler<>(yamlCodec);
    }

    public static <T> YamlResponseHandler<T> createYamlResponseHandler(YamlCodec<T> yamlCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new YamlResponseHandler<>(yamlCodec, firstSuccessfulResponseCode, otherSuccessfulResponseCodes);
    }

    private final YamlCodec<T> yamlCodec;
    private final Set<Integer> successfulResponseCodes;

    private YamlResponseHandler(YamlCodec<T> yamlCodec)
    {
        this(yamlCodec, 200, 201, 202, 203, 204, 205, 206);
    }

    private YamlResponseHandler(YamlCodec<T> yamlCodec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        this.yamlCodec = yamlCodec;
        this.successfulResponseCodes = ImmutableSet.<Integer>builder().add(firstSuccessfulResponseCode).addAll(Ints.asList(otherSuccessfulResponseCodes)).build();
    }

    @Override
    public T handleException(Request request, Exception exception)
    {
        throw propagate(request, exception);
    }

    @Override
    public T handle(Request request, Response response)
    {
        if (!successfulResponseCodes.contains(response.getStatusCode())) {
            throw new UnexpectedResponseException(
                    "Expected response code to be %s, but was %d".formatted(successfulResponseCodes, response.getStatusCode()),
                    request,
                    response);
        }

        if (!isYamlContent(response)) {
            throw new UnexpectedResponseException("Expected YAML response from server but got %s".formatted(response.getHeader(CONTENT_TYPE).orElse(null)), request, response);
        }

        byte[] bytes = getResponseBytes(request, response);

        try {
            return yamlCodec.fromYaml(bytes);
        }
        catch (IllegalArgumentException e) {
            String yaml = new String(bytes, UTF_8);
            throw new IllegalArgumentException("Unable to create %s from YAML response: <%s>".formatted(yamlCodec.getType(), yaml), e);
        }
    }
}
