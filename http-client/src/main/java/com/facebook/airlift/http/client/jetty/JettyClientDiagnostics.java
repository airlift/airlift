/*
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
package com.facebook.airlift.http.client.jetty;

import com.google.common.util.concurrent.RateLimiter;
import io.airlift.log.Logger;
import org.eclipse.jetty.client.HttpClient;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
class JettyClientDiagnostics
{
    private static final Logger log = Logger.get(JettyClientDiagnostics.class);

    // log at most once per 10s
    private final RateLimiter rateLimiter = RateLimiter.create(0.1);

    void logDiagnosticsInfo(HttpClient httpClient)
    {
        if (!log.isDebugEnabled() || !rateLimiter.tryAcquire()) {
            return;
        }

        log.debug(httpClient.dump());
    }
}
