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

import io.airlift.spi.secrets.SecretProvider;
import io.airlift.spi.secrets.SecretProviderFactory;
import io.airlift.spi.secrets.SecretsPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TestingSecretsPlugin
        implements SecretsPlugin, SecretProviderFactory
{
    public static final String NAME = "testing";

    private static final AtomicInteger nextFactoryId = new AtomicInteger();

    private final int factoryId = nextFactoryId.getAndIncrement();

    @Override
    public List<SecretProviderFactory> getSecretProviderFactories()
    {
        return List.of(this);
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public SecretProvider createSecretProvider(Map<String, String> config)
    {
        return key -> switch (key) {
            case "factory-id" -> Integer.toString(factoryId);
            case "parallel-capable" -> Boolean.toString(getClass().getClassLoader().isRegisteredAsParallelCapable());
            default -> throw new IllegalArgumentException("Unknown key: " + key);
        };
    }
}
