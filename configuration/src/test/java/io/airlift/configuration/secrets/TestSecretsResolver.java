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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.spi.secrets.SecretProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestSecretsResolver
{
    @Test
    public void testSecretsResolution()
    {
        SecretsResolver secretsResolver = new SecretsResolver(
                ImmutableMap.of("prefix", new PrefixedSecretProvider()));

        assertThat(secretsResolver.getResolvedConfiguration(ImmutableMap.of(
                "key1", "${prefix:key}",
                "key2", "${prefix:key}-abc",
                "key3", "${PREFIX:key}",
                "key4", "${PReFIX:key}",
                "key5", "normal_key")))
                .isEqualTo(ImmutableMap.of(
                        "key1", "prefix-key",
                        "key2", "prefix-key-abc",
                        "key3", "prefix-key",
                        "key4", "prefix-key",
                        "key5", "normal_key"));
    }

    @Test
    public void testSecretsResolutionSpecialCharacters()
    {
        SecretsResolver secretsResolver = new SecretsResolver(
                ImmutableMap.of("special", new MappedSecretProvider(ImmutableMap.of(
                        "some_path/some_folder", "another_path/another_folder",
                        "test:password", "test:another_password",
                        "path/secret:password", "another_path/secret:another_password"))));

        assertThat(secretsResolver.getResolvedConfiguration(ImmutableMap.of(
                "key1", "${special:some_path/some_folder}",
                "key2", "${special:test:password}-abc",
                "key3", "${SPECIAL:path/secret:password}")))
                .isEqualTo(ImmutableMap.of(
                        "key1", "another_path/another_folder",
                        "key2", "test:another_password-abc",
                        "key3", "another_path/secret:another_password"));
    }

    @Test
    public void testSecretsResolutionWithMultipleKey()
    {
        SecretsResolver secretsResolver = new SecretsResolver(
                ImmutableMap.of(
                        "prefix", new PrefixedSecretProvider(),
                        "prefix2", new PrefixedSecretProvider(),
                        "suffix", new SuffixedSecretProvider()));

        assertThat(secretsResolver.getResolvedConfiguration(ImmutableMap.of("key", "${prefix:key}-${prefix2:key2}-${suffix:key}")))
                .isEqualTo(ImmutableMap.of("key", "prefix-key-prefix-key2-key-suffix"));
    }

    @Test
    public void testSecretsResolverWithSpecialKeys()
    {
        SecretsResolver secretsResolver = new SecretsResolver(
                ImmutableMap.of(
                        "prefix", new PrefixedSecretProvider(),
                        "prefix2", new PrefixedSecretProvider()));

        assertThat(secretsResolver.getResolvedConfiguration(ImmutableMap.of(
                "key1", "${prefix2:${prefix:key}}",
                "key2", "${prefix:key",
                "key3", "{prefix:key}")))
                .isEqualTo(ImmutableMap.of(
                        // we can match special characters
                        "key1", "prefix-${prefix:key}",
                        "key2", "${prefix:key",
                        "key3", "{prefix:key}"));
    }

    @Test
    public void testSecretsResolutionWithUnknownResolver()
    {
        SecretsResolver secretsResolver = new SecretsResolver(
                ImmutableMap.of("prefix", new PrefixedSecretProvider()));

        assertThatThrownBy(() -> secretsResolver.getResolvedConfiguration(ImmutableMap.of("key", "${unknown_key:key}")))
                .hasMessageContaining("No secret provider for key 'unknown_key'");
    }

    @Test
    public void testSecretResolutionFailures()
    {
        SecretsResolver secretsResolver = new SecretsResolver(
                ImmutableMap.of("resolver", new FailureSecretProvider()));

        ImmutableList.Builder<String> errorMessages = ImmutableList.builder();

        assertThat(secretsResolver.getResolvedConfiguration(ImmutableMap.of("key", "${resolver:key}"), (propertyKey, throwable) -> errorMessages.add(throwable.getMessage()))).isEmpty();

        assertThat(errorMessages.build()).isEqualTo(ImmutableList.of("Invalid key: key"));
    }

    private static class MappedSecretProvider
            implements SecretProvider
    {
        private final Map<String, String> mapping;

        public MappedSecretProvider(Map<String, String> mapping)
        {
            this.mapping = requireNonNull(mapping, "mapping is null");
        }

        @Override
        public String resolveSecretValue(String key)
        {
            return mapping.get(key);
        }
    }

    private static class PrefixedSecretProvider
            implements SecretProvider
    {
        @Override
        public String resolveSecretValue(String key)
        {
            return "prefix-" + key;
        }
    }

    private static class SuffixedSecretProvider
            implements SecretProvider
    {
        @Override
        public String resolveSecretValue(String key)
        {
            return key + "-suffix";
        }
    }

    private static class FailureSecretProvider
            implements SecretProvider
    {
        @Override
        public String resolveSecretValue(String key)
        {
            throw new RuntimeException("Invalid key: " + key);
        }
    }
}
