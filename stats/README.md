* [HyperLogLog](docs/hll.md)
* [Quantile Digest](docs/qdigest.md)

## Optional Vector API acceleration

Dense HyperLogLog baseline adjustment uses packed 64-bit operations by default.
Applications can enable SIMD acceleration for larger sketches by launching Java with:

```
--add-modules=jdk.incubator.vector
```

The accelerated path uses the preferred vector width of the running JVM. Small
sketches and blocks containing overflow entries retain scalar/SWAR handling.
The Vector API is an incubator API on the project's JDK 25 baseline; enabling it
is optional. No additional JVM flags are needed for ordinary use of `stats`.
Both paths preserve the serialized format and cardinality estimates.
