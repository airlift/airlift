package com.proofpoint.jetty;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Server;

/**
 * Provides a fully configured instance of {@link org.eclipse.jetty.server.Server},
 * ready to use with Guice (via {@link com.google.inject.servlet.GuiceFilter})
 *
 * Features:
 *    - HTTP/HTTPS
 *    - Basic Auth
 *    - Request logging
 *    - JMX
 *
 * Configuration options are provided via {@link JettyConfig}
 *
 * To enable JMX, an {@link javax.management.MBeanServer} must be bound elsewhere
 * To enable Basic Auth, a {@link org.eclipse.jetty.security.LoginService} must be bound elsewhere
 *
 * To enable HTTPS, JettyConfig.isHttpsEnabled() must return true and JettyConfig.getKeystorePath() and
 * JettyConfig.getKeystorePassword() must return the path to the keystore containing the ssl cert & the password to the
 * keystore, respectively. The https port is specified via JettyConfig.getHttpsPort()
 */
public class JettyModule
    implements Module
{
    public static final String REALM_NAME = "Proofpoint";

    public void configure(Binder binder)
    {
        binder.bind(Server.class)
                .toProvider(JettyProvider.class)
                .in(Scopes.SINGLETON);

        binder.bind(JettyServer.class).in(Scopes.SINGLETON);

        binder.bind(LoginService.class)
                .toProvider(HashLoginServiceProvider.class);

        ConfigurationModule.bindConfig(binder, JettyConfig.class);
    }
}
