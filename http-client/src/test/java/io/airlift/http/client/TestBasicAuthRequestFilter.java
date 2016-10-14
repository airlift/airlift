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
package io.airlift.http.client;

import org.testng.annotations.Test;

import java.net.URI;
import java.util.function.Predicate;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestBasicAuthRequestFilter
{
    @Test
    public void testBasicAuthentication()
            throws Exception
    {
        Predicate<Request> predicate = request -> request.getUri().getPath().startsWith("/private");
        HttpRequestFilter filter = new BasicAuthRequestFilter(predicate, "Aladdin", "open sesame");

        Request publicResourceRequest = createTestRequest("/public");
        assertNull(filter.filterRequest(publicResourceRequest).getHeader(AUTHORIZATION));

        Request privateResourceRequest = createTestRequest("/private");
        assertEquals(filter.filterRequest(privateResourceRequest).getHeader(AUTHORIZATION), "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
    }

    private static Request createTestRequest(String path)
    {
        return prepareGet()
                .setUri(URI.create("http://example.com" + path))
                .build();
    }
}
