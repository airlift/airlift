[◀︎ Airlift](../README.md) • [◀︎ Reference](../README.md#reference)

## Reference: Configuration

### Configuration Object and `@Config`

Define classes using the [@Config](../configuration/src/main/java/io/airlift/configuration/Config.java) annotation.
The value of the annotation is the name of a config value. At runtime, Airlift uses the value from
the command line or a properties file (in that order) as the argument to the annotated method. If no 
command line or property is present with the name, the setter is never called and will retain its
default value.

Example:

```java
public class MyConfig
{
    private String address;
    
    @Config("foo.bar.address")
    public MyConfig setAddress(String address)
    {
        this.address = address;
        return this;
    }
}
```

### Configuration Values

Source configuration can be specified on the command line or via property files.
Airlift will map these configuration values to an annotated and bound Java model class.

Configuration files are standard `field=value` property files. Airlift uses
the file defined by system property `config` (i.e. pass on the command line
`-Dconfig=path-to-config-properties-file`).

### Configuration Binding

The main way to bind configuration objects is via the `ConfigBinder` utility:

```java
ConfigBinder.bindConfig(binder).bindConfig(MyConfigObject.class);
```

You can also do conditional binding. See the [note here for details](recipes.md#how-do-i-do-conditional-binding-based-on-a-config-value).

### @ConfigSecuritySensitive

Airlift logs configuration values. However, some values may be too sensitive
to log. Annotate the with `@ConfigSecuritySensitive` to avoid logging.

### ConfigDefaults

The value of a config field in your configuration object will be the default
value if one isn't found in property files, etc. You can also set the default
values via the `ConfigDefaults` binding mechanism:

```java
configBinder(binder).bindConfigDefaults(MyConfig.class, config -> config.setSomeValue("foo"));
```

### @DefunctConfig

You may have the need to permanently prevent a config name being used (it may have lost its meaning, etc.).
Add `@DefunctConfig("name")` to your Config class and if a `@Config` is found with the same name
Airlift will throw an exception.

### @LegacyConfig

If you want to migrate from an old config value to a new one use `@LegacyConfig`. A diagnostic
will be logged informing that the config is being renamed. You can optionally specify what the replacement
is.
