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
package io.airlift.bootstrap;

import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigurationAwareModule;
import org.testng.annotations.Test;

import static com.google.inject.name.Names.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestConfigurationAwareModule
{
    @Test
    public void testConfigAvailable()
    {
        Injector injector = new Bootstrap(new FooModule())
                .doNotInitializeLogging()
                .setRequiredConfigurationProperty("foo.enabled", "true")
                .setRequiredConfigurationProperty("bar.enabled", "true")
                .initialize();

        assertEquals(injector.getInstance(Key.get(String.class, named("foo"))), "fooInstance");
        assertEquals(injector.getInstance(Key.get(String.class, named("bar"))), "barInstance");
        assertEquals(injector.getInstance(Key.get(String.class, named("abc"))), "abcInstance");
    }

    @Test
    public void testInvalidInstall()
    {
        Bootstrap bootstrap = new Bootstrap(new BrokenInstallModule())
                .doNotInitializeLogging();

        assertThatThrownBy(bootstrap::initialize).isInstanceOfSatisfying(CreationException.class, e ->
                assertThat(e.getErrorMessages()).hasOnlyOneElementSatisfying(m ->
                        assertThat(m.getMessage()).endsWith("Use super.install() for ConfigurationAwareModule, not binder.install()")));
    }

    @Test
    public void testCombine()
    {
        Module combined = ConfigurationAwareModule.combine(
                new AbstractConfigurationAwareModule()
                {
                    @Override
                    protected void setup(Binder binder)
                    {
                        assertTrue(buildConfigObject(FooConfig.class).isFoo());
                        binder.bind(String.class).annotatedWith(named("foo")).toInstance("fooInstance");
                    }
                },
                new AbstractConfigurationAwareModule()
                {
                    @Override
                    protected void setup(Binder binder)
                    {
                        assertTrue(buildConfigObject(BarConfig.class).isBar());
                        binder.bind(String.class).annotatedWith(named("bar")).toInstance("barInstance");
                    }
                });

        Injector injector = new Bootstrap(combined)
                .doNotInitializeLogging()
                .setRequiredConfigurationProperty("foo.enabled", "true")
                .setRequiredConfigurationProperty("bar.enabled", "true")
                .initialize();

        assertEquals(injector.getInstance(Key.get(String.class, named("foo"))), "fooInstance");
        assertEquals(injector.getInstance(Key.get(String.class, named("bar"))), "barInstance");
    }

    public static class FooModule
            extends AbstractConfigurationAwareModule
    {
        @Override
        protected void setup(Binder binder)
        {
            assertTrue(buildConfigObject(FooConfig.class).isFoo());
            install(new BarModule());
            binder.bind(String.class).annotatedWith(named("foo")).toInstance("fooInstance");
            binder.install(new AbcModule());
        }
    }

    public static class BarModule
            extends AbstractConfigurationAwareModule
    {
        @Override
        protected void setup(Binder binder)
        {
            assertTrue(buildConfigObject(BarConfig.class).isBar());
            binder.bind(String.class).annotatedWith(named("bar")).toInstance("barInstance");
        }
    }

    public static class AbcModule
            implements Module
    {
        @Override
        public void configure(Binder binder)
        {
            binder.bind(String.class).annotatedWith(named("abc")).toInstance("abcInstance");
        }
    }

    public static class BrokenInstallModule
            extends AbstractConfigurationAwareModule
    {
        @Override
        protected void setup(Binder binder)
        {
            binder.install(new FooModule());
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

    public static class BarConfig
    {
        private boolean bar;

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
    }
}
