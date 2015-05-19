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

import com.proofpoint.discovery.client.announce.Announcer;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

public class TestStopAnnouncingResource
{
    @Test
    public void testStopAnnouncing()
    {
        Announcer announcer = mock(Announcer.class);
        AdminServerCredentialVerifier verifier = mock(AdminServerCredentialVerifier.class);
        StopAnnouncingResource resource = new StopAnnouncingResource(announcer, verifier);

        resource.stopAnnouncing("authHeader");

        verify(verifier).authenticate("authHeader");
        verifyNoMoreInteractions(verifier);
        verify(announcer).destroy();
        verifyNoMoreInteractions(announcer);
    }

    @Test
    public void testFailAuthentication()
    {
        Announcer announcer = mock(Announcer.class);
        AdminServerCredentialVerifier verifier = mock(AdminServerCredentialVerifier.class);
        StopAnnouncingResource resource = new StopAnnouncingResource(announcer, verifier);

        WebApplicationException expectedException = new WebApplicationException();
        doThrow(expectedException).when(verifier).authenticate(anyString());

        try {
            resource.stopAnnouncing("authHeader");
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertSame(e, expectedException);
        }

        verify(verifier).authenticate("authHeader");
        verifyNoMoreInteractions(verifier);
        verifyNoMoreInteractions(announcer);
    }
}
