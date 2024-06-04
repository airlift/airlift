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
package io.airlift.http.server.testing;

import io.airlift.http.server.HttpServerFeatures;

public class TestTestingHttpServer
        extends AbstractTestTestingHttpServer
{
    TestTestingHttpServer()
    {
        super(HttpServerFeatures.builder()
                .withHttp2(true) // Enable H2C
                .build());
    }
}
