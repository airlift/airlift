[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: Resources

## Resource Definition

> Google Guide Link: https://cloud.google.com/apis/design/resources

A resource is a target of interest: a document, a cluster, a catalog, etc. The Google guide is resource oriented and all APIs should 
concern resources, lists of resources and hierarchies of resources. Resources are externally represented as JSON.

API Builder resources have many restrictions. See [Verification and Enforcement](validate-only.md) for details.

## Internal and External Forms

Public APIs should use unique models, IDs, etc. Do not expose internal implementations in a public API. 

## Resource IDs

All resource IDs must extend `ApiId`. `ApiId` is parameterized with two arguments: `RESOURCE` and `INTERNALID`.
`RESOURCE` is the API Builder resource Java record that this ID refers to. API Builder uses this to correlate between the ID and its resource.
`INTERNALID` is the correlated internal ID.  

Additionally, the class must define a no-arg constructor that provides a default/example value for the ID
that is used to validate serialization.

E.g. here is the definition for ClusterId:

```java
public class ClusterId
        extends ApiId<Cluster, io.myco.internal.id.ClusterId>
{
    public ClusterId()
    {
        super("sample-id");
    }
    
    @JsonCreator
    public ClusterId(String id)
    {
        super(id);
    }

    public ClusterId(io.myco.internal.id.ClusterId internalId)
    {
        super(internalId);
    }
}
```

## Example Resource

```java
@ApiResource(name = "cluster", description = "A Trino cluster")
public record Cluster(
        ApiResourceVersion syncToken,
        @ApiReadOnly @ApiDescription("Cluster ID") ClusterId clusterId,
        @ApiDescription("Cluster name") String name,
        @ApiReadOnly @ApiDescription("Cluster status") ClusterStatus clusterStatus,
        @ApiDescription("Cluster type") ClusterTypeId clustertypeId
) {}
```

## ID Lookups

Resource ID classes can be annotated with `ApiIdSupportsLookup` to indicate that the ID can be passed
in [Methods](docs/methods.md) as either a real ID or a lookup value of the form: `prefix=value`. `prefix` 
is defined by the `ApiIdSupportsLookup` annotation (the default is "name").

When a Resource ID is annotated with `ApiIdSupportsLookup` the application must bind an `ApiIdLookup` handler for that ID. 
When a [method](docs/methods.md) that accepts the ID is called and the value has the prefix format the lookup handler is 
called to lookup/build the ID value.

## Merge/Unwrap Nested/Contained Resources

For most API endpoints there will be an endpoint specifying a new resource that accepts a version of the resource
without an ID or syncToken. Rather than creating two versions of the resource with all the same fields except the ID and
version you can use unwrapping to compose the two versions.

For example:

```java
// Resource without ID and version

@ApiResource(name = "cluster", description = "A Trino cluster")
public record Cluster(
        @ApiDescription("Cluster name") String name,
        @ApiReadOnly @ApiDescription("Cluster status") ClusterStatus clusterStatus,
        @ApiDescription("Cluster type") ClusterTypeId clustertypeId
) {}
```

```java

@ApiResource(name = "cluster", description = "A Trino cluster")
public record ClusterWithIdVersion(
        ApiResourceVersion syncToken,
        @ApiReadOnly @ApiDescription("Cluster ID") ClusterId clusterId,
        @ApiUnwrapped Cluster cluster
) {}
```

`ClusterWithIdVersion` contains the cluster and `@ApiUnwrapped` causes the fields of `Cluster` to be merged/unwrapped into 
`ClusterWithIdVersion` as if those fields were top level in `ClusterWithIdVersion`.

## Polymorphic Resources

Polymorphic (also known as sub-type) resources are supported per the following:

- They must be part of a sealed hierarchy based on a base interface
- The base interface must be annotated with `ApiPolyResource`
