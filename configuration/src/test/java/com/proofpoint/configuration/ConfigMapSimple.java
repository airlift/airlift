package com.proofpoint.configuration;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.proofpoint.testing.Assertions.assertInstanceOf;

public class ConfigMapSimple
{
    Map<String, String> map = null;

    public Map<String, String> getMap()
    {
        return map;
    }

    @Config("map")
    @ConfigMap
    public void setMap(Map<String, String> map)
    {
        assertInstanceOf(map, ImmutableMap.class);
        this.map = ImmutableMap.copyOf(map);
    }
}
