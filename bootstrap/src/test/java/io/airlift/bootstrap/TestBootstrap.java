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

import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.spi.Message;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigPropertyMetadata;
import io.airlift.configuration.ConfigSecuritySensitive;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class TestBootstrap
{
    private static boolean fooInstanceCreated;

    @Test
    public void testBootstrapListener()
    {
        AtomicReference<Bootstrap> initializedBootstrap = new AtomicReference<>();
        AtomicReference<Injector> initializedInjector = new AtomicReference<>();
        AtomicReference<Bootstrap> failedBootstrap = new AtomicReference<>();
        AtomicReference<Throwable> initializationFailure = new AtomicReference<>();
        BootstrapListener listener = new BootstrapListener()
        {
            @Override
            public void bootstrapInitialized(Bootstrap bootstrap, Injector injector)
            {
                initializedBootstrap.set(bootstrap);
                initializedInjector.set(injector);
            }

            @Override
            public void bootstrapFailed(Bootstrap bootstrap, Throwable failure)
            {
                failedBootstrap.set(bootstrap);
                initializationFailure.set(failure);
            }
        };

        Bootstrap.addListener(listener);
        try {
            Bootstrap successful = new Bootstrap();
            Injector injector = successful.initialize();
            assertThat(initializedBootstrap).hasValue(successful);
            assertThat(initializedInjector).hasValue(injector);
            injector.getInstance(LifeCycleManager.class).stop();

            Bootstrap failing = new Bootstrap(_ -> {
                throw new IllegalStateException("expected failure");
            });
            assertThatThrownBy(failing::initialize)
                    .isInstanceOf(ApplicationConfigurationException.class);
            assertThat(failedBootstrap).hasValue(failing);
            assertThat(initializationFailure.get())
                    .isInstanceOf(ApplicationConfigurationException.class);
        }
        finally {
            Bootstrap.removeListener(listener);
        }
    }

    @Test
    public void testBootstrapListenerSnapshotIsStableDuringInitialization()
    {
        List<String> notifications = new ArrayList<>();
        AtomicBoolean mutateListeners = new AtomicBoolean(true);
        BootstrapListener addedListener = new BootstrapListener()
        {
            @Override
            public void bootstrapInitialized(Bootstrap bootstrap, Injector injector)
            {
                notifications.add("added");
            }
        };
        BootstrapListener removedListener = new BootstrapListener()
        {
            @Override
            public void bootstrapInitialized(Bootstrap bootstrap, Injector injector)
            {
                notifications.add("removed");
            }
        };

        Bootstrap.addListener(removedListener);
        try {
            Injector firstInjector = new Bootstrap(_ -> {
                if (mutateListeners.compareAndSet(true, false)) {
                    Bootstrap.removeListener(removedListener);
                    Bootstrap.addListener(addedListener);
                }
            }).initialize();
            firstInjector.getInstance(LifeCycleManager.class).stop();
            assertThat(notifications).containsExactly("removed");

            notifications.clear();
            Injector secondInjector = new Bootstrap().initialize();
            secondInjector.getInstance(LifeCycleManager.class).stop();
            assertThat(notifications).containsExactly("added");
        }
        finally {
            Bootstrap.removeListener(addedListener);
            Bootstrap.removeListener(removedListener);
        }
    }

    @Test
    public void testBootstrapListenerFailuresAreIsolated()
            throws Exception
    {
        IllegalStateException initializedListenerFailure = new IllegalStateException("expected initialized listener failure");
        IllegalStateException failedListenerFailure = new IllegalStateException("expected failed listener failure");
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        BootstrapListener failingListener = new BootstrapListener()
        {
            @Override
            public void bootstrapInitialized(Bootstrap bootstrap, Injector injector)
            {
                throw initializedListenerFailure;
            }

            @Override
            public void bootstrapFailed(Bootstrap bootstrap, Throwable failure)
            {
                observedFailure.set(failure);
                throw failedListenerFailure;
            }
        };
        BootstrapListener selfSuppressingListener = new BootstrapListener()
        {
            @Override
            public void bootstrapFailed(Bootstrap bootstrap, Throwable failure)
            {
                throw (RuntimeException) failure;
            }
        };

        Bootstrap.addListener(failingListener);
        Bootstrap.addListener(selfSuppressingListener);
        try {
            Injector injector = new Bootstrap().initialize();
            injector.getInstance(LifeCycleManager.class).stop();

            Bootstrap failing = new Bootstrap(_ -> {
                throw new IllegalStateException("expected bootstrap failure");
            });
            assertThatThrownBy(failing::initialize)
                    .isSameAs(observedFailure.get())
                    .hasSuppressedException(failedListenerFailure);
        }
        finally {
            Bootstrap.removeListener(selfSuppressingListener);
            Bootstrap.removeListener(failingListener);
        }
    }

    @Test
    public void testRequiresExplicitBindings()
    {
        Bootstrap bootstrap = new Bootstrap();
        try {
            bootstrap.initialize().getInstance(Instance.class);
            fail("should require explicit bindings");
        }
        catch (ConfigurationException e) {
            assertThat(e.getErrorMessages().iterator().next().getMessage()).contains("Explicit bindings are required");
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
            assertThat(e.getErrorMessages().iterator().next().getMessage()).contains("circular dependencies are disabled");
        }
    }

    @Test
    public void testUnusedProperty()
    {
        Bootstrap bootstrap = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).asEagerSingleton();
        })
                .setRequiredConfigurationProperty("foo.pasword", "foo")
                .setRequiredConfigurationProperty("foo.disabled", "true");

        assertThatThrownBy(bootstrap::initialize)
                .isInstanceOfSatisfying(ApplicationConfigurationException.class, e ->
                        assertThat(e.getErrors()).containsExactly(
                                new Message("Configuration property 'foo.disabled' was not used. Did you mean to use 'foo.enabled'?"),
                                new Message("Configuration property 'foo.pasword' was not used. Did you mean to use 'foo.password' or 'foo.password2'?")));
    }

    @Test
    public void testUserErrorsReported()
    {
        Bootstrap bootstrap = new Bootstrap(_ -> {
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
        Bootstrap bootstrap = new Bootstrap(_ -> {
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
        assertThat(root.getHandlers()).hasSize(configuredHandlerCount);
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
        bootstrap.setOptionalConfigurationProperty("foo.password", "secret");
        bootstrap.setOptionalConfigurationProperty("bar.enabled", "true");

        fooInstanceCreated = false;
        assertThat(bootstrap.configure()).containsExactly(
                new ConfigPropertyMetadata("foo.enabled", false),
                new ConfigPropertyMetadata("foo.password", true));
        assertThat(fooInstanceCreated).isFalse();
        bootstrap.initialize();
        assertThat(fooInstanceCreated).isTrue();
    }

    @Test
    public void testConfigureWithConditionalModule()
    {
        AbstractConfigurationAwareModule module = new AbstractConfigurationAwareModule()
        {
            @Override
            protected void setup(Binder binder)
            {
                if (buildConfigObject(FooConfig.class).isFoo()) {
                    configBinder(binder).bindConfig(BarConfig.class);
                }
            }
        };
        Bootstrap bootstrap = new Bootstrap(module);
        bootstrap.setOptionalConfigurationProperty("foo.enabled", "true");
        bootstrap.setOptionalConfigurationProperty("foo.password", "secret");
        bootstrap.setOptionalConfigurationProperty("bar.enabled", "false");
        bootstrap.setOptionalConfigurationProperty("bar.password", "password");

        assertThat(bootstrap.configure())
                .containsExactly(
                        new ConfigPropertyMetadata("bar.enabled", false),
                        new ConfigPropertyMetadata("bar.password", true),
                        new ConfigPropertyMetadata("foo.enabled", false),
                        new ConfigPropertyMetadata("foo.password", true));
    }

    @Test
    public void testConfigureWithPropertyPrefix()
    {
        Bootstrap bootstrap = new Bootstrap(
                binder -> configBinder(binder).bindConfig(BarConfig.class, "foo"));
        bootstrap.setOptionalConfigurationProperty("foo.bar.enabled", "true");
        bootstrap.setOptionalConfigurationProperty("foo.bar.password", "secret");
        bootstrap.setOptionalConfigurationProperty("bar.enabled", "true");
        bootstrap.setOptionalConfigurationProperty("bar.password", "secret");

        assertThat(bootstrap.configure())
                .containsExactly(
                        new ConfigPropertyMetadata("foo.bar.enabled", false),
                        new ConfigPropertyMetadata("foo.bar.password", true));
    }

    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    @Test
    public void testConfigureDoesNotInitializeEagerSingletons()
    {
        fooInstanceCreated = false;

        Bootstrap bootstrap = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).asEagerSingleton();
        });
        bootstrap.setOptionalConfigurationProperty("foo.enabled", "true");

        bootstrap.configure();
        assertThat(fooInstanceCreated).isFalse();
    }

    @Test
    public void testSkipErrorReporting()
    {
        Bootstrap bootstrap = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).asEagerSingleton();
        });
        bootstrap.setOptionalConfigurationProperty("foo.enabled", "shouldBeBoolean");
        bootstrap.skipErrorReporting();

        bootstrap.configure(); // This should not fail when error reporting is skipped

        assertThatThrownBy(bootstrap::initialize)
                .isInstanceOf(CreationException.class)
                .hasMessageContaining("Invalid value 'shouldBeBoolean' for type boolean (property 'foo.enabled')");
    }

    @Test
    public void testDisableEnvInterpolation()
    {
        FooConfig interpolatedConfig = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).asEagerSingleton();
        })
                .setOptionalConfigurationProperty("foo.password", "${ENV:FOO_PASSWORD}")
                .initialize()
                .getInstance(FooConfig.class);

        assertThat(interpolatedConfig.getPassword()).isEqualTo("superSecretPassword");

        Bootstrap bootstrap = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).asEagerSingleton();
        })
                .disableEnvInterpolation();

        assertThatThrownBy(bootstrap.setOptionalConfigurationProperty("foo.password", "${env:FOO_PASSWORD}")::initialize)
                .isInstanceOf(ApplicationConfigurationException.class)
                .hasMessageContaining("No secret provider for key 'env'");

        Bootstrap otherBootstrap = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).asEagerSingleton();
        })
                .disableEnvInterpolation();

        assertThatThrownBy(otherBootstrap.setOptionalConfigurationProperty("foo.password", "${Env:FOO_PASSWORD}")::initialize)
                .isInstanceOf(ApplicationConfigurationException.class)
                .hasMessageContaining("No secret provider for key 'env'");
    }

    @Test
    public void testDisableSystemProperties()
    {
        System.setProperty("foo.password", "password from system properties");
        FooConfig config = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).asEagerSingleton();
        })
                .withUseSystemProperties(false)
                .initialize()
                .getInstance(FooConfig.class);

        assertThat(config.getPassword()).isNull();

        FooConfig configWithSystemProperties = new Bootstrap(binder -> {
            configBinder(binder).bindConfig(FooConfig.class);
            binder.bind(FooInstance.class).asEagerSingleton();
        })
                .withUseSystemProperties(true)
                .initialize()
                .getInstance(FooConfig.class);

        assertThat(configWithSystemProperties.getPassword()).isEqualTo("password from system properties");

        System.clearProperty("foo.password");
    }

    @Test
    public void testOptionalBindingWithLifeCycle()
    {
        Module module = binder -> {
            binder.bind(ControllerWithInstance.class).in(Scopes.SINGLETON);
            newOptionalBinder(binder, InstanceWithLifecycle.class)
                    .setDefault()
                    .to(InstanceWithLifecycleImpl.class)
                    .in(Scopes.SINGLETON);
        };

        Bootstrap bootstrap = new Bootstrap(module);
        LifeCycleManager lifeCycleManager = bootstrap.initialize().getInstance(LifeCycleManager.class);
        lifeCycleManager.stop();
    }

    @Test
    public void testConsumedProperty()
    {
        AbstractConfigurationAwareModule module = new AbstractConfigurationAwareModule()
        {
            @Override
            protected void setup(Binder binder)
            {
                this.consumeProperty(new ConfigPropertyMetadata("some-secret", true));
                this.consumeProperty(new ConfigPropertyMetadata("some-not-secret", false));
            }
        };
        Bootstrap bootstrap = new Bootstrap(module)
                .setOptionalConfigurationProperties(Map.of(
                        "some-secret", "secret-value",
                        "some-not-secret", "not-secret-value",
                        "some-other", "other-value"));

        assertThat((Object) bootstrap.configure())
                .isEqualTo(Set.of(
                        new ConfigPropertyMetadata("some-secret", true),
                        new ConfigPropertyMetadata("some-not-secret", false)));
    }

    @Test
    public void testRequiredConsumedProperty()
    {
        AbstractConfigurationAwareModule module = new AbstractConfigurationAwareModule()
        {
            @Override
            protected void setup(Binder binder)
            {
                this.consumeProperty(new ConfigPropertyMetadata("foo.password", true));
                this.consumeProperty(new ConfigPropertyMetadata("foo.disabled", false));
            }
        };
        Bootstrap bootstrap = new Bootstrap(module)
                .setRequiredConfigurationProperty("foo.password", "foo")
                .setRequiredConfigurationProperty("foo.disabled", "true");

        bootstrap.initialize(); // doesn't throw
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
        public String password;
        public String password2;

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

        public String getPassword()
        {
            return password;
        }

        @ConfigSecuritySensitive
        @Config("foo.password")
        public FooConfig setPassword(String password)
        {
            this.password = password;
            return this;
        }

        public String getPassword2()
        {
            return password2;
        }

        @ConfigSecuritySensitive
        @Config("foo.password2")
        public FooConfig setPassword2(String password)
        {
            this.password2 = password;
            return this;
        }
    }

    public static class BarConfig
    {
        private boolean bar;
        public String password;

        public boolean isBar()
        {
            return bar;
        }

        @Config("bar.enabled")
        public BarConfig setBar(boolean bar)
        {
            this.bar = bar;
            return this;
        }

        public String getPassword()
        {
            return password;
        }

        @ConfigSecuritySensitive
        @Config("bar.password")
        public BarConfig setPassword(String password)
        {
            this.password = password;
            return this;
        }
    }
}
