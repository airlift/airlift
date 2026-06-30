package io.airlift.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.TSFBuilder;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.JsonRecyclerPools;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public abstract class BaseJacksonProvider<
        M extends ObjectMapper,
        B extends MapperBuilder<? extends M, B>,
        U extends BaseJacksonProvider<M, B, U>>
        implements Provider<M>
{
    private final B mapperBuilder;

    private Map<Class<?>, JsonSerializer<?>> keySerializers;
    private Map<Class<?>, KeyDeserializer> keyDeserializers;
    private Map<Class<?>, JsonSerializer<?>> jsonSerializers;
    private Map<Class<?>, JsonDeserializer<?>> jsonDeserializers;

    private final Set<JacksonSubType> jacksonSubTypes = new HashSet<>();

    protected <F extends JsonFactory> BaseJacksonProvider(
            TSFBuilder<F, ?> factoryBuilder,
            Function<F, B> mapperBuilderFactory)
    {
        // Disable the length limit, caller will be responsible for validating the input length
        factoryBuilder.streamReadConstraints(StreamReadConstraints
                .builder()
                .maxStringLength(Integer.MAX_VALUE)
                .maxNestingDepth(Integer.MAX_VALUE)
                .maxNameLength(Integer.MAX_VALUE)
                .maxDocumentLength(Long.MAX_VALUE)
                .build());

        factoryBuilder.streamWriteConstraints(StreamWriteConstraints
                .builder()
                .maxNestingDepth(Integer.MAX_VALUE)
                .build());

        factoryBuilder.enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER);
        factoryBuilder.enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER);
        factoryBuilder.enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER);

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
        factoryBuilder.disable(JsonFactory.Feature.INTERN_FIELD_NAMES);
        factoryBuilder.recyclerPool(JsonRecyclerPools.threadLocalPool());

        mapperBuilder = mapperBuilderFactory.apply(factoryBuilder.build());

        // ignore unknown fields (for backwards compatibility)
        mapperBuilder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // do not allow converting a float to an integer
        mapperBuilder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

        // use ISO dates
        mapperBuilder.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Fail if there are trailing tokens after entity was read and mapped
        mapperBuilder.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

        // Skip fields that are null or absent (Optional) when serializing objects.
        // This only applies to mapped object fields, not containers like Map or List.
        mapperBuilder.defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.ALWAYS));

        // disable auto detection of json properties... all properties must be explicit
        mapperBuilder.disable(MapperFeature.AUTO_DETECT_CREATORS);
        mapperBuilder.disable(MapperFeature.AUTO_DETECT_FIELDS);
        mapperBuilder.disable(MapperFeature.AUTO_DETECT_SETTERS);
        mapperBuilder.disable(MapperFeature.AUTO_DETECT_GETTERS);
        mapperBuilder.disable(MapperFeature.AUTO_DETECT_IS_GETTERS);
        mapperBuilder.disable(MapperFeature.USE_GETTERS_AS_SETTERS);
        mapperBuilder.disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
        mapperBuilder.disable(MapperFeature.INFER_PROPERTY_MUTATORS);
        mapperBuilder.disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS);

        mapperBuilder.addModule(new Jdk8Module());
        mapperBuilder.addModule(new JavaTimeModule());
        mapperBuilder.addModule(new GuavaModule());
        mapperBuilder.addModule(new ParameterNamesModule());
        mapperBuilder.addModule(new RecordAutoDetectModule());
        // Replace reflection-based bean access with runtime-generated accessors (LambdaMetafactory).
        // Property detection is unchanged (still driven by explicit annotations above); only the
        // get/set mechanism is faster, reducing CPU and allocation on serialization/deserialization.
        mapperBuilder.addModule(new BlackbirdModule());

        try {
            getClass().getClassLoader().loadClass("org.joda.time.DateTime");
            mapperBuilder.addModule(new JodaModule());
        }
        catch (ClassNotFoundException ignored) {
        }
    }

    protected final B mapperBuilder()
    {
        return mapperBuilder;
    }

    @Inject(optional = true)
    public void setJsonSerializers(Map<Class<?>, JsonSerializer<?>> jsonSerializers)
    {
        this.jsonSerializers = ImmutableMap.copyOf(jsonSerializers);
    }

    public U withJsonSerializers(Map<Class<?>, JsonSerializer<?>> jsonSerializers)
    {
        setJsonSerializers(jsonSerializers);
        return (U) this;
    }

    @Inject(optional = true)
    public void setJsonDeserializers(Map<Class<?>, JsonDeserializer<?>> jsonDeserializers)
    {
        this.jsonDeserializers = ImmutableMap.copyOf(jsonDeserializers);
    }

    public U withJsonDeserializers(Map<Class<?>, JsonDeserializer<?>> jsonDeserializers)
    {
        setJsonDeserializers(jsonDeserializers);
        return (U) this;
    }

    @Inject(optional = true)
    public void setKeySerializers(@JsonKeySerde Map<Class<?>, JsonSerializer<?>> keySerializers)
    {
        this.keySerializers = keySerializers;
    }

    public U withKeySerializers(@JsonKeySerde Map<Class<?>, JsonSerializer<?>> keySerializers)
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
    public void setModules(Set<com.fasterxml.jackson.databind.Module> modules)
    {
        modules.forEach(mapperBuilder::addModule);
    }

    public U withModules(Set<Module> modules)
    {
        setModules(modules);
        return (U) this;
    }

    @Inject(optional = true)
    public void setJacksonSubTypes(Set<JacksonSubType> jacksonSubTypes)
    {
        this.jacksonSubTypes.addAll(jacksonSubTypes);
    }

    public U withJacksonSubTypes(Set<JacksonSubType> jacksonSubTypes)
    {
        setJacksonSubTypes(jacksonSubTypes);
        return (U) this;
    }

    protected M create()
    {
        if (jsonSerializers != null || jsonDeserializers != null || keySerializers != null || keyDeserializers != null) {
            SimpleModule module = new SimpleModule(getClass().getName(), new Version(1, 0, 0, null, null, null));
            if (jsonSerializers != null) {
                for (Map.Entry<Class<?>, JsonSerializer<?>> entry : jsonSerializers.entrySet()) {
                    addSerializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (jsonDeserializers != null) {
                for (Map.Entry<Class<?>, JsonDeserializer<?>> entry : jsonDeserializers.entrySet()) {
                    addDeserializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (keySerializers != null) {
                for (Map.Entry<Class<?>, JsonSerializer<?>> entry : keySerializers.entrySet()) {
                    addKeySerializer(module, entry.getKey(), entry.getValue());
                }
            }
            if (keyDeserializers != null) {
                for (Map.Entry<Class<?>, KeyDeserializer> entry : keyDeserializers.entrySet()) {
                    module.addKeyDeserializer(entry.getKey(), entry.getValue());
                }
            }
            mapperBuilder.addModule(module);
        }

        for (JacksonSubType jacksonSubType : jacksonSubTypes) {
            jacksonSubType.modules()
                    .forEach(mapperBuilder::addModule);
        }

        return mapperBuilder.build();
    }

    //
    // Yes this code is strange.  The addSerializer and addDeserializer methods arguments have
    // generic types that are dependent on each other, but since our map has no type information, we
    // have no type T for casting the type and serializer.  This is why these methods have generic type
    // T but it is only used for casting
    //
    @SuppressWarnings("unchecked")
    private <T> void addSerializer(SimpleModule module, Class<?> type, JsonSerializer<?> jsonSerializer)
    {
        module.addSerializer((Class<? extends T>) type, (JsonSerializer<T>) jsonSerializer);
    }

    @SuppressWarnings("unchecked")
    public <T> void addDeserializer(SimpleModule module, Class<?> type, JsonDeserializer<?> jsonDeserializer)
    {
        module.addDeserializer((Class<T>) type, (JsonDeserializer<? extends T>) jsonDeserializer);
    }

    @SuppressWarnings("unchecked")
    private <T> void addKeySerializer(SimpleModule module, Class<?> type, JsonSerializer<?> keySerializer)
    {
        module.addKeySerializer((Class<? extends T>) type, (JsonSerializer<T>) keySerializer);
    }
}
