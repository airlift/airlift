package com.proofpoint.configuration;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.proofpoint.testing.Assertions.assertInstanceOf;

public class ConfigMapComplex
{
    Map<Integer, Config1> map = null;

    public Map<Integer, Config1> getMap()
    {
        return map;
    }

    @Config("map")
    public void setMap(Map<Integer, Config1> map)
    {
        assertInstanceOf(map, ImmutableMap.class);
        this.map = ImmutableMap.copyOf(map);
    }
}
