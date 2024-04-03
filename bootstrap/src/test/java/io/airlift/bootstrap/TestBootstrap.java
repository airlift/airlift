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
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.spi.Message;
import io.airlift.configuration.Config;
import org.testng.annotations.Test;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.testing.Assertions.assertContains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestBootstrap
{
    private static boolean fooInstanceCreated;

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
    public void testUnusedProperty()
    {
        Bootstrap bootstrap = new Bootstrap()
                .setRequiredConfigurationProperty("test-required", "foo");

        assertThatThrownBy(bootstrap::initialize)
                .isInstanceOfSatisfying(ApplicationConfigurationException.class, e ->
                        assertThat(e.getErrors()).containsExactly(
                                new Message("Configuration property 'test-required' was not used")));
    }

    @Test
    public void testUserErrorsReported()
    {
        Bootstrap bootstrap = new Bootstrap(binder -> {
            throw new RuntimeException("happy user error");
        });

        assertThatThrownBy(bootstrap::initialize)
                .isInstanceOfSatisfying(ApplicationConfigurationException.class, e -> {
                    assertThat(e.getErrors().stream().map(Message::getMessage)).containsExactly(
                            "An exception was caught and reported. Message: happy user error");
                    // also check stacktrace printout
                    assertThat(e).hasStackTraceContaining("An exception was caught and reported. Message: happy user error");
                });
    }

    @Test
    public void testUserErrorsReportedWithConfigurationProblem()
    {
        Bootstrap bootstrap = new Bootstrap(binder -> {
            throw new RuntimeException("happy user error");
        })
                .setRequiredConfigurationProperty("test-required", "foo");

        assertThatThrownBy(bootstrap::initialize)
                .isInstanceOfSatisfying(ApplicationConfigurationException.class, e -> {
                    assertThat(e.getErrors().stream().map(Message::getMessage)).containsExactlyInAnyOrder(
                            "Configuration property 'test-required' was not used",
                            "An exception was caught and reported. Message: happy user error");
                    // also check stacktrace printout
                    assertThat(e).hasStackTraceContaining("Configuration property 'test-required' was not used");
                    assertThat(e).hasStackTraceContaining("An exception was caught and reported. Message: happy user error");
                });
    }

    @Test
    public void testLoggingConfiguredOnce()
    {
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        new Bootstrap().setOptionalConfigurationProperty("log.path", "tcp://0.0.0.0:0").initialize();
        int configuredHandlerCount = root.getHandlers().length;
        new Bootstrap().setOptionalConfigurationProperty("log.path", "tcp://0.0.0.0:0").initialize();
        assertEquals(root.getHandlers().length, configuredHandlerCount);
    }

    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    @Test
    public void testSeparateConfigureAndInitialize()
    {
        Bootstrap bootstrap = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).in(Scopes.SINGLETON);
        });
        bootstrap.setOptionalConfigurationProperty("foo.enabled", "true");
        bootstrap.setOptionalConfigurationProperty("bar.enabled", "true");

        fooInstanceCreated = false;
        assertThat(bootstrap.configure()).containsExactly("foo.enabled");
        assertThat(fooInstanceCreated).isFalse();
        bootstrap.initialize();
        assertThat(fooInstanceCreated).isTrue();
    }

    public static class Instance {}

    public static class InstanceA
    {
        @Inject
        public InstanceA(@SuppressWarnings("UnusedVariable") InstanceB b) {}
    }

    public static class InstanceB
    {
        @Inject
        public InstanceB(@SuppressWarnings("UnusedVariable") InstanceA a) {}
    }

    public static class FooInstance
    {
        @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
        @Inject
        public FooInstance(@SuppressWarnings("UnusedVariable") FooConfig config)
        {
            fooInstanceCreated = true;
        }
    }

    public static class FooConfig
    {
        private boolean foo;

        public boolean isFoo()
        {
            return foo;
        }

        @Config("foo.enabled")
        public FooConfig setFoo(boolean foo)
        {
            this.foo = foo;
            return this;
        }
    }
}
