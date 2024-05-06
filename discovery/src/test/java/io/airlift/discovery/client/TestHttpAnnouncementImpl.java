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
package io.airlift.discovery.client;

import org.testng.annotations.Test;

import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static org.testng.Assert.assertEquals;

public class TestHttpAnnouncementImpl
{
    @HttpAnnouncement(announcementId = "apple")
    private final HttpAnnouncement appleHttpAnnouncement;

    @HttpAnnouncement(announcementId = "banana")
    private final HttpAnnouncement bananaHttpAnnouncement;

    @HttpAnnouncement(announcementId = "quot\"ation-and-\\backslash")
    private final HttpAnnouncement httpAnnouncementWithCharacters;

    public TestHttpAnnouncementImpl()
    {
        try {
            this.appleHttpAnnouncement = getClass().getDeclaredField("appleHttpAnnouncement").getAnnotation(HttpAnnouncement.class);
            this.bananaHttpAnnouncement = getClass().getDeclaredField("bananaHttpAnnouncement").getAnnotation(HttpAnnouncement.class);
            this.httpAnnouncementWithCharacters = getClass().getDeclaredField("httpAnnouncementWithCharacters").getAnnotation(HttpAnnouncement.class);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testAnnouncementId()
    {
        assertEquals(new HttpAnnouncementImpl("type A").announcementId(), "type A");
    }

    @Test
    public void testAnnotationType()
    {
        assertEquals(new HttpAnnouncementImpl("apple").annotationType(), HttpAnnouncement.class);
        assertEquals(new HttpAnnouncementImpl("apple").annotationType(), appleHttpAnnouncement.annotationType());
    }

    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(appleHttpAnnouncement, new HttpAnnouncementImpl("apple"))
                .addEquivalentGroup(bananaHttpAnnouncement, new HttpAnnouncementImpl("banana"))
                .check();
    }
}
