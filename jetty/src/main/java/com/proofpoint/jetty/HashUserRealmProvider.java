package com.proofpoint.jetty;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.proofpoint.log.Logger;
import org.apache.commons.lang.StringUtils;
import org.mortbay.jetty.security.HashUserRealm;

import java.io.IOException;

public class HashUserRealmProvider
        implements Provider<HashUserRealm>
{
    private static final Logger log = Logger.get(HashUserRealmProvider.class);

    private final JettyConfig config;

    @Inject
    public HashUserRealmProvider(JettyConfig config)
    {
        this.config = config;
    }

    public HashUserRealm get()
    {
        String realmConfig = config.getUserAuthPath();
        try {
            if (!StringUtils.isEmpty(realmConfig)) {
                return new HashUserRealm(JettyModule.REALM_NAME, realmConfig);
            }
            return null;
        }
        catch (IOException e) {
            log.error(e, "Error when loading user realm");
        }

        return null;
    }
}
