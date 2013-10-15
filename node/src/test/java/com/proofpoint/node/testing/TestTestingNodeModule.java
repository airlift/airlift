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
package com.proofpoint.node.testing;

import com.google.common.base.Optional;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.Test;

import static com.proofpoint.testing.Assertions.assertGreaterThanOrEqual;
import static com.proofpoint.testing.Assertions.assertNotEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestTestingNodeModule
{
    @Test
    public void testTestingNode()
    {
        long testStartTime = System.currentTimeMillis();

        Injector injector = Guice.createInjector(new TestingNodeModule(), new ApplicationNameModule("test-application"));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertNotNull(nodeInfo);
        assertEquals(nodeInfo.getApplication(), "test-application");
        assertTrue(nodeInfo.getEnvironment().matches("test\\d+"));
        assertEquals(nodeInfo.getPool(), "general");
        assertNotNull(nodeInfo.getNodeId());
        assertNotNull(nodeInfo.getLocation());
        assertNull(nodeInfo.getBinarySpec());
        assertNull(nodeInfo.getConfigSpec());
        assertNotNull(nodeInfo.getInstanceId());

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        assertNotNull(nodeInfo.toString());
    }

    @Test
    public void testTestingNodeExplicitEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule("foo"), new ApplicationNameModule("test-application"));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertNotNull(nodeInfo);
        assertEquals(nodeInfo.getEnvironment(), "foo");
    }

    @Test
    public void testTestingNodePresentEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule(Optional.of("foo")), new ApplicationNameModule("test-application"));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertNotNull(nodeInfo);
        assertEquals(nodeInfo.getEnvironment(), "foo");
    }

    @Test
    public void testTestingNodeAbsentEnvironment()
    {
        Injector injector = Guice.createInjector(new TestingNodeModule(Optional.<String>absent()), new ApplicationNameModule("test-application"));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);

        assertNotNull(nodeInfo);
        assertTrue(nodeInfo.getEnvironment().matches("test\\d+"));
    }
}
