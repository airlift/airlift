# Airlift
[![Maven Central](https://img.shields.io/maven-central/v/io.airlift/airlift.svg?label=Maven%20Central)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.airlift%22)
[![Build Status](https://github.com/airlift/airlift/workflows/CI/badge.svg)](https://github.com/airlift/airlift/actions?query=workflow%3ACI+event%3Apush+branch%3Amaster)

Airlift is a toolkit for building REST services in Java.

This project is used as the foundation for distributed systems like [Trino (formerly PrestoSQL)](https://trino.io).

Airlift pulls together stable, mature libraries from the Java ecosystem into a simple, light-weight package that lets you focus on getting things done and includes built-in support for configuration, metrics, logging, dependency injection, and much more, enabling you and your team to ship a production-quality web service in the shortest time possible.

Airlift takes the best-of-breed libraries from the Java ecosystem and glues them together based on years of experience in building high performance Java services without getting in your way and without forcing you into a large, proprietary framework.

## Getting Started

- [Overview](docs/overview.md)
- [Getting Started](docs/getting_started.md)
  - Then see [Next Steps](docs/next_steps.md)

## Reference

- [Configuration](docs/ref_configuration.md)
- [Lifecycle/Bootstrapping](docs/ref_lifecycle.md)
- TBD - Concurrency
- TBD - Database Pooling
- TBD - Discovery
- TBD - Events
- TBD - HTTP server
- [HTTP client](http-client/README.md)
- TBD - Packaging
- TBD - Logging
- TBD - Tracing
- TBD - Maven BOM
- TBD - Jackson/JSON

## Recipes

- How do I ... [do conditional binding based on config](docs/recipes.md#how-do-i-do-conditional-binding-based-on-a-config-value)
- How do I ... [serve static HTML files](docs/recipes.md#how-do-i-serve-static-html-files)
- How do I ... [package my service](docs/recipes.md#how-do-i-package-my-service)
- TBD - How do I do ... ?

