package io.airlift.jaxrs;

import java.io.IOException;

public class YamlParsingException
        extends IOException
{
    public YamlParsingException(Throwable cause)
    {
        super(cause);
    }
}
