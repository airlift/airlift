[◀︎ Airlift](../README.md)

## Overview

Airlift is a toolkit and not a framework. Frameworks such as Spring are very heavy-weight and will control the future of your project. Toolkits, on the other hand, are small and enable your project by providing targeted utility. Think of Airlift as plumbing for your service. Airlift helps you by eliminating the drudgery of piecing together the basics while allowing you to focus on the functionality of your service.

### Open Source

Airlift incorporates the following standard open source libraries:

| Library | Domain | URL |
| ------- | ------ | --- |
| Jetty | Industry standard HTTP server and client | [https://www.eclipse.org/jetty/](https://www.eclipse.org/jetty/) |
| JAX-RS/Jersey | The Java standard for REST servers | [https://eclipse-ee4j.github.io/jersey/](https://eclipse-ee4j.github.io/jersey/) |
| Jackson | Industry standard JSON serialization | [https://github.com/FasterXML/jackson](https://github.com/FasterXML/jackson) |
| Guava | Swiss army knife for Java | [https://github.com/google/guava](https://github.com/google/guava) |
| Guice | The best dependency injection framework for Java | [https://github.com/google/guice](https://github.com/google/guice) |
| jmxutils | Simple library for exposing JMX endpoints | [https://github.com/martint/jmxutils](https://github.com/martint/jmxutils) |

### Configuration

Airlift incorporates a property-file-to-object mapping system that is easy to use and easy to understand.

- Configs have default values and descriptions, which are printed when the server starts.
- Supports legacy configs for graceful transitions and deprecated configs for hard removals (with a good error message for end users).
- Unused config properties are an error, rather than it being silently ignored. This prevents the situation where an administrator thinks they set an important property, but they had a typo in the name.

### Build Tools

Airlift uses [Apache Maven](https://maven.apache.org) the industry standard build tool. Airlift incorporates a suite of Maven plugins that will improve your development experience and prevent common security holes and runtime incompatibilities. Further, Airlift pre-defines a BOM (Bill of Materials) as well as dependency management specifications for dozens of standard libraries that you might want to use.

### Packaging

Packaging Java executables is a notoriously difficult task. Airlift has a very simple system for packaging your service as a Tarball with a simple launcher. This package is designed to be easily incorporated with Docker containers.
