package com.proofpoint.jmx.http.rpc;

import com.proofpoint.configuration.Config;

public class JmxHttpRpcConfig
{
    private String username;
    private String password;

    public String getUsername()
    {
        return username;
    }

    @Config("jmx-http-rpc.username")
    public JmxHttpRpcConfig setUsername(String username)
    {
        this.username = username;
        return this;
    }

    public String getPassword()
    {
        return password;
    }

    @Config("jmx-http-rpc.password")
    public JmxHttpRpcConfig setPassword(String password)
    {
        this.password = password;
        return this;
    }
}
