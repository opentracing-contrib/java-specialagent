# Changes by Version

## v1.1.0 (2019-04-19)
* Place `Tracer` instances into an isolated class loader.
* Upgrade to OpenTracing API 0.32.0.
* Support instrumentation plugins: Jedis, RabbitMQ, RabbitMQ Spring, gRPC, and Apache Thrift.
* Upgraded `opentracing-specialagent-api`.
* Improved logging: see intercept events on `Level.FINER`.

## v1.0.2 (2019-04-05)
* Release `agentrule-maven-plugin` in deployment.
* Support any 3rd-party libraries present on BootPath.
* Upgrade LightStep Tracer to 0.15.4 for interoperability with SpringBoot.

## v1.0.1 (2019-03-15)
* Resolve bugs in dependency correlation for plugin wiring in SpecialAgent runtime.
* Standardize `-Dsa.` prefix for properties of SpecialAgent.
* Support execution of SpecialAgent with a tracer in IDEs.
* Implement `ProxyMockTracer`, which allows all tests to plug into a real tracer as specified by the `-Dsa.tracer=...` property.
* General performance improvements.

## v1.0.0 (2019-03-15)
* Support for [Tracer Plugins](https://github.com/opentracing-contrib/java-specialagent/#selecting-the-tracer-plugin).
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Elasticsearch 6, RxJava 2, Kafka Client, AsyncHttpClient.
* Performance optimization via exclusion of unintended instrumentation of tracer code.
* Improvements to `AgentRunner` runtime, Fingerprint algorithm, logging and exception behavior.

## v0.9.0 (2019-03-01)
* Initial release