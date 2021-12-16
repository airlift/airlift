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

import com.google.inject.ConfigurationException;
import com.google.inject.ProvisionException;
import com.google.inject.spi.Message;
import org.testng.annotations.Test;

import javax.inject.Inject;

import static io.airlift.testing.Assertions.assertContains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    public void testStrictConfig()
    {
        Bootstrap bootstrap = new Bootstrap()
                .setRequiredConfigurationProperty("test-required", "foo");

        assertThatThrownBy(bootstrap::initialize)
                .isInstanceOfSatisfying(ApplicationConfigurationException.class, e ->
                        assertThat(e.getErrors()).containsExactly(
                                new Message("Configuration property 'test-required' was not used")));
    }

    @SuppressWarnings("removal")
    @Test
    public void testNonStrictConfig()
    {
        new Bootstrap()
                .setRequiredConfigurationProperty("test-required", "foo")
                .nonStrictConfig()
                .initialize();
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
