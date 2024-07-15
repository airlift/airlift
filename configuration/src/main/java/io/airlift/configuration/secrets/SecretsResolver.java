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
package io.airlift.configuration.secrets;

import com.google.common.collect.ImmutableMap;
import io.airlift.spi.secrets.SecretProvider;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static java.util.regex.Matcher.quoteReplacement;

public class SecretsResolver
{
    private static final Pattern PATTERN = Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_-]*):([a-zA-Z][a-zA-Z0-9_-]*)}");

    private final Map<String, SecretProvider> secretProviders;

    public SecretsResolver(Map<String, SecretProvider> secretProviders)
    {
        this.secretProviders = ImmutableMap.copyOf(requireNonNull(secretProviders, "secretProviders is null"));
    }

    public Map<String, String> getResolvedConfiguration(Map<String, String> properties)
    {
        return getResolvedConfiguration(properties, (propertyKey, throwable) -> {
            throw new RuntimeException(throwable.getMessage());
        });
    }

    public Map<String, String> getResolvedConfiguration(Map<String, String> properties, BiConsumer<String, Throwable> onError)
    {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builderWithExpectedSize(properties.size());
        properties.forEach((propertyKey, propertyValue) -> {
            try {
                builder.put(propertyKey, resolveConfiguration(propertyValue));
            }
            catch (RuntimeException exception) {
                onError.accept(propertyKey, exception);
            }
        });
        return builder.buildOrThrow();
    }

    private String resolveConfiguration(String configurationValue)
    {
        StringBuilder replacedPropertyValue = new StringBuilder();
        Matcher matcher = PATTERN.matcher(configurationValue);
        while (matcher.find()) {
            String secretProviderName = matcher.group(1).toLowerCase(ENGLISH);
            SecretProvider secretProvider = secretProviders.get(secretProviderName);
            checkArgument(secretProvider != null, "No secret provider for key '%s'", secretProviderName);
            String keyName = matcher.group(2);
            matcher.appendReplacement(replacedPropertyValue, quoteReplacement(secretProvider.resolveSecretValue(keyName)));
        }
        matcher.appendTail(replacedPropertyValue);
        return replacedPropertyValue.toString();
    }
}
