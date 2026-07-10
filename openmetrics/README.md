# OpenMetrics resource for airlift

To use, add the `JmxOpenMetricsModule` to your bootstrap such as:

    Bootstrap app = new Bootstrap(
            ...
            new JmxOpenMetricsModule(),
            ...
            new MainModule());

See the Sample Server for an example.

Prometheus can be configured to hit the openmetrics endpoint using:

    scrape_configs:
    - job_name: sample_server
      scrape_native_histograms: true
      static_configs:
      - targets:
        - localhost:8080

The `/metrics` endpoint supports OpenMetrics 1.0 text and the Prometheus protobuf exposition
format. The response is selected from the request's `Accept` header. For protobuf, use:

    Accept: application/vnd.google.protobuf; proto=io.prometheus.client.MetricFamily; encoding=delimited

When the OpenTelemetry stats backend is active, protobuf responses expose `TimeDistribution`,
`TimeStat`, `Distribution`, and `DistributionStat` values as Prometheus native exponential
histograms. OpenMetrics text responses continue to expose summaries for compatibility. Time
histograms use nanosecond values and include the `ns` unit in the protobuf metric family.
