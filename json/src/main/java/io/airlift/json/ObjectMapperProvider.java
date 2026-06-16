package io.airlift.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import static java.util.Objects.requireNonNull;

/**
 * Use {@link JsonMapperProvider} instead.
 */
@Deprecated
public class ObjectMapperProvider
        extends BaseJacksonProvider<ObjectMapper, JsonMapper.Builder, ObjectMapperProvider>
{
    public ObjectMapperProvider()
    {
        this(new JsonFactoryBuilder());
    }

    public ObjectMapperProvider(JsonFactory jsonFactory)
    {
        this(new JsonFactoryBuilder(requireNonNull(jsonFactory, "jsonFactory is null")));
    }

    private ObjectMapperProvider(JsonFactoryBuilder jsonFactoryBuilder)
    {
        super(jsonFactoryBuilder, JsonMapper::builder);

        // When serialization fails in the middle, it's better to return a truncated (invalid) JSON
        // than something that could be interpreted as a valid (but incorrect) result.
        // This is especially applicable to server endpoints that return JSON responses.
        mapperBuilder().disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT);
    }

    @Override
    public ObjectMapper get()
    {
        return create();
    }
}
