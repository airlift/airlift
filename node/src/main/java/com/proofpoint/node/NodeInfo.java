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
package com.proofpoint.node;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class NodeInfo
{
    private static final Pattern HOST_EXCEPTION_MESSAGE_PATTERN = Pattern.compile("([-_a-zA-Z0-9]+):.*");

    private final String application;
    private final String environment;
    private final String pool;
    private final String nodeId;
    private final String location;
    private final String instanceId = UUID.randomUUID().toString();
    private final InetAddress internalIp;
    private final String internalHostname;
    private final String externalAddress;
    private final InetAddress bindIp;
    private final long startTime = System.currentTimeMillis();

    public NodeInfo(String environment)
    {
        this("test-application", new NodeConfig().setEnvironment(environment));
    }

    @Inject
    public NodeInfo(@ApplicationName String application, NodeConfig config)
    {
        this(application,
                config.getEnvironment(),
                config.getPool(),
                config.getNodeId(),
                config.getNodeInternalIp(),
                config.getNodeInternalHostname(),
                config.getNodeBindIp(),
                config.getNodeExternalAddress(),
                config.getLocation()
        );
    }

    /**
     * @deprecated Replaced by {@link #NodeInfo(String, String, String, String, InetAddress, String, InetAddress, String, String)}.
     */
    @Deprecated
    public NodeInfo(String application,
            String environment,
            String pool,
            String nodeId,
            InetAddress internalIp,
            String internalHostname,
            InetAddress bindIp,
            String externalAddress,
            String location,
            String binarySpec,
            String configSpec)
    {
        this(application, environment, pool, nodeId, internalIp, internalHostname, bindIp, externalAddress, location);
    }

    public NodeInfo(String application,
            String environment,
            String pool,
            String nodeId,
            InetAddress internalIp,
            String internalHostname,
            InetAddress bindIp,
            String externalAddress,
            String location)
    {
        checkNotNull(application, "application is null");
        checkNotNull(environment, "environment is null");
        checkNotNull(pool, "pool is null");
        checkArgument(environment.matches(NodeConfig.ENV_REGEXP), "environment '%s' is invalid", environment);
        checkArgument(pool.matches(NodeConfig.POOL_REGEXP), "pool '%s' is invalid", pool);

        this.application = application;
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

        if (internalIp != null) {
            this.internalIp = internalIp;
        }
        else {
            this.internalIp = findPublicIp();
        }

        if (internalHostname != null) {
            checkArgument(internalHostname.equals("localhost") || internalHostname.matches(NodeConfig.HOSTNAME_REGEXP), String.format("hostname '%s' is invalid", internalHostname));
            this.internalHostname = internalHostname;
        }
        else {
            this.internalHostname = findPublicHostname();
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
            this.externalAddress = InetAddresses.toAddrString(this.internalIp);
        }
    }

    /**
     * The name of this application server
     */
    @Managed
    public String getApplication()
    {
        return application;
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
     * @deprecated Always null.
     */
    @Deprecated
    public String getBinarySpec()
    {
        return null;
    }

    /**
     * Configuration this JavaVM is running.
     * @deprecated Always null.
     */
    @Deprecated
    public String getConfigSpec()
    {
        return null;
    }

    /**
     * The unique id of this JavaVM instance.  This id will change every time the vm is restarted.
     */
    @Managed
    @Deprecated
    public String getInstanceId()
    {
        return instanceId;
    }

    /**
     * The internal network ip address the server should use when announcing its location to other machines.
     * This ip address should available to all machines within the environment, but may not be globally routable.
     * If this is not set, the following algorithm is used to choose the public ip:
     * <ol>
     * <li>InetAddress.getLocalHost() if good IPv4</li>
     * <li>First good IPv4 address of an up network interface</li>
     * <li>First good IPv6 address of an up network interface</li>
     * <li>InetAddress.getLocalHost()</li>
     * </ol>
     * An address is considered good if it is not a loopback address, a multicast address, or an any-local-address address.
     */
    @Managed
    public InetAddress getInternalIp()
    {
        return internalIp;
    }

    /**
     * The internal network hostname the server should use when announcing its location to other machines.
     * This ip address should available to all machines within the environment, but may not be globally routable.
     * If this is not set, the following algorithm is used to choose the public ip:
     * <ol>
     * <li>InetAddress.getLocalHost().getHostName()</li>
     * <li>The hostname parsed out of the message of the exception thrown above</li>
     * <li>The internal IP</li>
     * </ol>
     */
    @Managed
    public String getInternalHostname()
    {
        return internalHostname;
    }

    /**
     * The address to use when contacting this server from an external network.  If possible, ip address should be globally
     * routable.  The address is returned as a string because the name may not be resolvable from the local machine.
     * <p/>
     * If this is not set, the internal ip is used.
     */
    @Managed
    public String getExternalAddress()
    {
        return externalAddress;
    }

    /**
     * The ip address the server should use when binding a server socket.  When the public ip address
     * is explicitly set, this will be the publicIP, but when the public ip is discovered this will be
     * the IPv4 any local address (e.g., 0.0.0.0).
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
                .add("internalIp", internalIp)
                .add("externalAddress", externalAddress)
                .add("bindIp", bindIp)
                .add("startTime", startTime)
                .toString();
    }

    private static InetAddress findPublicIp()
    {
        // Check if local host address is a good v4 address
        InetAddress localAddress = null;
        try {
            localAddress = InetAddress.getLocalHost();
            if (isGoodV4Address(localAddress)) {
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
        for (NetworkInterface networkInterface : getGoodNetworkInterfaces()) {
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (isGoodV4Address(address)) {
                    return address;
                }
            }
        }
        // check all up network interfaces for a good v6 address
        for (NetworkInterface networkInterface : getGoodNetworkInterfaces()) {
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (isGoodV6Address(address)) {
                    return address;
                }
            }
        }
        // just return the local host address
        // it is most likely that this is a disconnected developer machine
        return localAddress;
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

    private static boolean isGoodV4Address(InetAddress address)
    {
        return address instanceof Inet4Address &&
                !address.isAnyLocalAddress() &&
                !address.isLoopbackAddress() &&
                !address.isMulticastAddress();
    }

    private static boolean isGoodV6Address(InetAddress address)
    {
        return address instanceof Inet6Address &&
                !address.isAnyLocalAddress() &&
                !address.isLoopbackAddress() &&
                !address.isMulticastAddress();
    }

    private String findPublicHostname()
    {
        try {
            return InetAddress.getLocalHost().getHostName().toLowerCase();
        }
        catch (UnknownHostException e) {
            // Java 7u5 and later on MacOS sometimes throws this unless the local hostname is in DNS
            // or hosts file. The exception message is the hostname followed by a colon and an error message.
            final Matcher matcher = HOST_EXCEPTION_MESSAGE_PATTERN.matcher(e.getMessage());
            if (matcher.matches()) {
                return matcher.group(1).toLowerCase();
            }
            return InetAddresses.toUriString(internalIp);
        }
    }
}
