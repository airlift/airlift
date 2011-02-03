package com.proofpoint.http.server;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Server;

/**
 * Provides a fully configured instance of an http server,
 * ready to use with Guice (via {@link com.google.inject.servlet.GuiceFilter})
 * <p/>
 * Features:
 *    - HTTP/HTTPS
 *    - Basic Auth
 *    - Request logging
 *    - JMX
 *
 * Configuration options are provided via {@link HttpServerConfig}
 * <p/>
 * To enable JMX, an {@link javax.management.MBeanServer} must be bound elsewhere
 * To enable Basic Auth, a {@link org.eclipse.jetty.security.LoginService} must be bound elsewhere
 * <p/>
 * To enable HTTPS, HttpServerConfig.isHttpsEnabled() must return true and HttpServerConfig.getKeystorePath() and
 * HttpServerConfig.getKeystorePassword() must return the path to the keystore containing the ssl cert & the password to the
 * keystore, respectively. The https port is specified via HttpServerConfig.getHttpsPort()
 */
public class HttpServerModule
        implements Module
{
    private final Class<? extends Provider<? extends LoginService>> loginServiceProviderClass;

    public static final String REALM_NAME = "Proofpoint";

    /**
     * Uses a default login service
     */
    public HttpServerModule()
    {
        this(HashLoginServiceProvider.class);
    }

    /**
     * @param loginServiceProviderClass Provider for a login service or null for none
     */
    public HttpServerModule(Class<? extends Provider<? extends LoginService>> loginServiceProviderClass)
    {
        this.loginServiceProviderClass = loginServiceProviderClass;
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(Server.class)
                .toProvider(JettyProvider.class)
                .in(Scopes.SINGLETON);

        binder.bind(JettyServer.class).in(Scopes.SINGLETON);

        if ( loginServiceProviderClass != null ) {
            binder.bind(LoginService.class).toProvider(loginServiceProviderClass);
        }

        ConfigurationModule.bindConfig(binder).to(HttpServerConfig.class);
    }
}
