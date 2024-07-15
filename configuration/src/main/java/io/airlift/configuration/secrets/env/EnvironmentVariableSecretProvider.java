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
package io.airlift.configuration.secrets.env;

import io.airlift.spi.secrets.SecretProvider;

public class EnvironmentVariableSecretProvider
        implements SecretProvider
{
    @Override
    public String resolveSecretValue(String key)
    {
        String envValue = System.getenv().get(key);
        if (envValue == null) {
            throw new RuntimeException("Environment variable is not set: " + key);
        }
        return envValue;
    }
}
