package io.airlift.configuration;

import java.util.Map;

import static io.airlift.configuration.ValueType.MAP;

public class ConfigMap
{
    Map<String, String> mapOption;

    public Map<String, String> getMapOption()
    {
        return mapOption;
    }

    @Config(value = "mapOption", type = MAP)
    public ConfigMap setMapOption(Map<String, String> mapOption)
    {
        this.mapOption = mapOption;
        return this;
    }

    Map<String, Integer> mapOptionInteger;

    public Map<String, Integer> getMapOptionInteger()
    {
        return mapOptionInteger;
    }

    String mapSingleOption;

    public String getMapSingleOption()
    {
        return mapSingleOption;
    }

    @Config("mapOption.key1")
    public ConfigMap setMapSingleOption(String mapSingleOption)
    {
        this.mapSingleOption = mapSingleOption;
        return this;
    }

    @Config(value = "mapOptionInteger", type = MAP)
    public ConfigMap setMapOptionInteger(Map<String, Integer> mapOptionInteger)
    {
        this.mapOptionInteger = mapOptionInteger;
        return this;
    }

    Map<String, EnumOptions> mapOptionEnum;

    public Map<String, EnumOptions> getMapOptionEnum()
    {
        return mapOptionEnum;
    }

    @Config(value = "mapOptionEnum", type = MAP)
    public ConfigMap setMapOptionEnum(Map<String, EnumOptions> mapOptionEnum)
    {
        this.mapOptionEnum = mapOptionEnum;
        return this;
    }

    enum EnumOptions
    {
        OPTION1,
        OPTION2,
        OPTION3
    }
}
