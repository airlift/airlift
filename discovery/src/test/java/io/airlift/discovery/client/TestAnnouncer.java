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
package io.airlift.discovery.client;

import static io.airlift.concurrent.MoreFutures.getFutureValue;
import static io.airlift.discovery.client.ServiceTypes.serviceType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.discovery.client.testing.InMemoryDiscoveryClient;
import io.airlift.node.NodeConfig;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestAnnouncer {
    public static final Duration MAX_AGE = new Duration(1, TimeUnit.MILLISECONDS);
    private final ServiceType serviceType = serviceType("foo");
    private Announcer announcer;
    private InMemoryDiscoveryClient discoveryClient;
    private ServiceAnnouncement serviceAnnouncement;
    private NodeInfo nodeInfo;

    @BeforeEach
    protected void setUp() {
        nodeInfo = new NodeInfo(new NodeConfig().setEnvironment("test").setPool("pool"));
        discoveryClient = new InMemoryDiscoveryClient(nodeInfo, MAX_AGE);
        serviceAnnouncement = ServiceAnnouncement.serviceAnnouncement(serviceType.value())
                .addProperty("a", "apple")
                .build();
        announcer = new Announcer(discoveryClient, ImmutableSet.of(serviceAnnouncement));
    }

    @AfterEach
    public void tearDown() {
        announcer.destroy();
        assertAnnounced();
    }

    @Test
    public void testBasic() {
        assertAnnounced();

        announcer.start();

        assertAnnounced(serviceAnnouncement);
    }

    @Test
    public void startAfterDestroy() {
        announcer.start();
        announcer.destroy();

        try {
            announcer.start();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void idempotentStart() {
        announcer.start();
        announcer.start();
        announcer.start();
    }

    @Test
    public void idempotentDestroy() {
        announcer.start();
        announcer.destroy();
        announcer.destroy();
        announcer.destroy();
    }

    @Test
    public void destroyNoStart() {
        announcer.destroy();
    }

    @Test
    public void addAnnouncementAfterStart() throws Exception {
        assertAnnounced();

        announcer.start();

        ServiceAnnouncement newAnnouncement = ServiceAnnouncement.serviceAnnouncement(serviceType.value())
                .addProperty("a", "apple")
                .build();
        announcer.addServiceAnnouncement(newAnnouncement);

        Thread.sleep(100);
        assertAnnounced(serviceAnnouncement, newAnnouncement);
    }

    @Test
    public void removeAnnouncementAfterStart() throws Exception {
        assertAnnounced();

        announcer.start();

        announcer.removeServiceAnnouncement(serviceAnnouncement.getId());

        Thread.sleep(100);
        assertAnnounced();
    }

    private void assertAnnounced(ServiceAnnouncement... serviceAnnouncements) {
        Future<ServiceDescriptors> future = discoveryClient.getServices(serviceType.value(), "pool");
        ServiceDescriptors serviceDescriptors = getFutureValue(future, DiscoveryException.class);

        assertThat(serviceDescriptors.getType()).isEqualTo(serviceType.value());
        assertThat(serviceDescriptors.getPool()).isEqualTo("pool");
        assertThat(serviceDescriptors.getETag()).isNotNull();
        assertThat(serviceDescriptors.getMaxAge()).isEqualTo(MAX_AGE);

        List<ServiceDescriptor> descriptors = serviceDescriptors.getServiceDescriptors();
        assertThat(descriptors).hasSameSizeAs(serviceAnnouncements);

        ImmutableMap.Builder<UUID, ServiceDescriptor> builder = ImmutableMap.builder();
        for (ServiceDescriptor descriptor : descriptors) {
            builder.put(descriptor.getId(), descriptor);
        }
        Map<UUID, ServiceDescriptor> descriptorMap = builder.build();

        for (ServiceAnnouncement serviceAnnouncement : serviceAnnouncements) {
            ServiceDescriptor serviceDescriptor = descriptorMap.get(serviceAnnouncement.getId());
            assertThat(serviceDescriptor)
                    .as("No descriptor for announcement " + serviceAnnouncement.getId())
                    .isNotNull();
            assertThat(serviceDescriptor.getType()).isEqualTo(serviceType.value());
            assertThat(serviceDescriptor.getPool()).isEqualTo("pool");
            assertThat(serviceDescriptor.getId()).isEqualTo(serviceAnnouncement.getId());
            assertThat(serviceDescriptor.getProperties()).isEqualTo(serviceAnnouncement.getProperties());
            assertThat(serviceDescriptor.getNodeId()).isEqualTo(nodeInfo.getNodeId());
        }
    }
}
