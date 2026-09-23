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

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;

import java.util.Set;

/**
 * The response codes a handler accepts: either an explicit set of codes, or any successful (2xx) code.
 */
final class SuccessfulResponseCodes
{
    private static final SuccessfulResponseCodes ANY_SUCCESSFUL = new SuccessfulResponseCodes(ImmutableSet.of());

    // empty means any successful code
    private final Set<Integer> codes;

    private SuccessfulResponseCodes(Set<Integer> codes)
    {
        this.codes = codes;
    }

    static SuccessfulResponseCodes anySuccessful()
    {
        return ANY_SUCCESSFUL;
    }

    static SuccessfulResponseCodes of(int firstCode, int... otherCodes)
    {
        return new SuccessfulResponseCodes(ImmutableSet.<Integer>builder().add(firstCode).addAll(Ints.asList(otherCodes)).build());
    }

    boolean contains(int statusCode)
    {
        if (codes.isEmpty()) {
            return statusCode >= 200 && statusCode < 300;
        }
        return codes.contains(statusCode);
    }

    @Override
    public String toString()
    {
        return codes.isEmpty() ? "2xx" : codes.toString();
    }
}
