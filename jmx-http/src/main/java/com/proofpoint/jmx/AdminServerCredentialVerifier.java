/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.jmx;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.Base64;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

class AdminServerCredentialVerifier
{
    private static final Base64.Decoder decoder = Base64.getDecoder();

    private final String username;
    private final String password;

    @Inject
    AdminServerCredentialVerifier(AdminServerConfig config)
    {
        this.username = requireNonNull(config, "config is null").getUsername();
        this.password = config.getPassword();
    }

    public void authenticate(String authHeader)
    {
        if (username == null || password == null) {
            throw new WebApplicationException(Response.status(FORBIDDEN)
                    .header("Content-Type", "text/plain")
                    .entity("Administrator password not configured")
                    .build());
        }

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            unauthorized();
        }

        String credentials = new String(decoder.decode(authHeader.substring("Basic ".length())));
        int index = credentials.indexOf(':');
        if (index < 0 || !username.equals(credentials.substring(0, index)) || !password.equals(credentials.substring(index + 1))) {
            unauthorized();
        }
    }

    private static void unauthorized()
    {
        throw new WebApplicationException(Response.status(UNAUTHORIZED)
                .header("WWW-Authenticate", "Basic realm=\"Administration port\"")
                .header("Content-Type", "text/plain")
                .entity("Incorrect username or password")
                .build()
        );
    }
}
