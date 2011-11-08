package com.proofpoint.node;

import com.google.common.base.Preconditions;
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
    private final InetAddress publicIp;
    private final InetAddress bindIp;
    private final long startTime = System.currentTimeMillis();

    public NodeInfo(String environment)
    {
        this(new NodeConfig().setEnvironment(environment));
    }

    @Inject
    public NodeInfo(NodeConfig config)
    {
        this(config.getEnvironment(), config.getPool(), config.getNodeId(), config.getNodeIp(), config.getLocation(), config.getBinarySpec(), config.getConfigSpec());
    }

    public NodeInfo(String environment, String pool, String nodeId, InetAddress nodeIp, String location, String binarySpec, String configSpec)
    {
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(pool, "pool is null");
        Preconditions.checkArgument(environment.matches(NodeConfig.ENV_REGEXP), String.format("environment '%s' is invalid", environment));
        Preconditions.checkArgument(pool.matches(NodeConfig.POOL_REGEXP), String.format("pool '%s' is invalid", pool));

        this.environment = environment;
        this.pool = pool;

        if (nodeId != null) {
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

        if (nodeIp != null) {
            this.publicIp = nodeIp;
            this.bindIp = nodeIp;
        }
        else {
            this.publicIp = findPublicIp();
            this.bindIp = InetAddresses.fromInteger(0);
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
     * The public ip address the server should use when announcing its location to other machines.
     * If this is not set, the following algorithm is used to choose the public ip:
     * <ol>
     *     <li>InetAddress.getLocalHost() if good IPv4</li>
     *     <li>First good IPv4 address of an up network interface</li>
     *     <li>First good IPv6 address of an up network interface</li>
     *     <li>InetAddress.getLocalHost()</li>
     * </ol>
     * An address is considered good if it is not a loopback address, a multicast address, or an any-local-address address.
     */
    @Managed
    public InetAddress getPublicIp()
    {
        return publicIp;
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
        final StringBuffer sb = new StringBuffer();
        sb.append("NodeInfo");
        sb.append("{nodeId=").append(nodeId);
        sb.append(", instanceId=").append(instanceId);
        sb.append(", publicIp=").append(publicIp);
        sb.append(", bindIp=").append(bindIp);
        sb.append(", startTime=").append(startTime);
        sb.append('}');
        return sb.toString();
    }

    private static InetAddress findPublicIp()
    {
        // Check if local host address is a good v4 address
        InetAddress localAddress;
        try {
            localAddress = InetAddress.getLocalHost();
            if (isGoodV4Address(localAddress)) {
                return localAddress;
            }
        }
        catch (UnknownHostException ignored) {
            throw new AssertionError("Could not get local ip address");
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
        catch (SocketException e) {
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
}
