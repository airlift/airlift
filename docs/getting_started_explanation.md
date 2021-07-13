[◀︎ Airlift](../README.md) • [◀︎ Getting Started](getting_started.md)

## Getting Started Detailed Explanation

This following is a detailed explanation of the [Getting Started](getting_started.md) example.

### Maven POM File

In Step 1 you create a simple Maven POM file. If you are new to Apache Maven
please see the [Maven Website](https://maven.apache.org) for details.

- Parent POM - every Airlift Service has Airbase as its parent.

- `properties`
  - `dep.airlift.version` - declares the version of airlift in use
  - `air.check.skip-license` - for this example we don't require license headers. But, for production, you should
  - `dep.packaging.version` - ensure that a project uses the same version for airbase while building and packaging
  - `project.build.targetJdk` - the JDK being used
- `dependencyManagement` - declare Airlift's dependency versions via the Maven BOM convention
- `dependencies` - Notice that Airlift's parent has pre-defined versions for all the dependencies you will need to start a service.

### REST Resource File

In Step 2 you create a standard JAX-RS Resource file. Notice that there is nothing
Airlift-specific here. Just standard JAX-RS. If you are not familiar with JAX-RS
see the [Jersey Website](https://eclipse-ee4j.github.io/jersey/) for details.

### Guice Bindings

In Step 3 you "bind" the JAX-RS resource using Guice. Airlift makes this very easy with its `jaxrsBinder`. If you're not familiar with Guice see the [Guice Website](https://github.com/google/guice) for details.

### Main

In Step 4 you create your Main class. This uses Airlift's "Bootstrap" mechanism. It
handles all the scaffolding and housework of initializing Jersey, creates a best-practices HttpServer, etc. These chores are normally a huge headache and it's hard to
get it right. In addition to your `ServiceModule` there are a few required Airlift
modules here.

Once the bootstrap instance is created, the Service is started via:

```
app.initialize();
```
