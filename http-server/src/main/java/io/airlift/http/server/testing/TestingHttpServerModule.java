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
import com.google.inject.Key;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.http.server.EnableCaseSensitiveHeaderCache;
import io.airlift.http.server.EnableLegacyUriCompliance;
import io.airlift.http.server.EnableVirtualThreads;
import io.airlift.http.server.HttpServer;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.HttpsConfig;
import io.airlift.http.server.LocalAnnouncementHttpServerInfo;
import jakarta.servlet.Filter;

import java.util.Optional;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConditionalModule.conditionalModule;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import static java.util.Objects.requireNonNull;

public class TestingHttpServerModule
        extends AbstractConfigurationAwareModule
{
    private final int httpPort;
    private final Optional<String> configPrefix;

    public TestingHttpServerModule()
    {
        this(0, Optional.empty());
    }

    public TestingHttpServerModule(int httpPort)
    {
        this(httpPort, Optional.empty());
    }

    public TestingHttpServerModule(String configPrefix)
    {
        this(0, Optional.of(configPrefix));
    }

    public TestingHttpServerModule(int httpPort, String configPrefix)
    {
        this(httpPort, Optional.of(configPrefix));
    }

    private TestingHttpServerModule(int httpPort, Optional<String> configPrefix)
    {
        this.httpPort = httpPort;
        this.configPrefix = requireNonNull(configPrefix, "configPrefix is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        binder.disableCircularProxies();

        configBinder(binder).bindConfig(HttpServerConfig.class, configPrefix.orElse(null));
        configBinder(binder).bindConfigDefaults(HttpServerConfig.class, config -> config.setHttpPort(httpPort));

        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        binder.bind(TestingHttpServer.class).in(Scopes.SINGLETON);
        newOptionalBinder(binder, ClientCertificate.class).setDefault().toInstance(ClientCertificate.NONE);
        binder.bind(HttpServer.class).to(Key.get(TestingHttpServer.class));
        // override with HttpServerBinder.enableVirtualThreads()
        newOptionalBinder(binder, Key.get(Boolean.class, EnableVirtualThreads.class)).setDefault().toInstance(false);
        // override with HttpServerBinder.enableLegacyUriCompliance()
        newOptionalBinder(binder, Key.get(Boolean.class, EnableLegacyUriCompliance.class)).setDefault().toInstance(false);
        newSetBinder(binder, Filter.class);
        newSetBinder(binder, HttpResourceBinding.class);
        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class);
        newOptionalBinder(binder, Key.get(Boolean.class, EnableCaseSensitiveHeaderCache.class)).setDefault().toInstance(false);

        newOptionalBinder(binder, HttpsConfig.class);
        install(conditionalModule(HttpServerConfig.class, configPrefix, HttpServerConfig::isHttpsEnabled, moduleBinder -> {
            configBinder(moduleBinder).bindConfig(HttpsConfig.class, configPrefix.orElse(null));
            configBinder(moduleBinder).bindConfigDefaults(HttpsConfig.class, config -> {
                if (httpPort == 0) {
                    config.setHttpsPort(0);
                }
            });
        }));
    }
}
