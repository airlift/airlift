package io.airlift.mcp;

import java.util.Optional;

import static com.google.inject.Scopes.SINGLETON;

public class TestMcpWithDatabaseStorage
        extends TestMcp
{
    public TestMcpWithDatabaseStorage()
    {
        super(TestingDatabaseStorage.class, Optional.of(binder -> binder.bind(TestingDatabaseServer.class).in(SINGLETON)));
    }
}
