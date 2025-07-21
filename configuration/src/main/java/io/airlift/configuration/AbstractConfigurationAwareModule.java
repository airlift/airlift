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
package io.airlift.configuration;

import com.google.common.reflect.AbstractInvocationHandler;
import com.google.inject.Binder;
import com.google.inject.Module;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;

public abstract class AbstractConfigurationAwareModule
        implements ConfigurationAwareModule
{
    private ConfigurationFactory configurationFactory;
    private Binder binder;

    @Override
    public synchronized void setConfigurationFactory(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = requireNonNull(configurationFactory, "configurationFactory is null");
    }

    @Override
    public final synchronized void configure(Binder binder)
    {
        checkState(configurationFactory != null, "configurationFactory was not set");
        checkState(this.binder == null, "re-entry not allowed");
        this.binder = requireNonNull(binder, "binder is null");
        try {
            setup(ForbidInstallBinder.proxy(binder));
        }
        finally {
            this.binder = null;
        }
    }

    protected void consumeProperty(ConfigPropertyMetadata property)
    {
        configurationFactory.consumeProperty(property);
    }

    protected Map<String, String> getProperties()
    {
        return configurationFactory.getProperties();
    }

    protected Optional<String> getProperty(String name)
    {
        return Optional.ofNullable(configurationFactory.getProperties().get(name));
    }

    protected synchronized <T> T buildConfigObject(Class<T> configClass)
    {
        configBinder(binder).bindConfig(configClass);
        return configurationFactory.build(configClass);
    }

    protected synchronized <T> T buildConfigObject(Class<T> configClass, String prefix)
    {
        configBinder(binder).bindConfig(configClass, prefix);
        return configurationFactory.build(configClass, prefix);
    }

    protected synchronized void install(Module module)
    {
        if (module instanceof ConfigurationAwareModule configurationAwareModule) {
            configurationAwareModule.setConfigurationFactory(configurationFactory);
        }
        binder.install(module);
    }

    protected abstract void setup(Binder binder);

    private static class ForbidInstallBinder
            extends AbstractInvocationHandler
    {
        private static final Method INSTALL_METHOD;

        static {
            try {
                INSTALL_METHOD = Binder.class.getMethod("install", Module.class);
            }
            catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        static Binder proxy(Binder binder)
        {
            return (Binder) Proxy.newProxyInstance(
                    ForbidInstallBinder.class.getClassLoader(),
                    new Class<?>[] {Binder.class},
                    new ForbidInstallBinder(binder));
        }

        private final Binder delegate;

        public ForbidInstallBinder(Binder delegate)
        {
            this.delegate = requireNonNull(delegate, "delegate is null");
        }

        @Override
        protected Object handleInvocation(Object proxy, Method method, Object[] args)
                throws Throwable
        {
            if (INSTALL_METHOD.equals(method) && (args[0] instanceof ConfigurationAwareModule)) {
                throw new IllegalStateException("Use super.install() for ConfigurationAwareModule, not binder.install()");
            }

            return method.invoke(delegate, args);
        }
    }
}
