package io.airlift.jaxrs;

import tools.jackson.core.JacksonException;

public class JsonParsingException
        extends JacksonException
{
    public JsonParsingException(Throwable cause)
    {
        super(cause);
    }
}
