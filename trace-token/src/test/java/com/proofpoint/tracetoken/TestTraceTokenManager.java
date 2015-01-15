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
package com.proofpoint.tracetoken;

import org.testng.annotations.Test;

import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerRequestToken;
import static org.testng.Assert.assertEquals;

public class TestTraceTokenManager
{
    @Test
    public void testCreateToken()
    {
        String token = createAndRegisterNewRequestToken();
        assertEquals(getCurrentRequestToken(), token);
    }

    @Test
    public void testRegisterCustomToken()
    {
        registerRequestToken("abc");

        assertEquals(getCurrentRequestToken(), "abc");
    }

    @Test
    public void testOverrideRequestToken()
    {
        String oldToken = createAndRegisterNewRequestToken();

        assertEquals(getCurrentRequestToken(), oldToken);

        registerRequestToken("abc");
        assertEquals(getCurrentRequestToken(), "abc");
    }
}
