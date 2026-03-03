package io.airlift.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.util.Objects.requireNonNull;

/**
 * Use {@link JsonMapperProvider} instead.
 */
@Deprecated
public class ObjectMapperProvider
        extends BaseJacksonProvider<ObjectMapper, ObjectMapperProvider>
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
        super(jsonFactoryBuilder);
    }

    @Override
    public ObjectMapper get()
    {
        return create();
    }
}
