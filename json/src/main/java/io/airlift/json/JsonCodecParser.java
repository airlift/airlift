package io.airlift.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;

public class JsonCodecParser<T>
        implements AutoCloseable
{
    private final ObjectReader objectReader;
    private final JsonParser parser;

    JsonCodecParser(ObjectReader objectReader, JsonParser parser)
    {
        this.objectReader = objectReader;
        this.parser = parser;
    }

    @Override
    public void close()
            throws Exception
    {
        parser.close();
    }

    public boolean isClosed()
    {
        return parser.isClosed();
    }

    public boolean hasCurrentToken()
    {
        return parser.hasCurrentToken();
    }

    public JsonToken currentToken()
    {
        return parser.currentToken();
    }

    public JsonParser skipChildren()
            throws IOException
    {
        return parser.skipChildren();
    }

    public T readValue()
            throws IOException
    {
        return objectReader.readValue(parser);
    }

    public JsonToken nextToken()
            throws IOException
    {
        return parser.nextToken();
    }

    public JsonToken nextValue()
            throws IOException
    {
        return parser.nextValue();
    }

    public String nextFieldName()
            throws IOException
    {
        return parser.nextFieldName();
    }

    public String nextTextValue()
            throws IOException
    {
        return parser.nextTextValue();
    }

    public int nextIntValue(int defaultValue)
            throws IOException
    {
        return parser.nextIntValue(defaultValue);
    }

    public long nextLongValue(long defaultValue)
            throws IOException
    {
        return parser.nextLongValue(defaultValue);
    }

    public Boolean nextBooleanValue()
            throws IOException
    {
        return parser.nextBooleanValue();
    }
}
