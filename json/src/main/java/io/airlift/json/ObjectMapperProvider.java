package io.airlift.json;

import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.databind.ObjectMapper;

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
