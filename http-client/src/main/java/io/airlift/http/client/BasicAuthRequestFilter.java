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

import java.util.Base64;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static io.airlift.http.client.Request.Builder.fromRequest;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Objects.requireNonNull;

public class BasicAuthRequestFilter
        implements HttpRequestFilter
{
    private final Predicate<Request> requestMatcher;
    private final String authenticationHeader;

    public BasicAuthRequestFilter(String user, String password)
    {
        this(request -> true, user, password);
    }

    public BasicAuthRequestFilter(Predicate<Request> requestMatcher, String user, String password)
    {
        this.requestMatcher = requireNonNull(requestMatcher, "requestMatcher is null");
        this.authenticationHeader = createAuthenticationHeader(user, password);
    }

    private static String createAuthenticationHeader(String user, String password)
    {
        requireNonNull(user, "user is null");
        checkArgument(!user.contains(":"), "Illegal character ':' found in username");
        requireNonNull(password, "password is null");
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(ISO_8859_1));
    }

    @Override
    public Request filterRequest(Request request)
    {
        if (!requestMatcher.test(request)) {
            return request;
        }
        return fromRequest(request)
                .addHeader(AUTHORIZATION, authenticationHeader)
                .build();
    }
}
