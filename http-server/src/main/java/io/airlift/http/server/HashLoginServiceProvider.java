/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package io.airlift.http.server;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.airlift.log.Logger;
import org.eclipse.jetty.security.HashLoginService;

import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;

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

    @Override
    public HashLoginService get()
    {
        String authConfig = config.getUserAuthFile();
        try {
            if (!isNullOrEmpty(authConfig)) {
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
