package io.airlift.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TSFBuilder;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.Version;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.datatype.guava.GuavaModule;
import tools.jackson.datatype.joda.JodaModule;
import tools.jackson.module.blackbird.BlackbirdModule;

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

    private Map<Class<?>, ValueSerializer<?>> keySerializers;
    private Map<Class<?>, KeyDeserializer> keyDeserializers;
    private Map<Class<?>, ValueSerializer<?>> jsonSerializers;
    private Map<Class<?>, ValueDeserializer<?>> jsonDeserializers;

    private final Set<JacksonSubType> jacksonSubTypes = new HashSet<>();

    protected <F extends TokenStreamFactory> BaseJacksonProvider(
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
         * Disabling INTERN_PROPERTY_NAMES eliminates this contention with minimal performance
         * impact - field name deduplication becomes slightly less memory-efficient, but the
         * elimination of lock contention far outweighs this cost in high-concurrency scenarios.
         *
         * See: https://github.com/FasterXML/jackson-core/issues/332.
         */
        factoryBuilder.disable(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES);
        factoryBuilder.recyclerPool(JsonRecyclerPools.threadLocalPool());

        mapperBuilder = mapperBuilderFactory.apply(factoryBuilder.build());

        // Start from the Jackson 2.x defaults that changed in 3.0, then apply airlift's settings below
        mapperBuilder.configureForJackson2();

        // ignore unknown fields (for backwards compatibility)
        mapperBuilder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // do not allow converting a float to an integer
        mapperBuilder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

        // use ISO dates
        mapperBuilder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Fail if there are trailing tokens after entity was read and mapped
        mapperBuilder.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

        // detect constructor parameter names (Jackson 2.x airlift registered ParameterNamesModule)
        mapperBuilder.enable(MapperFeature.DETECT_PARAMETER_NAMES);

        // Jackson 2.x defaults that configureForJackson2() does not cover
        // keep declaration order of properties
        mapperBuilder.disable(MapperFeature.SORT_PROPERTIES_BY_INDEX);
        // include properties without @JsonView in every view
        mapperBuilder.enable(MapperFeature.DEFAULT_VIEW_INCLUSION);

        // Skip fields that are null or absent (Optional) when serializing objects.
        // This only applies to mapped object fields, not containers like Map or List.
        mapperBuilder.changeDefaultPropertyInclusion(_ -> JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.ALWAYS));

        // disable auto detection of json properties... all properties must be explicit
        mapperBuilder.changeDefaultVisibility(visibilityChecker -> visibilityChecker
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
                .withScalarConstructorVisibility(JsonAutoDetect.Visibility.NONE)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE));
        mapperBuilder.disable(MapperFeature.USE_GETTERS_AS_SETTERS);
        mapperBuilder.disable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS);
        mapperBuilder.disable(MapperFeature.INFER_PROPERTY_MUTATORS);
        mapperBuilder.disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS);

        mapperBuilder.addModule(new GuavaModule());
        // Jackson 3.x always writes java.time.Month and java.time.Year as numbers; 2.x wrote them as strings
        mapperBuilder.addModule(new LegacyJavaTimeModule());
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
    public void setKeySerializers(@JacksonKeySerde Map<Class<?>, ValueSerializer<?>> keySerializers)
    {
        this.keySerializers = ImmutableMap.copyOf(keySerializers);
    }

    public U withKeySerializers(@JacksonKeySerde Map<Class<?>, ValueSerializer<?>> keySerializers)
    {
        setKeySerializers(keySerializers);
        return (U) this;
    }

    @Inject(optional = true)
    public void setKeyDeserializers(@JacksonKeySerde Map<Class<?>, KeyDeserializer> keyDeserializers)
    {
        this.keyDeserializers = ImmutableMap.copyOf(keyDeserializers);
    }

    public U withKeyDeserializers(@JacksonKeySerde Map<Class<?>, KeyDeserializer> keyDeserializers)
    {
        setKeyDeserializers(keyDeserializers);
        return (U) this;
    }

    @Inject(optional = true)
    public void setModules(Set<JacksonModule> modules)
    {
        modules.forEach(mapperBuilder::addModule);
    }

    public U withModules(Set<JacksonModule> modules)
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
