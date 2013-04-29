/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.discovery.client.balance;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.discovery.client.ServiceUnavailableException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;

public class HttpServiceBalancerImpl implements HttpServiceBalancer
{
    private final AtomicReference<Set<URI>> httpUris = new AtomicReference<>((Set<URI>) ImmutableSet.<URI>of());
    private final String description;

    public HttpServiceBalancerImpl(String description)
    {
        this.description = checkNotNull(description, "description is null");
    }

    @Override
    public HttpServiceAttempt createAttempt()
    {
        List<URI> httpUris = new ArrayList<>(this.httpUris.get());
        if (httpUris.isEmpty()) {
            throw new ServiceUnavailableException(description);
        }
        Collections.shuffle(httpUris);
        return new HttpServiceAttemptImpl(httpUris, 0);
    }

    @Beta
    public void updateHttpUris(Set<URI> newHttpUris)
    {
        httpUris.set(ImmutableSet.copyOf(newHttpUris));
    }

    private class HttpServiceAttemptImpl implements HttpServiceAttempt
    {

        private final List<URI> uris;
        private int attempt;

        public HttpServiceAttemptImpl(List<URI> uris, int attempt)
        {
            this.uris = uris;
            this.attempt = attempt;
        }

        @Override
        public URI getUri()
        {
            return uris.get(attempt % uris.size());
        }

        @Override
        public void markGood()
        {
            //todo
        }

        @Override
        public void markBad()
        {
            //todo
        }

        @Override
        public HttpServiceAttempt tryNext()
        {
            return new HttpServiceAttemptImpl(uris, attempt + 1);
        }
    }
}
