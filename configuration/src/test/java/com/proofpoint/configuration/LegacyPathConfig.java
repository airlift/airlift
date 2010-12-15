package com.proofpoint.configuration;

public interface LegacyPathConfig
{
    @Config("a")
    @Default("/one/two")
    public String       getZookeeperString();

    @Config("b")
    @Default("/one/two")
    public String       getDFSPath();

    @Config("c")
    @Default("/one/two")
    public String       getLocalPath();
}
