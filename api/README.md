[◀︎ Airlift](../README.md)

# API Builder

API Builder is an abstraction that makes writing APIs standardized. The intention is to
codify and validate an API that is based on the [Google Cloud API design guide](https://cloud.google.com/apis/design). The framework is opinionated 
and restricts what is possible.

The Google design guide is based on the these concepts:

- _Resources_ - Resources (aka models) are validated to conform to the guide's conventions. [See: Guide](https://cloud.google.com/apis/design/resources)
- _Methods_ - Instead of lower-level HTTP/REST methods, the guide's methods (List, Get, Create, Update, Delete) are used. [See: Guide](https://cloud.google.com/apis/design/standard_methods)
- _URIs_ - Generated URIs conform to the specification of the guide. [See: Guide](https://cloud.google.com/apis/design/resource_names)

## Introduction

See [Introduction](docs/starting.md) for details on how API Builder enhances JAX-RS/Jersey and how to get started with API Builder.

## Reference

| Document                                            | Description                                                                                              |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| [Resources](docs/resources.md)                      | Details on what constitutes a "resource", how to create them, etc.                                       |
| [Methods](docs/methods.md)                          | Details on operation/method types, how to create new methods, etc.                                       |
| [URIs](docs/uris.md)                                | How URIs are automatically generated and their format                                                    |
| [Patching](docs/patch.md)                           | How to implement updates/PATCH                                                                   |
| [Pagination](docs/pagination.md)                    | How to implement paginated query responses                                                               |
| [Filtering](docs/filtering.md)                      | How to implement filtered GET operations (i.e. APIs that allow filtering on fields such as catalog name) |
| [Ordering](docs/ordering.md)                        | How to implement ordered GET operations (i.e. APIs that allow ordering on fields such as catalog name)   |
| [Validate Only](docs/validate-only.md)              | How to implement validate-only requests                                                                  |
| [Modifiers and Headers](docs/modifiers.md)          | How to implement optional request modifiers and headers                                                  |
| [Deprecation](docs/deprecation.md)                  | How to deprecate methods/APIs                                                                            |
| [OpenAPI](docs/openapi.md)                          | Details of API Builder's OpenAPI/Swagger support                                                         |
| [Verification and Enforcement](docs/enforcement.md) | How methods, resources, etc. rules are verified and enforced                                             |
| [Custom Methods](docs/custom.md)                    | How to create custom method operations                                                                   |
| [Quotas](docs/quotas.md)                            | Managing resource quotas                                                                                 |
| [Streaming](docs/streaming.md)                      | Streaming responses                                                                                      |
| [Multi-part Uploads](docs/multipart.md)             | Multi-part uploads                                                                                       |
