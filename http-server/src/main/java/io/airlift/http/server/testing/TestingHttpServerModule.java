/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.server.testing;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.http.server.EnableHttp2;
import io.airlift.http.server.EnableLegacyUriCompliance;
import io.airlift.http.server.EnableVirtualThreads;
import io.airlift.http.server.HttpServer;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerFeatures;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.HttpsConfig;
import io.airlift.http.server.LocalAnnouncementHttpServerInfo;
import io.airlift.http.server.TheServlet;
import jakarta.servlet.Filter;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConditionalModule.conditionalModule;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.http.server.HttpServerBinder.HttpResourceBinding;

public class TestingHttpServerModule
        extends AbstractConfigurationAwareModule
{
    private final int httpPort;

    public TestingHttpServerModule()
    {
        this(0);
    }

    public TestingHttpServerModule(int httpPort)
    {
        this.httpPort = httpPort;
    }

    @Override
    protected void setup(Binder binder)
    {
        binder.disableCircularProxies();

        configBinder(binder).bindConfig(HttpServerConfig.class);
        configBinder(binder).bindConfigDefaults(HttpServerConfig.class, config -> config.setHttpPort(httpPort));

        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        binder.bind(TestingHttpServer.class).in(Scopes.SINGLETON);
        newOptionalBinder(binder, ClientCertificate.class).setDefault().toInstance(ClientCertificate.NONE);
        binder.bind(HttpServer.class).to(Key.get(TestingHttpServer.class));
        // override with HttpServerBinder.enableVirtualThreads()
        newOptionalBinder(binder, Key.get(Boolean.class, EnableVirtualThreads.class)).setDefault().toInstance(false);
        // override with HttpServerBinder.enableLegacyUriCompliance()
        newOptionalBinder(binder, Key.get(Boolean.class, EnableLegacyUriCompliance.class)).setDefault().toInstance(false);
        // override with HttpServerBinder.disableHttp2()
        newOptionalBinder(binder, Key.get(Boolean.class, EnableHttp2.class)).setDefault().toInstance(true);
        newSetBinder(binder, Filter.class, TheServlet.class);
        newSetBinder(binder, HttpResourceBinding.class, TheServlet.class);
        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class);

        newOptionalBinder(binder, HttpsConfig.class);
        install(conditionalModule(HttpServerConfig.class, HttpServerConfig::isHttpsEnabled, moduleBinder -> {
            configBinder(moduleBinder).bindConfig(HttpsConfig.class);
            configBinder(moduleBinder).bindConfigDefaults(HttpsConfig.class, config -> {
                if (httpPort == 0) {
                    config.setHttpsPort(0);
                }
            });
        }));
    }

    @Provides
    @Inject
    public HttpServerFeatures serverFeatures(
            @EnableVirtualThreads boolean virtualThreads,
            @EnableLegacyUriCompliance boolean uriCompliance,
            @EnableHttp2 boolean http2)
    {
        return HttpServerFeatures.builder()
                .withVirtualThreads(virtualThreads)
                .withLegacyUriCompliance(uriCompliance)
                .withHttp2(http2)
                .build();
    }
}
