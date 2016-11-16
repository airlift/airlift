package io.airlift.configuration;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

public class ConfigBinder
{
    public static ConfigBinder configBinder(Binder binder)
    {
        return new ConfigBinder(binder);
    }

    private final Binder binder;

    private ConfigBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
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
        binder.bind(key).toProvider(new ConfigurationProvider<>(key, configClass, prefix));
        createConfigDefaultsBinder(key);
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
        createConfigDefaultsBinder(key).addBinding().toInstance(new ConfigDefaultsHolder<>(key, configDefaults));
    }

    /**
     * Binds default values for all the instances of given config class for the current binder
     */
    public <T> void bindConfigGlobalDefaults(Class<T> configClass, ConfigDefaults<T> configDefaults)
    {
        Key<T> key = Key.get(configClass, GlobalDefaults.class);
        createConfigDefaultsBinder(key).addBinding().toInstance(new ConfigDefaultsHolder<>(key, configDefaults));
    }

    private <T> Multibinder<ConfigDefaultsHolder<T>> createConfigDefaultsBinder(Key<T> key)
    {
        @SuppressWarnings("SerializableInnerClassWithNonSerializableOuterClass")
        Type type = new TypeToken<ConfigDefaultsHolder<T>>() {}
                .where(new TypeParameter<T>() {}, (TypeToken<T>) TypeToken.of(key.getTypeLiteral().getType()))
                .getType();

        TypeLiteral<ConfigDefaultsHolder<T>> typeLiteral = (TypeLiteral<ConfigDefaultsHolder<T>>) TypeLiteral.get(type);

        if (key.getAnnotationType() == null) {
            return newSetBinder(binder, typeLiteral);
        }
        if (key.hasAttributes()) {
            return newSetBinder(binder, typeLiteral, key.getAnnotation());
        }
        return newSetBinder(binder, typeLiteral, key.getAnnotationType());
    }
}
