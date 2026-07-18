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

import java.net.URI;

public final class RequestDiagnostics
{
    private RequestDiagnostics() {}

    public static URI sanitizeUri(URI uri)
    {
        if (uri == null) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder();
        if (uri.getScheme() != null) {
            sanitized.append(uri.getScheme()).append(':');
        }
        if (uri.getRawAuthority() != null) {
            String authority = uri.getRawAuthority();
            int userInfoEnd = authority.lastIndexOf('@');
            if (userInfoEnd >= 0) {
                authority = authority.substring(userInfoEnd + 1);
            }
            sanitized.append("//").append(authority);
        }
        if (uri.getRawPath() != null) {
            sanitized.append(uri.getRawPath());
        }
        return URI.create(sanitized.toString());
    }
}
