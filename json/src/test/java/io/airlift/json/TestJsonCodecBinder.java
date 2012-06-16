package io.airlift.json;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import org.testng.annotations.Test;

import javax.inject.Inject;

import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;
import static org.testng.Assert.assertNotNull;

public class TestJsonCodecBinder
{
    @Test
    public void ignoresRepeatedBinding()
    {
        Injector injector = Guice.createInjector(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                jsonCodecBinder(binder).bindJsonCodec(Integer.class);
                jsonCodecBinder(binder).bindJsonCodec(Integer.class);

                binder.bind(Dummy.class).in(Scopes.SINGLETON);
            }
        });

        assertNotNull(injector.getInstance(Dummy.class).getCodec());
    }

    private static class Dummy
    {
        private final JsonCodec<Integer> codec;

        @Inject
        public Dummy(JsonCodec<Integer> codec)
        {
            this.codec = codec;
        }

        public JsonCodec<Integer> getCodec()
        {
            return codec;
        }
    }
}
