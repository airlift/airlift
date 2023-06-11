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
package io.airlift.http.client;

import com.google.common.collect.Multiset;
import com.google.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;

public class TestingStatusListener
        implements HttpStatusListener
{
    public static final int EXCEPTION_STATUS = 654;

    private final Multiset<Integer> statusCounter;

    @Inject
    public TestingStatusListener(Multiset<Integer> statusCounter)
    {
        this.statusCounter = statusCounter;
    }

    @Override
    public void statusReceived(int statusCode)
    {
        statusCounter.add(statusCode);
        if (statusCode == EXCEPTION_STATUS) {
            throw new UncheckedIOException(new IOException("Fake exception"));
        }
    }
}
