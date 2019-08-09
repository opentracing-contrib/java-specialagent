# Changes by Version

## v1.3.4 (2019-08-09)
* Support for Spring 4.
* Standard stream logging.
* Disable class format changes.
* Stability updates to [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Apache HTTP Client, Feign.
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Spring 4 Web, Spring 4 Web MVC, Elasticsearch 7, AWS SDK 2.

## v1.3.3 (2019-07-26)
* Support Servlet API 2.3 with `web-servlet-filter` Plugin.
* Remedy class collision errors of conflicting library versions.
* Reduce excess log verbosity.
* Support windows slashes.
* Update JDBC Plugin to `0.1.5`.
* Update Grizzly AHC Plugin to `0.1.3`.
* Minor fixes to Kafka Messaging, Redisson, and Cassandra plugins.
* Resolve conflicting transitive dependencies in Instrumentation Plugins.
* Update Fingerprinting Algorithm to support (full)-depth-n fingerprints.
* Implement [static-deferred](https://github.com/opentracing-contrib/java-specialagent/#223-static-deferred-attach) attachment strategy.
* Implement [mechanism to disable `AgentRule`s](https://github.com/opentracing-contrib/java-specialagent#343-disabling-agentrules-of-an-instrumentation-plugin).
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Spring 3 Web, Spring 3 Web MVC, JAX-RS, Thread.

## v1.3.2 (2019-06-28)
* Fix to propagation of system properties via dynamic attach.
* Implement assert that name of SpecialAgent JAR is correct.
* Make SpecialAgent thread safe.
* Update LightStep and Jaeger Tracer Plugins.
* Update Fingerprinting Algorithm to support (semi)-depth-n fingerprints.
* Update [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): JDBC.

## v1.3.1 (2019-06-17)
* Re-integrate [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Grizzly AHC, Grizzly Http Server, Reactor.

## v1.3.0 (2019-06-14)
* Officially support Spring plugins.
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Spring Messaging, Spring WebSocket STOMP, Spring `@JmsListener`, Spring RabbitMQ.
* Update to Apache HttpClient Plugin: Added `hostname` and `port` tags to http client spans.
* Upgrade to SpecialAgent API in lieu of Fingerprinting Algorithm rewrite.
* Resolve issue #86: DynamoDB Calls regarding bad headers when using LightStep.
* Resolve issue #87: Apache HttpClient Plugin regarding default HTTP port.
* Resolve issue #88: Redis Plugin regarding PubSub.

## v1.2.0 (2019-05-31)
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Feign, Zuul, Spring Kafka, Spring Scheduling.
* Upgrade to SpecialAgent API, requiring AgentRule(s) to explicitly specify a name for the instrumentation plugin.
* Improved configuration spec utilizing short names for instrumentation plugins.
* Fix to bug regarding dependency resolution of a plugin's dependency graph.
* Improved API documentation.

## v1.1.2 (2019-05-17)
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Redis, Spring Web Flux, Grizzly HTTP Server, Grizzly AsyncHttpClient, Reactor, Hazelcast, Spymemcached.
* Upgraded Jaeger Agent Plugin to support OT API 0.32.0.
* Startup performance improvement.
* Improved documentation.
* Bug fixes.

## v1.1.1 (2019-05-03)
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Apache HttpClient, Redis Client Lettuce, Spring Web, Spring Web MVC.
* Addition of configurable "General Verbose Mode" in `AgentRule`.
* Fix class loading issues seen in Tomcat and Elasticsearch6.
* Fix Mongo Driver rule in lieu of upgrade to OT API v0.32.0.
* Fix `OkHttp` rule to also instrument default constructor.
* Now reporting code test coverage on Coveralls.
* Declared support for OpenJDK.
* Improved documentation.

## v1.1.0 (2019-04-19)
* Place `Tracer` instances into an isolated class loader.
* Upgrade to OpenTracing API 0.32.0.
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Jedis, RabbitMQ, RabbitMQ Spring, gRPC, and Apache Thrift.
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
* Initial release.