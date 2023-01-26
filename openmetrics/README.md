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
      static_configs:
      - targets:
        - localhost:8080
