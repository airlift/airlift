package io.airlift.mcp;

import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TestingContextResolver
        implements ContextResolver<TestingContext>
{
    @Override
    public TestingContext getContext(Class<?> ignore)
    {
        return new TestingContext();
    }
}
