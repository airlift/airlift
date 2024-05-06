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

import java.lang.annotation.Annotation;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

class HttpAnnouncementImpl
        implements HttpAnnouncement
{
    private final String announcementId;

    public HttpAnnouncementImpl(String announcementId)
    {
        this.announcementId = requireNonNull(announcementId, "announcementId is null");
    }

    public String announcementId()
    {
        return announcementId;
    }

    public String toString()
    {
        return format("@%s(announcementId=\"%s\")", annotationType().getName(), announcementId.replace("\"", "\\\""));
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof HttpAnnouncement that)) {
            return false;
        }
        return announcementId.equals(that.announcementId());
    }

    @Override
    public int hashCode()
    {
        // see Annotation.hashCode()
        int result = 0;
        result += ((127 * "announcementId".hashCode()) ^ announcementId.hashCode());
        return result;
    }

    public Class<? extends Annotation> annotationType()
    {
        return HttpAnnouncement.class;
    }
}
