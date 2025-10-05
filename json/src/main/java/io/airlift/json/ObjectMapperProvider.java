package io.airlift.json;

import io.airlift.jackson.BaseJacksonProvider;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
        // write characters outside the BMP as escaped surrogate pairs, as Jackson 2.x did
        super(jsonFactoryBuilder.disable(JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8), JsonMapper::builder);

        // When serialization fails in the middle, it's better to return a truncated (invalid) JSON
        // than something that could be interpreted as a valid (but incorrect) result.
        // This is especially applicable to server endpoints that return JSON responses.
        mapperBuilder().disable(StreamWriteFeature.AUTO_CLOSE_CONTENT);
    }

    @Override
    public ObjectMapper get()
    {
        return create();
    }
}
