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
package com.proofpoint.discovery.client.announce;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;

import java.net.URI;

public class StaticAnnouncementHttpServerInfoImpl implements AnnouncementHttpServerInfo
{
    private final URI httpUri;
    private final URI httpExternalUri;

    private final URI httpsUri;

    public StaticAnnouncementHttpServerInfoImpl(URI httpUri, URI httpExternalUri, URI httpsUri)
    {
        Preconditions.checkArgument(
                (httpUri == null && httpExternalUri == null) ||
                (httpUri != null && httpExternalUri != null),
                "httpUri and httpExternalUri must both be null or both non-null");

        this.httpUri = httpUri;
        this.httpExternalUri = httpExternalUri;
        this.httpsUri = httpsUri;
    }

    @Override
    public URI getHttpUri()
    {
        return httpUri;
    }

    @Override
    public URI getHttpExternalUri()
    {
        return httpExternalUri;
    }

    @Override
    public URI getHttpsUri()
    {
        return httpsUri;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("httpUri", httpUri)
                .add("httpExternalUri", httpExternalUri)
                .add("httpsUri", httpsUri)
                .toString();
    }
}
