package io.airlift.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.Version;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.datatype.guava.GuavaModule;
import tools.jackson.datatype.joda.JodaModule;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class BaseJacksonProvider<V, U extends BaseJacksonProvider<V, U>>
        implements Provider<V>
{
    private final JsonMapper.Builder jsonMapper;

    private Map<Class<?>, ValueSerializer<?>> keySerializers;
    private Map<Class<?>, KeyDeserializer> keyDeserializers;
    private Map<Class<?>, ValueSerializer<?>> jsonSerializers;
    private Map<Class<?>, ValueDeserializer<?>> jsonDeserializers;

    private final Set<JsonSubType> jsonSubTypes = new HashSet<>();

    protected BaseJacksonProvider(JsonFactoryBuilder jsonFactoryBuilder)
    {
        // Disable the length limit, caller will be responsible for validating the input length
        jsonFactoryBuilder.streamReadConstraints(StreamReadConstraints
                .builder()
                .maxStringLength(Integer.MAX_VALUE)
                .maxNestingDepth(Integer.MAX_VALUE)
                .maxNameLength(Integer.MAX_VALUE)
                .maxDocumentLength(Long.MAX_VALUE)
                .build());

        jsonFactoryBuilder.streamWriteConstraints(StreamWriteConstraints
                .builder()
                .maxNestingDepth(Integer.MAX_VALUE)
                .build());

        jsonFactoryBuilder.enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER);
        jsonFactoryBuilder.enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER);
        jsonFactoryBuilder.enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER);

        /*
         * When multiple threads deserialize JSON responses concurrently,
         * Jackson's default behavior of interning field names causes severe lock contention
         * on the JVM's global String pool. This manifests as threads blocked waiting at
         * InternCache.intern.
         *
         * Disabling INTERN_FIELD_NAMES eliminates this contention with minimal performance
         * impact - field name deduplication becomes slightly less memory-efficient, but the
         * elimination of lock contention far outweighs this cost in high-concurrency scenarios.
         *
         * See: https://github.com/FasterXML/jackson-core/issues/332.
         */
        jsonFactoryBuilder.disable(JsonFactory.Feature.INTERN_PROPERTY_NAMES);
        jsonFactoryBuilder.recyclerPool(JsonRecyclerPools.threadLocalPool());

        jsonMapper = JsonMapper.builder(jsonFactoryBuilder.build());

        // ignore unknown fields (for backwards compatibility)
        jsonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // do not fail on null injection for primitive fields
        // enabled by default as of Jackson 3.0 (in 2.x it was disabled)
        jsonMapper.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

        // do not allow converting a float to an integer
        jsonMapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

        // use ISO dates
        jsonMapper.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);

        // When serialization fails in the middle, it's better to return a truncated (invalid) JSON
        // than something that could be interpreted as a valid (but incorrect) result.
        // This is especially applicable to server endpoints that return JSON responses.
        jsonMapper.disable(StreamWriteFeature.AUTO_CLOSE_CONTENT);

        // Skip fields that are null or absent (Optional) when serializing objects.
        // This only applies to mapped object fields, not containers like Map or List.
        jsonMapper.changeDefaultPropertyInclusion(_ -> JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.ALWAYS));

        // When a field is null or absent (Optional) in JSON, set the value to a default value (i.e. empty Optional)
        jsonMapper.changeDefaultNullHandling(_ -> JsonSetter.Value.construct(Nulls.DEFAULT, Nulls.DEFAULT));

        // disable auto detection of json properties... all properties must be explicit
        jsonMapper.changeDefaultVisibility(visibilityChecker -> visibilityChecker
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
                .withScalarConstructorVisibility(JsonAutoDetect.Visibility.NONE)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE));

        jsonMapper.addModule(new GuavaModule());
        jsonMapper.addModule(new RecordAutoDetectModule());

        try {
            getClass().getClassLoader().loadClass("org.joda.time.DateTime");
            jsonMapper.addModule(new JodaModule());
        }
        catch (ClassNotFoundException ignored) {
        }
    }

    @Inject(optional = true)
    public void setJsonSerializers(Map<Class<?>, ValueSerializer<?>> jsonSerializers)
    {
        this.jsonSerializers = ImmutableMap.copyOf(jsonSerializers);
    }

    public U withJsonSerializers(Map<Class<?>, ValueSerializer<?>> jsonSerializers)
    {
        setJsonSerializers(jsonSerializers);
        return (U) this;
    }

    @Inject(optional = true)
    public void setJsonDeserializers(Map<Class<?>, ValueDeserializer<?>> jsonDeserializers)
    {
        this.jsonDeserializers = ImmutableMap.copyOf(jsonDeserializers);
    }

    public U withJsonDeserializers(Map<Class<?>, ValueDeserializer<?>> jsonDeserializers)
    {
        setJsonDeserializers(jsonDeserializers);
        return (U) this;
    }

    @Inject(optional = true)
    public void setKeySerializers(@JsonKeySerde Map<Class<?>, ValueSerializer<?>> keySerializers)
    {
        this.keySerializers = keySerializers;
    }

    public U withKeySerializers(@JsonKeySerde Map<Class<?>, ValueSerializer<?>> keySerializers)
    {
        setKeySerializers(keySerializers);
        return (U) this;
    }

    @Inject(optional = true)
    public void setKeyDeserializers(@JsonKeySerde Map<Class<?>, KeyDeserializer> keyDeserializers)
    {
        this.keyDeserializers = keyDeserializers;
    }

    public U withKeyDeserializers(@JsonKeySerde Map<Class<?>, KeyDeserializer> keyDeserializers)
    {
        setKeyDeserializers(keyDeserializers);
        return (U) this;
    }

    @Inject(optional = true)
    public void setModules(Set<JacksonModule> modules)
    {
        modules.forEach(jsonMapper::addModule);
    }

    public U withModules(Set<JacksonModule> modules)
    {
        setModules(modules);
        return (U) this;
    }

    @Inject(optional = true)
    public void setJsonSubTypes(Set<JsonSubType> jsonSubTypes)
    {
        this.jsonSubTypes.addAll(jsonSubTypes);
    }

    public U withJsonSubTypes(Set<JsonSubType> jsonSubTypes)
    {
        setJsonSubTypes(jsonSubTypes);
        return (U) this;
    }

    protected JsonMapper create()
    {
        if (jsonSerializers != null || jsonDeserializers != null || keySerializers != null || keyDeserializers != null) {
            SimpleModule module = new SimpleModule(getClass().getName(), new Version(1, 0, 0, null, null, null));
            if (jsonSerializers != null) {
                for (Map.Entry<Class<?>, ValueSerializer<?>> entry : jsonSerializers.entrySet()) {
                    addSerializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (jsonDeserializers != null) {
                for (Map.Entry<Class<?>, ValueDeserializer<?>> entry : jsonDeserializers.entrySet()) {
                    addDeserializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (keySerializers != null) {
                for (Map.Entry<Class<?>, ValueSerializer<?>> entry : keySerializers.entrySet()) {
                    addKeySerializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (keyDeserializers != null) {
                for (Map.Entry<Class<?>, KeyDeserializer> entry : keyDeserializers.entrySet()) {
                    addKeyDeserializer(module, entry.getKey(), entry.getValue());
                }
            }
            jsonMapper.addModule(module);
        }

        for (JsonSubType jsonSubType : jsonSubTypes) {
            jsonSubType.modules()
                    .forEach(jsonMapper::addModule);
        }

        return jsonMapper.build();
    }

    //
    // Yes this code is strange.  The addSerializer and addDeserializer methods arguments have
    // generic types that are dependent on each other, but since our map has no type information, we
    // have no type T for casting the type and serializer.  This is why these methods have generic type
    // T but it is only used for casting
    //
    @SuppressWarnings("unchecked")
    private <T> void addSerializer(SimpleModule module, Class<?> type, ValueSerializer<?> jsonSerializer)
    {
        module.addSerializer((Class<? extends T>) type, (ValueSerializer<T>) jsonSerializer);
    }

    @SuppressWarnings("unchecked")
    public <T> void addDeserializer(SimpleModule module, Class<?> type, ValueDeserializer<?> jsonDeserializer)
    {
        module.addDeserializer((Class<T>) type, (ValueDeserializer<? extends T>) jsonDeserializer);
    }

    @SuppressWarnings("unchecked")
    private <T> void addKeySerializer(SimpleModule module, Class<?> type, ValueSerializer<?> keySerializer)
    {
        module.addKeySerializer((Class<? extends T>) type, (ValueSerializer<T>) keySerializer);
    }

    @SuppressWarnings("unchecked")
    private <T> void addKeyDeserializer(SimpleModule module, Class<?> type, KeyDeserializer keySerializer)
    {
        module.addKeyDeserializer(type, keySerializer);
    }
}
