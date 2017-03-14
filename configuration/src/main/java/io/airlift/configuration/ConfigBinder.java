package io.airlift.configuration;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

public class ConfigBinder
{
    public static ConfigBinder configBinder(Binder binder)
    {
        return new ConfigBinder(new GuiceConfigBinder(binder));
    }

    static ConfigBinder configBinder(ConfigurationFactory configurationFactory, Optional<Object> bindingSource)
    {
        return new ConfigBinder(new CallbackConfigBinder(configurationFactory, bindingSource));
    }

    public interface InternalConfigBinder
    {
        <T> void bind(ConfigurationBinding<T> configurationBinding);

        <T> void bindConfigDefaults(ConfigDefaultsHolder<T> configDefaultsHolder);

        void bindConfigurationBindingListener(ConfigurationBindingListener configurationBindingListener);
    }

    private static final class GuiceConfigBinder
            implements InternalConfigBinder
    {
        private final Binder binder;
        private Multibinder<ConfigurationBindingListenerHolder> listenerMultibinder;

        public GuiceConfigBinder(Binder binder)
        {
            this.binder = requireNonNull(binder, "binder is null").skipSources(getClass(), ConfigBinder.class);
            this.listenerMultibinder = newSetBinder(binder, ConfigurationBindingListenerHolder.class);
        }

        @Override
        public <T> void bind(ConfigurationBinding<T> configurationBinding)
        {
            Key<T> key = configurationBinding.getKey();
            binder.bind(key).toProvider(new ConfigurationProvider<>(configurationBinding));
            createConfigDefaultsBinder(key);
        }

        @Override
        public <T> void bindConfigDefaults(ConfigDefaultsHolder<T> configDefaultsHolder)
        {
            createConfigDefaultsBinder(configDefaultsHolder.getConfigKey()).addBinding().toInstance(configDefaultsHolder);
        }

        @Override
        public void bindConfigurationBindingListener(ConfigurationBindingListener configurationBindingListener)
        {
            listenerMultibinder.addBinding().toInstance(new ConfigurationBindingListenerHolder(configurationBindingListener));
        }

        private <T> Multibinder<ConfigDefaultsHolder<T>> createConfigDefaultsBinder(Key<T> key)
        {
            TypeLiteral<ConfigDefaultsHolder<T>> typeLiteral = getTypeLiteral(key);

            if (key.getAnnotationType() == null) {
                return newSetBinder(binder, typeLiteral);
            }
            if (key.hasAttributes()) {
                return newSetBinder(binder, typeLiteral, key.getAnnotation());
            }
            return newSetBinder(binder, typeLiteral, key.getAnnotationType());
        }

        @SuppressWarnings("unchecked")
        private static <T> TypeLiteral<ConfigDefaultsHolder<T>> getTypeLiteral(Key<T> key)
        {
            TypeToken<T> typeToken = (TypeToken<T>) TypeToken.of(key.getTypeLiteral().getType());
            return (TypeLiteral<ConfigDefaultsHolder<T>>)
                    TypeLiteral.get(new TypeToken<ConfigDefaultsHolder<T>>() {}
                            .where(new TypeParameter<T>() {}, typeToken)
                            .getType());
        }
    }

    private static final class CallbackConfigBinder
            implements InternalConfigBinder
    {
        private final ConfigurationFactory configurationFactory;
        private final Optional<Object> bindingSource;

        public CallbackConfigBinder(ConfigurationFactory configurationFactory, Optional<Object> bindingSource)
        {
            this.configurationFactory = requireNonNull(configurationFactory, "configurationFactory is null");
            this.bindingSource = requireNonNull(bindingSource, "bindingSource is null");
        }

        @Override
        public <T> void bind(ConfigurationBinding<T> configurationBinding)
        {
            configurationFactory.registerConfigurationProvider(new ConfigurationProvider<>(configurationBinding), bindingSource);
        }

        @Override
        public <T> void bindConfigDefaults(ConfigDefaultsHolder<T> configDefaultsHolder)
        {
            configurationFactory.registerConfigDefaults(configDefaultsHolder);
        }

        @Override
        public void bindConfigurationBindingListener(ConfigurationBindingListener configurationBindingListener)
        {
            configurationFactory.addConfigurationBindingListener(configurationBindingListener);
        }
    }

