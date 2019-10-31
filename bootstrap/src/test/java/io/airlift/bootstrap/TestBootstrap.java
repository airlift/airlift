/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package io.airlift.bootstrap;

import com.google.common.collect.ImmutableMap;
import com.google.inject.ConfigurationException;
import com.google.inject.ProvisionException;
import org.testng.annotations.Test;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.airlift.bootstrap.Bootstrap.replaceEnvironmentVariables;
import static io.airlift.testing.Assertions.assertContains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestBootstrap
{
    @Test
    public void testRequiresExplicitBindings()
    {
        Bootstrap bootstrap = new Bootstrap();
        try {
            bootstrap.initialize().getInstance(Instance.class);
            fail("should require explicit bindings");
        }
        catch (ConfigurationException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "Explicit bindings are required");
        }
    }

    @Test
    public void testDoesNotAllowCircularDependencies()
    {
        Bootstrap bootstrap = new Bootstrap(binder -> {
            binder.bind(InstanceA.class);
            binder.bind(InstanceB.class);
        });

        try {
            bootstrap.initialize().getInstance(InstanceA.class);
            fail("should not allow circular dependencies");
        }
        catch (ProvisionException e) {
            assertContains(e.getErrorMessages().iterator().next().getMessage(), "circular dependencies are disabled");
        }
    }

    @Test
    public void testEnvironmentVariableReplacement()
    {
        Map<String, String> original = ImmutableMap.<String, String>builder()
                .put("apple", "apple-value")
                .put("grape", "${ENV:GRAPE}")
                .put("peach", "${ENV:PEACH}")
                .put("grass", "${ENV:!!!}")
                .put("pear", "${ENV:X_PEAR}")
                .put("cherry", "${ENV:X_CHERRY}")
                .put("orange", "orange-value")
                .build();

        Map<String, String> environment = ImmutableMap.<String, String>builder()
                .put("GRAPE", "env-grape")
                .put("X_CHERRY", "env-cherry")
                .build();

        List<String> errors = new ArrayList<>();
        Map<String, String> actual = replaceEnvironmentVariables(original, environment, (key, error) -> errors.add(error));

        Map<String, String> expected = ImmutableMap.<String, String>builder()
                .put("apple", "apple-value")
                .put("grape", "env-grape")
                .put("grass", "${ENV:!!!}")
                .put("cherry", "env-cherry")
                .put("orange", "orange-value")
                .build();

        assertEquals(actual, expected);

        assertThat(errors).containsExactly(
                "Configuration property 'peach' references unset environment variable 'PEACH'",
                "Configuration property 'pear' references unset environment variable 'X_PEAR'");
    }

    public static class Instance {}

    public static class InstanceA
    {
        @Inject
        public InstanceA(InstanceB b) {}
    }

    public static class InstanceB
    {
        @Inject
        public InstanceB(InstanceA a) {}
    }
}
