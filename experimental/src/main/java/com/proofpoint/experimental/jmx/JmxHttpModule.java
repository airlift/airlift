package com.proofpoint.experimental.jmx;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.deser.StdScalarDeserializer;
import org.codehaus.jackson.map.ser.SerializerBase;
import org.codehaus.jackson.map.ser.ToStringSerializer;
import org.codehaus.jackson.node.ObjectNode;
import sun.management.LazyCompositeData;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.TabularData;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.proofpoint.json.JsonBinder.jsonBinder;

public class JmxHttpModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(MBeanResource.class).in(Scopes.SINGLETON);
        jsonBinder(binder).addSerializerBinding(InetAddress.class).toInstance(ToStringSerializer.instance);
        jsonBinder(binder).addSerializerBinding(ObjectName.class).toInstance(ToStringSerializer.instance);
        jsonBinder(binder).addSerializerBinding(OpenType.class).toInstance(ToStringSerializer.instance);
        jsonBinder(binder).addSerializerBinding(CompositeData.class).to(CompositeDataSerializer.class);
        jsonBinder(binder).addSerializerBinding(TabularData.class).to(TabularDataSerializer.class);
        jsonBinder(binder).addDeserializerBinding(ObjectName.class).to(ObjectNameDeserializer.class);

        // jackson has a bug in the serializer selection code so it does not know that subclasses of LazyCompositeData are also CompositeData
        jsonBinder(binder).addSerializerBinding(LazyCompositeData.class).to(CompositeDataSerializer.class);
    }

    static class ObjectNameDeserializer
            extends StdScalarDeserializer<ObjectName>
    {
        public ObjectNameDeserializer()
        {
            super(ObjectName.class);
        }

        @Override
        public ObjectName deserialize(JsonParser jsonParser, DeserializationContext context)
                throws IOException
        {
            JsonToken token = jsonParser.getCurrentToken();
            if (token == JsonToken.VALUE_STRING) {
                try {
                    return ObjectName.getInstance(jsonParser.getText());
                }
                catch (MalformedObjectNameException e) {
                    throw context.instantiationException(getValueClass(), e);
                }
            }
            throw context.mappingException(getValueClass());
        }
    }

    static class TabularDataSerializer
            extends SerializerBase<TabularData>
    {
        public TabularDataSerializer()
        {
            super(TabularData.class, true);
        }

        @Override
        public void serialize(TabularData data, JsonGenerator jsonGenerator, SerializerProvider provider)
                throws IOException
        {
            jsonGenerator.writeStartArray();

            JsonSerializer<Object> mapSerializer = provider.findValueSerializer(Map.class, null);
            for (Map<String, Object> map : toList(data)) {
                if (!map.isEmpty()) {
                    mapSerializer.serialize(map, jsonGenerator, provider);
                }
            }

            jsonGenerator.writeEndArray();
        }

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint)
                throws JsonMappingException
        {
            // List<Map<String, Object>
            ObjectNode o = createSchemaNode("array", true);
            o.put("items", createSchemaNode("object", true));
            return o;
        }

    }

    static class CompositeDataSerializer
            extends SerializerBase<CompositeData>
    {
        public CompositeDataSerializer()
        {
            super(CompositeData.class, true);
        }

        @Override
        public void serialize(CompositeData data, JsonGenerator jsonGenerator, SerializerProvider provider)
                throws IOException
        {
            Map<String, Object> map = toMap(data);
            if (!map.isEmpty()) {
                jsonGenerator.writeStartObject();
                JsonSerializer<Object> cachedSerializer = null;
                Class<?> cachedType = null;

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String key = entry.getKey();
                    jsonGenerator.writeFieldName(key);

                    Object value = entry.getValue();

                    // get the serializer, but cache to reduce lookups
                    Class<?> valueType = value.getClass();
                    JsonSerializer<Object> serializer;
                    if (valueType == cachedType) {
                        serializer = cachedSerializer;
                    }
                    else {
                        serializer = provider.findValueSerializer(valueType, null);
                        cachedSerializer = serializer;
                        cachedType = valueType;
                    }

                    try {
                        serializer.serialize(value, jsonGenerator, provider);
                    }
                    catch (Exception e) {
                        wrapAndThrow(provider, e, map, key);
                    }
                }
                jsonGenerator.writeEndObject();
            }
            else {
                jsonGenerator.writeString("dain42");
            }
        }

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        {
            return createSchemaNode("object", true);
        }
    }

    private static Map<String, Object> toMap(CompositeData data)
    {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

        // never trust JMX to do the right thing
        Set<String> keySet = data.getCompositeType().keySet();
        if (keySet != null) {
            for (String key : keySet) {
                if (key != null) {
                    Object value = data.get(key);
                    if (value != null) {
                        builder.put(key, value);
                    }
                }
            }
        }
        return builder.build();
    }

    private static List<Map<String, Object>> toList(TabularData data)
    {
        ImmutableList.Builder<Map<String, Object>> builder = ImmutableList.builder();

        // never trust JMX to do the right thing
        Set<List<?>> keySet = (Set<List<?>>) data.keySet();
        if (keySet != null) {
            for (List<?> key : keySet) {
                if (key != null && !key.isEmpty()) {
                    Object[] index = key.toArray(new Object[key.size()]);
                    CompositeData value = data.get(index);
                    if (value != null) {
                        builder.add(toMap(value));
                    }
                }
            }
        }
        return builder.build();
    }
}
