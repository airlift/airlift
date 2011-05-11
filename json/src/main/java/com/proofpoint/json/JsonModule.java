package com.proofpoint.json;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.json.JsonCodecFactory;
import com.proofpoint.json.ObjectMapperProvider;
import org.codehaus.jackson.map.ObjectMapper;

public class JsonModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        // NOTE: this MUST NOT be a singleton because ObjectMappers are mutable.  This means
        // one component could reconfigure the mapper and break all other components
        binder.bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);

        binder.bind(JsonCodecFactory.class).in(Scopes.SINGLETON);
    }
}