    private final InternalConfigBinder binder;

    private ConfigBinder(InternalConfigBinder binder)
    {
        this.binder = requireNonNull(binder, "binder is null");
    }

    public <T> void bindConfig(Class<T> configClass)
    {
        requireNonNull(configClass, "configClass is null");

        bindConfig(Key.get(configClass), configClass, null);
    }

    public <T> void bindConfig(Class<T> configClass, Annotation annotation)
    {
        requireNonNull(configClass, "configClass is null");
        requireNonNull(annotation, "annotation is null");

        bindConfig(Key.get(configClass, annotation), configClass, null);
    }

    public <T> void bindConfig(Class<T> configClass, Class<? extends Annotation> annotation)
    {
        requireNonNull(configClass, "configClass is null");
        requireNonNull(annotation, "annotation is null");

        bindConfig(Key.get(configClass, annotation), configClass, null);
    }

    public <T> void bindConfig(Class<T> configClass, String prefix)
    {
        requireNonNull(configClass, "configClass is null");

        bindConfig(Key.get(configClass), configClass, prefix);
    }

    public <T> void bindConfig(Class<T> configClass, Annotation annotation, String prefix)
    {
        requireNonNull(configClass, "configClass is null");
        requireNonNull(annotation, "annotation is null");

        bindConfig(Key.get(configClass, annotation), configClass, prefix);
    }

    public <T> void bindConfig(Class<T> configClass, Class<? extends Annotation> annotation, String prefix)
    {
        requireNonNull(configClass, "configClass is null");
        requireNonNull(annotation, "annotation is null");

        bindConfig(Key.get(configClass, annotation), configClass, prefix);
    }

    public <T> void bindConfig(Key<T> key, Class<T> configClass, String prefix)
    {
        binder.bind(new ConfigurationBinding<>(key, configClass, Optional.ofNullable(prefix)));
    }

    public <T> void bindConfigDefaults(Class<T> configClass, ConfigDefaults<T> configDefaults)
    {
        requireNonNull(configClass, "configClass is null");
        requireNonNull(configDefaults, "configDefaults is null");

        bindConfigDefaults(Key.get(configClass), configDefaults);
    }

    public <T> void bindConfigDefaults(Class<T> configClass, Annotation annotation, ConfigDefaults<T> configDefaults)
    {
        requireNonNull(configClass, "configClass is null");
        requireNonNull(annotation, "annotation is null");
        requireNonNull(configDefaults, "configDefaults is null");

        bindConfigDefaults(Key.get(configClass, annotation), configDefaults);
    }

    public <T> void bindConfigDefaults(Class<T> configClass, Class<? extends Annotation> annotation, ConfigDefaults<T> configDefaults)
    {
        requireNonNull(configClass, "configClass is null");
        requireNonNull(annotation, "annotation is null");
        requireNonNull(configDefaults, "configDefaults is null");

        bindConfigDefaults(Key.get(configClass, annotation), configDefaults);
    }

    public <T> void bindConfigDefaults(Key<T> key, ConfigDefaults<T> configDefaults)
    {
        binder.bindConfigDefaults(new ConfigDefaultsHolder<>(key, configDefaults));
    }

    /**
     * Binds default values for all the instances of given config class for the current binder
     */
    public <T> void bindConfigGlobalDefaults(Class<T> configClass, ConfigDefaults<T> configDefaults)
    {
        Key<T> key = Key.get(configClass, GlobalDefaults.class);
        binder.bindConfigDefaults(new ConfigDefaultsHolder<>(key, configDefaults));
    }

    /**
     * Binds a configuration binding listener that can create additional config bindings.
     */
    public void bindConfigurationBindingListener(ConfigurationBindingListener configurationBindingListener)
    {
        binder.bindConfigurationBindingListener(configurationBindingListener);
    }
}
