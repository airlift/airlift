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
package io.airlift.api.maven;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestDefaultServiceType
{
    @Test
    public void testDefaultConstructor()
    {
        DefaultServiceType serviceType = new DefaultServiceType();

        assertThat(serviceType.id()).isEqualTo("default");
        assertThat(serviceType.version()).isEqualTo(1);
        assertThat(serviceType.title()).isEqualTo("API");
        assertThat(serviceType.description()).isEqualTo("Generated API");
    }

    @Test
    public void testCustomConstructor()
    {
        DefaultServiceType serviceType = new DefaultServiceType("public", 2, "Public API", "Service APIs");

        assertThat(serviceType.id()).isEqualTo("public");
        assertThat(serviceType.version()).isEqualTo(2);
        assertThat(serviceType.title()).isEqualTo("Public API");
        assertThat(serviceType.description()).isEqualTo("Service APIs");
    }

    @Test
    public void testRejectsInvalidVersion()
    {
        assertThatThrownBy(() -> new DefaultServiceType("public", 0, "Public API", "Service APIs"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("version must be positive");
    }

    @Test
    public void testRejectsNullId()
    {
        assertThatThrownBy(() -> new DefaultServiceType(null, 1, "Public API", "Service APIs"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id is null");
    }

    @Test
    public void testRejectsNullTitle()
    {
        assertThatThrownBy(() -> new DefaultServiceType("public", 1, null, "Service APIs"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("title is null");
    }

    @Test
    public void testRejectsNullDescription()
    {
        assertThatThrownBy(() -> new DefaultServiceType("public", 1, "Public API", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("description is null");
    }
}
