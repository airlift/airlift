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
package io.airlift.node;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.airlift.node.NodeConfig.AddressSource;
import org.weakref.jmx.Managed;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class NodeInfo
{
    private final String environment;
    private final String pool;
    private final String nodeId;
    private final String location;
    private final String binarySpec;
    private final String configSpec;
    private final String instanceId = UUID.randomUUID().toString();
    private final String internalAddress;
    private final String externalAddress;
    private final InetAddress bindIp;
    private final long startTime = System.currentTimeMillis();

    public NodeInfo(String environment)
    {
        this(new NodeConfig().setEnvironment(environment));
    }

    @Inject
    public NodeInfo(NodeConfig config)
    {
        this(config.getEnvironment(),
                config.getPool(),
                config.getNodeId(),
                config.getNodeInternalAddress(),
                config.getNodeBindIp(),
                config.getNodeExternalAddress(),
                config.getLocation(),
                config.getBinarySpec(),
                config.getConfigSpec(),
                config.getInternalAddressSource()
        );
    }

    public NodeInfo(String environment,
            String pool,
            String nodeId,
            String internalAddress,
            InetAddress bindIp,
            String externalAddress,
            String location,
            String binarySpec,
            String configSpec,
            AddressSource internalAddressSource)
    {
        checkNotNull(environment, "environment is null");
        checkNotNull(pool, "pool is null");
        checkNotNull(internalAddressSource, "internalAddressSource is null");
        checkArgument(environment.matches(NodeConfig.ENV_REGEXP), "environment '%s' is invalid", environment);
        checkArgument(pool.matches(NodeConfig.POOL_REGEXP), "pool '%s' is invalid", pool);

        this.environment = environment;
        this.pool = pool;

        if (nodeId != null) {
            checkArgument(nodeId.matches(NodeConfig.ID_REGEXP), "nodeId '%s' is invalid", nodeId);
            this.nodeId = nodeId;
        }
        else {
            this.nodeId = UUID.randomUUID().toString();
        }

        if (location != null) {
            this.location = location;
        }
        else {
            this.location = "/" + this.nodeId;
        }

        this.binarySpec = binarySpec;
        this.configSpec = configSpec;

        if (internalAddress != null) {
            this.internalAddress = internalAddress;
        }
        else {
            this.internalAddress = findInternalAddress(internalAddressSource);
        }

        if (bindIp != null) {
            this.bindIp = bindIp;
        }
        else {
            this.bindIp = InetAddresses.fromInteger(0);
        }

        if (externalAddress != null) {
            this.externalAddress = externalAddress;
        }
        else {
            this.externalAddress = this.internalAddress;
        }
    }

    /**
     * The environment in which this server is running.
     */
    @Managed
    public String getEnvironment()
    {
        return environment;
    }

    /**
     * The pool of which this server is a member.
     */
    @Managed
    public String getPool()
    {
        return pool;
    }

    /**
     * The unique id of the deployment slot in which this binary is running.  This id should
     * represents the physical deployment location and should not change.
     */
    @Managed
    public String getNodeId()
    {
        return nodeId;
    }

    /**
     * Location of this JavaVM.
     */
    @Managed
    public String getLocation()
    {
        return location;
    }

    /**
     * Binary this JavaVM is running.
     */
    @Managed
    public String getBinarySpec()
    {
        return binarySpec;
    }

    /**
     * Configuration this JavaVM is running.
     */
    @Managed
    public String getConfigSpec()
    {
        return configSpec;
    }

    /**
     * The unique id of this JavaVM instance.  This id will change every time the vm is restarted.
     */
    @Managed
    public String getInstanceId()
    {
        return instanceId;
    }

    /**
     * The internal network address the server should use when announcing its location to other machines.
     * This address should available to all machines within the environment, but may not be globally routable.
     * If this is not set, the following algorithm is used to choose the public address:
     *
     * <ol>
     * <li>InetAddress.getLocalHost() if good IPv4</li>
     * <li>First good IPv4 address of an up network interface</li>
     * <li>First good IPv6 address of an up network interface</li>
     * <li>InetAddress.getLocalHost()</li>
     * </ol>
     * An address is considered good if it is not a loopback address, a multicast address, or an any-local-address address.
     */
    @Managed
    public String getInternalAddress()
    {
        return internalAddress;
    }

    /**
     * The address to use when contacting this server from an external network.  If possible, ip address should be globally
     * routable.  The address is returned as a string because the name may not be resolvable from the local machine.
     * <p>
     * If this is not set, the internal address is used.
     */
    @Managed
    public String getExternalAddress()
    {
        return externalAddress;
    }

    /**
     * The IP address the server should use when binding a server socket.
     *
     * If this is not set, this will be the IPv4 any local address (e.g., 0.0.0.0).
     */
    @Managed
    public InetAddress getBindIp()
    {
        return bindIp;
    }

    /**
     * The time this server was started.
     */
    @Managed
    public long getStartTime()
    {
        return startTime;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("nodeId", nodeId)
                .add("instanceId", instanceId)
                .add("internalAddress", internalAddress)
                .add("externalAddress", externalAddress)
                .add("bindIp", bindIp)
                .add("startTime", startTime)
                .toString();
    }

    private static String findInternalAddress(AddressSource addressSource)
    {
        switch (addressSource) {
            case IP:
                return InetAddresses.toAddrString(findInternalIp());
            case HOSTNAME:
                return getLocalHost().getHostName();
            case FQDN:
                return getLocalHost().getCanonicalHostName();
            default:
                throw new IllegalArgumentException();
        }
    }

    private static InetAddress findInternalIp()
    {
        // Check if local host address is a good v4 address
        InetAddress localAddress = null;
        try {
            localAddress = InetAddress.getLocalHost();
            if (isV4Address(localAddress) && getGoodAddresses().contains(localAddress)) {
                return localAddress;
            }
        }
        catch (UnknownHostException ignored) {
        }
        if (localAddress == null) {
            try {
                localAddress = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
            }
            catch (UnknownHostException e) {
                throw new AssertionError("Could not get local ip address");
            }
        }

        // check all up network interfaces for a good v4 address
        for (InetAddress address : getGoodAddresses()) {
            if (isV4Address(address)) {
                return address;
            }
        }

        // check all up network interfaces for a good v6 address
        for (InetAddress address : getGoodAddresses()) {
            if (isV6Address(address)) {
                return address;
            }
        }

        // just return the local host address
        // it is most likely that this is a disconnected developer machine
        return localAddress;
    }

    private static List<InetAddress> getGoodAddresses()
    {
        ImmutableList.Builder<InetAddress> list = ImmutableList.builder();
        for (NetworkInterface networkInterface : getGoodNetworkInterfaces()) {
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (isGoodAddress(address)) {
                    list.add(address);
                }
            }
        }
        return list.build();
    }

    private static List<NetworkInterface> getGoodNetworkInterfaces()
    {
        ImmutableList.Builder<NetworkInterface> builder = ImmutableList.builder();
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                try {
                    if (!networkInterface.isLoopback() && networkInterface.isUp()) {
                        builder.add(networkInterface);
                    }
                }
                catch (Exception ignored) {
                }
            }
        }
        catch (SocketException ignored) {
        }
        return builder.build();
    }

    private static boolean isV4Address(InetAddress address)
    {
        return address instanceof Inet4Address;
    }

    private static boolean isV6Address(InetAddress address)
    {
        return address instanceof Inet6Address;
    }

    private static boolean isGoodAddress(InetAddress address)
    {
        return !address.isAnyLocalAddress() &&
                !address.isLinkLocalAddress() &&
                !address.isLoopbackAddress() &&
                !address.isMulticastAddress();
    }

    private static InetAddress getLocalHost()
    {
        try {
            return InetAddress.getLocalHost();
        }
        catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }
}
