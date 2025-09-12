package io.airlift.api;

public abstract class ApiStringId<RESOURCE>
        extends ApiId<RESOURCE, ApiStringId.Wrapper>
{
    @FunctionalInterface
    public interface Wrapper
    {
        String value();
    }

    protected ApiStringId(String id)
    {
        super(id);
    }

    public String value()
    {
        return id;
    }

    @Override
    public Wrapper toInternal()
    {
        return () -> id;
    }
}
