package com.proofpoint.json;

import com.google.inject.Provider;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import static org.codehaus.jackson.map.DeserializationConfig.Feature.AUTO_DETECT_SETTERS;
import static org.codehaus.jackson.map.DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.codehaus.jackson.map.SerializationConfig.Feature.AUTO_DETECT_GETTERS;
import static org.codehaus.jackson.map.SerializationConfig.Feature.AUTO_DETECT_IS_GETTERS;
import static org.codehaus.jackson.map.SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS;
import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

public class ObjectMapperProvider implements Provider<ObjectMapper>
{
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

        return objectMapper;
    }
}
