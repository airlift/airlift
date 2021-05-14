[◀︎ Airlift](../README.md) • [◀︎ Reference](../README.md#reference)

## Reference: Lifecycle/Bootstrapping

### @PostConstruct and @PreDestroy

Airlift enables the standard [@PostConstruct](https://docs.oracle.com/javase/8/docs/api/javax/annotation/PostConstruct.html) and [@PreDestroy](https://docs.oracle.com/javase/8/docs/api/javax/annotation/PreDestroy.html) annotations for objects injected/bound
via Airlift's Bootstrap. 

### Lifecycle Manager

For additional lifecycle management you can inject the [LifeCycleManager](../bootstrap/src/main/java/io/airlift/bootstrap/LifeCycleManager.java) instance.

### Bootstrapping

The [Bootstrap](../bootstrap/src/main/java/io/airlift/bootstrap/Bootstrap.java) class is used
to initialize and start Airlift applications. See the [example](getting_started.md) for an example
usage of the Bootstrap class.
