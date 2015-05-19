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

import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import java.util.Base64;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestAdminServerCredentialVerifier
{
    private static final AdminServerConfig ADMIN_SERVER_CONFIG = new AdminServerConfig().setUsername("foo").setPassword("bar");

    @Test
    public void testNoPasswordConfigured()
    {
        try {
            new AdminServerCredentialVerifier(new AdminServerConfig())
                    .authenticate(null);
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 403);
        }
    }

    @Test
    public void testNoAuthentication()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG)
                    .authenticate(null);
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testSuccess()
    {
        new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG)
                .authenticate("Basic " + Base64.getEncoder().encodeToString("foo:bar".getBytes()));
    }

    @Test
    public void testBadUsername()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG)
                    .authenticate("Basic " + Base64.getEncoder().encodeToString("bad:bar".getBytes()));
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testBadPassword()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG)
                    .authenticate("Basic " + Base64.getEncoder().encodeToString("foo:bad".getBytes()));
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testWrongScheme()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG)
                    .authenticate("Digest " + Base64.getEncoder().encodeToString("foo:bar".getBytes()));
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testNoPassword()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG)
                    .authenticate("Digest " + Base64.getEncoder().encodeToString("foo".getBytes()));
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }
}
