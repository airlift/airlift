package io.airlift.json;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.module.SimpleModule;

import java.util.Map;
import java.util.Map.Entry;

import static org.codehaus.jackson.map.DeserializationConfig.Feature.AUTO_DETECT_SETTERS;
import static org.codehaus.jackson.map.DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.codehaus.jackson.map.SerializationConfig.Feature.AUTO_DETECT_GETTERS;
import static org.codehaus.jackson.map.SerializationConfig.Feature.AUTO_DETECT_IS_GETTERS;
import static org.codehaus.jackson.map.SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS;
import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

public class ObjectMapperProvider implements Provider<ObjectMapper>
{
    private Map<Class<?>, JsonSerializer<?>> jsonSerializers;
    private Map<Class<?>, JsonDeserializer<?>> jsonDeserializers;

    @Inject(optional = true)
    public void setJsonSerializers(Map<Class<?>, JsonSerializer<?>> jsonSerializers)
    {
        this.jsonSerializers = jsonSerializers;
    }

    @Inject(optional = true)
    public void setJsonDeserializers(Map<Class<?>, JsonDeserializer<?>> jsonDeserializers)
    {
        this.jsonDeserializers = jsonDeserializers;
    }

    @Override
    public ObjectMapper get()
    {
        ObjectMapper objectMapper = new ObjectMapper();

        // ignore unknown fields (for backwards compatibility)
        objectMapper.getDeserializationConfig().disable(FAIL_ON_UNKNOWN_PROPERTIES);

        // use ISO dates
        objectMapper.getSerializationConfig().disable(WRITE_DATES_AS_TIMESTAMPS);

        // skip fields that are null instead of writing an explicit json null value
        objectMapper.getSerializationConfig().setSerializationInclusion(NON_NULL);

        // disable auto detection of json properties... all properties must be explicit
        objectMapper.getDeserializationConfig().disable(DeserializationConfig.Feature.AUTO_DETECT_FIELDS);
        objectMapper.getDeserializationConfig().disable(AUTO_DETECT_SETTERS);
        objectMapper.getSerializationConfig().disable(SerializationConfig.Feature.AUTO_DETECT_FIELDS);
        objectMapper.getSerializationConfig().disable(AUTO_DETECT_GETTERS);
        objectMapper.getSerializationConfig().disable(AUTO_DETECT_IS_GETTERS);

        if (jsonSerializers != null || jsonDeserializers != null) {
            SimpleModule module = new SimpleModule(getClass().getName(), new Version(1, 0, 0, null));
            if (jsonSerializers != null) {
                for (Entry<Class<?>, JsonSerializer<?>> entry : jsonSerializers.entrySet()) {
                    addSerializer(module, entry.getKey(), entry.getValue());

                }
            }
            if (jsonDeserializers != null) {
                for (Entry<Class<?>, JsonDeserializer<?>> entry : jsonDeserializers.entrySet()) {
                    addDeserializer(module, entry.getKey(), entry.getValue());
                }
            }
            objectMapper.registerModule(module);
        }

        return objectMapper;
    }

    //
    // Yes this code is strange.  The addSerializer and addDeserializer methods arguments have
    // generic types that are dependent on each other, but since our map has no type information, we
    // have no type T for casting the type and serializer.  This is why these methods have generic type
    // T but it is only used for casting
    //

    private <T> void addSerializer(SimpleModule module, Class<?> type, JsonSerializer<?> jsonSerializer)
    {
        module.addSerializer((Class<? extends T>) type, (JsonSerializer<T>) jsonSerializer);
    }

    public <T> void addDeserializer(SimpleModule module, Class<?> type, JsonDeserializer<?> jsonDeserializer)
    {
        module.addDeserializer((Class<T>) type, (JsonDeserializer<? extends T>) jsonDeserializer);
    }
}
