[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Quotas

## Background

Public APIs should always enforce quotas to avoid misuse and accidental overuse. Users of a public API
might inadvertently send a large number of create requests or a rogue client might do this maliciously.
Also, customers may have contractual limits on resource consumption that need to be enforced.

## Resource Quotas

Actual quota enforcement is outside the scope of API Builder. API Builder's quota management ensures that
a quota was _applied_ for a resource. All `@ApiCreate` methods _**must**_ consume resources
that have quotas specified and/or specify quotas for the method. During the execution of the create method, if specified 
quotas do not get used or if an unspecified quota is used, it is an error.

## `ApiQuotaController`

To connect your application's quota enforcement with API Builder, use `ApiQuotaController`. It is assumed that there is a "quota key"
that can be used to map a quota to an API Builder resource/method. When a quota is applied your application must call
`ApiQuotaController.recordQuotaUsage()` with the corresponding quota key. This informs API Builder that
the quota should be considered as applied otherwise it is an error. Also, unspecified quotas are an error.

## Usage

Add quota keys to resources used by create methods. E.g.

```java
@ApiResource(name = "something", ... quotas = "MY_QUOTA")
public record MyWidget(...) {}

@ApiCreate(description = "Create a new widget")
public void createWidget(MyWidget widget)
{
    // contoller to create a widget
}
```

You can also specify quotas directly in the `@ApiCreate`/`@ApiCustom` annotation but specifying it
in the resouce is preferred and easier to understand.

Whenever quotas are applied, ensure that `recordQuotaUsage` gets called:

```java
apiQuotaController.recordQuotaUsage("MY_QUOTA");
```

## Caveats

- Service methods that have the `ApiTrait.BETA` trait do not throw quota exceptions but instead merely log warnings
- If a service method throws an exception, i.e. it is not successful, then quota usage enforcement is not done
