[◀︎ Airlift](../README.md)

## Recipes

### How Do I Do Conditional Binding Based On a Config Value

_Option 1_

```java
public class MyModule
        extends AbstractConfigurationAwareModule
{
    protected void setup(Binder binder)
    {
        MyConfig config = buildConfigObject(MyConfig.class);    // NOTE: this method also binds the MyConfig
        if (config.isFooEnabled()) {
            binder.binder(Foo.class).to(MyFoo.class);
        }
    }
}
```

_Option 2_

```java
public class MyModule
        extends AbstractConfigurationAwareModule
{
    protected void setup(Binder binder)
    {
        // NOTE: this method also binds the MyConfig
        install(installModuleIf(MyConfig.class, MyConfig::isFooEnabled, new MyConditionalModule()));
    }
}
```

IMPORTANT: When using `AbstractConfigurationAwareModule` to install additional modules you must use
the overloaded `install()` method making sure not to use `binder.install()` when installing
a module that itself extends `ConfigurationAwareModule` or `AbstractConfigurationAwareModule`.

### How Do I Serve Static HTML Files

Use `httpServerBinder`. E.g.

```java
public class MyModule
        implements Module
{
    public void configure(Binder binder)
    {
        // ...
        httpServerBinder(binder).bindResource("/url-path", "classpath").withWelcomeFile("default-file.html");   // NOTE: "welcome file" is optional
    }
}
```

### How Do I Package My Service

Airlift supports a simple packaging mechanism. Put a file named `.build-airlift` in the root
directory of your service. A Maven profile will be triggered that packages your
service as a Unix tarball along with a Python launcher. You can easily
incorporate this into a Docker Container.

See the [Launcher README](../launcher/README.md) for more details.
