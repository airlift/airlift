package com.proofpoint.http.server;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.log.Logger;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.security.HashLoginService;

import java.io.IOException;

public class HashLoginServiceProvider
        implements Provider<HashLoginService>
{
    private static final Logger log = Logger.get(HashLoginServiceProvider.class);

    private final HttpServerConfig config;

    @Inject
    public HashLoginServiceProvider(HttpServerConfig config)
    {
        this.config = config;
    }

    public HashLoginService get()
    {
        String authConfig = config.getUserAuthFile();
        try {
            if (!StringUtils.isEmpty(authConfig)) {
                HashLoginService service = new HashLoginService(HttpServerModule.REALM_NAME, authConfig);
                service.loadUsers();
                return service;
            }
            return null;
        }
        catch (IOException e) {
            log.error(e, "Error when loading user auth info from %s", authConfig);
        }

        return null;
    }
}
