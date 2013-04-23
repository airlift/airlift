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
package com.proofpoint.discovery.client;

import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestServiceInventory
{
    @Test
    public void testNullServiceInventory()
            throws Exception
    {
        ServiceInventory serviceInventory = new ServiceInventory(new ServiceInventoryConfig(),
                new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class)
        );

        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
        serviceInventory.updateServiceInventory();
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
    }

    @Test
    public void testFileServiceInventory()
            throws Exception
    {
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory.json").toURI());

        ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class)
        );

        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 2);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);
        serviceInventory.updateServiceInventory();
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 2);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        Assert.assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);
    }
}
