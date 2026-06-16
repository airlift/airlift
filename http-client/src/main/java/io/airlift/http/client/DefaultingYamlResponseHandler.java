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

import java.io.InputStream;
import java.util.Set;

import static io.airlift.http.client.ResponseHandlerUtils.isYamlContent;

public class DefaultingYamlResponseHandler<T>
        implements ResponseHandler<T, RuntimeException>
{
    public static <T> DefaultingYamlResponseHandler<T> createDefaultingYamlResponseHandler(YamlCodec<T> yamlCodec, T defaultValue)
    {
        return new DefaultingYamlResponseHandler<>(yamlCodec, defaultValue);
    }

    public static <T> DefaultingYamlResponseHandler<T> createDefaultingYamlResponseHandler(YamlCodec<T> yamlCodec, T defaultValue, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new DefaultingYamlResponseHandler<>(yamlCodec, defaultValue, firstSuccessfulResponseCode, otherSuccessfulResponseCodes);
    }

    private final YamlCodec<T> yamlCodec;
    private final T defaultValue;
    private final Set<Integer> successfulResponseCodes;

    private DefaultingYamlResponseHandler(YamlCodec<T> yamlCodec, T defaultValue)
    {
        this(yamlCodec, defaultValue, 200, 201, 202, 203, 204, 205, 206);
    }

    private DefaultingYamlResponseHandler(YamlCodec<T> yamlCodec, T defaultValue, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        this.yamlCodec = yamlCodec;
        this.defaultValue = defaultValue;
        this.successfulResponseCodes = ImmutableSet.<Integer>builder().add(firstSuccessfulResponseCode).addAll(Ints.asList(otherSuccessfulResponseCodes)).build();
    }

    @Override
    public T handleException(Request request, Exception exception)
    {
        return defaultValue;
    }

    @Override
    public T handle(Request request, Response response)
    {
        if (!successfulResponseCodes.contains(response.getStatusCode())) {
            return defaultValue;
        }
        if (!isYamlContent(response)) {
            return defaultValue;
        }
        try (InputStream inputStream = response.getInputStream()) {
            return yamlCodec.fromYaml(inputStream);
        }
        catch (Exception e) {
            return defaultValue;
        }
    }
}
