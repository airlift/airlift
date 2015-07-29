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

import io.airlift.discovery.client.AnnouncementHttpServerInfo;

import javax.inject.Inject;

import java.net.URI;

public class LocalAnnouncementHttpServerInfo implements AnnouncementHttpServerInfo
{
    private final HttpServerInfo httpServerInfo;

    @Inject
    public LocalAnnouncementHttpServerInfo(HttpServerInfo httpServerInfo)
    {
        this.httpServerInfo = httpServerInfo;
    }

    @Override
    public URI getHttpUri()
    {
        return httpServerInfo.getHttpUri();
    }

    @Override
    public URI getHttpExternalUri()
    {
        return httpServerInfo.getHttpExternalUri();
    }

    @Override
    public URI getHttpsUri()
    {
        return httpServerInfo.getHttpsUri();
    }

    @Override
    public URI getHttpsExternalUri()
    {
        return httpServerInfo.getHttpsExternalUri();
    }
}
