package io.airlift.mcp.features;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.mcp.legacy.LegacyFeatures;

import static com.google.inject.Scopes.SINGLETON;

public class FeaturesModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(RpcMessageParser.class).in(SINGLETON);
        binder.bind(FeaturesProvider.class).in(SINGLETON);
        binder.bind(LegacyFeatures.class).in(SINGLETON);
    }
}
