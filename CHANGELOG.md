# Changes by Version

## v1.5.8 (2020-02-14)
* New instrumentation plugins: Mule 4 (#324), Pulsar Functions (#398)
* New integration tests: Pulsar Functions (#408 #411 #412)
* Mechanism to load customized span decorators externally (#410)
* Mechanism in `TestUtil` to check span count by component name (#413 #414)
* Rebase `servlet` rule with current implementation of `java-web-servlet-filter` (#400)
* Modify `servlet` rule to add `skipPattern` and `spanDecorators` (#399)
* Modify `servlet` rule to allow creation of tags from HTTP headers (#402)
* Solution to prevent tracers getting instrumented by SpecialAgent (#381)
* Fix Static Deferred Attach not executing hook for Spring Boot apps (#382)
* Fix to stack-overflow in `FilterAgentIntercept` (#391)
* Fix to memory leak in `servlet` rule with Jetty 9.4 and Spring 5 (#395)
* Fix error tag for `jedis` (#396)
* Cleanup and refactor patterns (#397)

## v1.5.7 (2020-02-01)
* Improve intercept logging (#385)
* Improve test coverage for init patterns (#342)
* Fix download links in README.md (#386)
* Make `TracedMessage` distributable (#380)
* Fix Static Deferred Attach for Spring Boot based apps (#379)
* Determine libs transitively supported by existing instrumentation task (#329)
* Implement integration tests for transitively supported libs (#330)
* Add integration tests for Ratpack (#378)
* Add integration tests for SparkJava (#373)
* Add integration tests for Twilio (#371)
* Fix `NoSuchMethodException: TracingFilter.buildSpan(...)` (#369)
* Use `WrapperProxy` from "io.opentracing.contrib:common" (#374)
* Implement [Dynamic Instrumentation Rule](https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/dynamic/) (#375)
* Fix "object is not an instance of declaring class" resulting from `DynamicProxy` (#368)
* Fix incorrect return from `PluginManifest.getPluginManifest` when called from `AgentRunner` (#372)
* Fix different span behavior for Spring WebMVC with Static Deferred Attach (#358)
* Make `JmsTest` in JMS-2 more stable (#364)
* Allow Static Deferred Attach to work with Spring Boot apps built with `spring-boot-maven-plugin` (#362)
* Add support for global tags when using LightStep Tracer (#320)
* Update `SpecialAgentUtil.convertToNameRegex(...)` to match name literals (i.e. "spring:boot") (#363)
* Update Instrumentation Plugins (#387)
* Tokenize `${sa.itest.args}` in `itest-maven-plugin` to (#388)
* Fix `VerifyError: "Illegal type in constant pool"` due to `isEnabled(Class,String)` (#389)
* Fix Static Deferred Attach initializing Spring Boot too early (#367)

## v1.5.6 (2020-01-17)
* Improve test module structure (#348)
* Use DynamicProxy for all objects instrumented via inheritance pattern (#359)
* Update `CouchbaseClientTest` to resemble `CouchbaseClientITest` (#352)
* Static deferred attach is unavailable (#356)
* Upgrade JDBC Plugin to v0.2.7

## v1.5.5 (2020-01-10)
* Implement AgentRule(s) for Neo4J Driver (#321)
* Implement AgentRule(s) for Netty (#318)
* Explicitly support OpenJDK (#158)
* Explicitly support Jetty 9.3.x and 9.4.x (#327)
* Use strict advice clauses in FilterAgentRule (#331, #338)
* Improve integration test architecture (#335)
* Upgrade JDBC Plugin to v0.2.5 (DynamicProxy) (#343)

## v1.5.4 (2019-12-27)
* Fix to possible race condition in "servlet" rule (#316)
* Implement AgentRule(s) for Google Http Client (#314)
* Implement AgentRule(s) for Couchbase Client (#310)
* Implement integration test for Spymemcached (#305)

## v1.5.3 (2019-12-13)
* Reduce source & target versions for all plugins (#277)
* Fix to startup failure with Dynamic Attach on Windows (#288)
* Fix to `-Dsa.log.file` resulting in empty logs (#294)
* Add ability to specify system properties to AgentRunner runtimes (#307)
* Implement instrumentation rule for `HttpUrlConnection` (#300)
* Implement [Static Deferred Attach for SpringWebMVC](https://github.com/opentracing-contrib/java-specialagent/#static-deferred-attach-is-currently-supported-for) (#304)
* Implement integration test for `ForkJoinPool` (#102)
* Implement integration tests for
  1. Zuul (#266)
  1. Spring WebFlux (#275)
  1. Spring WebMvc (#278)
  1. Spring Scheduling (#281)
  1. Spring Kafka (#284)
  1. Spring RabbitMQ (#289)
  1. Spring JMS (#291)
  1. Spring Messaging (#295)
  1. Spring WebSocket (#297)

## v1.5.2 (2019-11-29)
* Integrate [OpenTelemetry Bridge Tracer Plugin](https://github.com/opentracing-contrib/java-opentelemetry-bridge) into SpecialAgent (#229)
* Implement Formal API for Static Deferred Attach (#267)
* Implement integration tests for:
  1. Hazelcast (#230)
  1. JAX-RS Client (#235)
  1. Apache HttpClient (#233)
  1. Kafka Client (#244)
  1. Lettuce (#247)
  1. Reactor (#255)
  1. Jedis (#257)
  1. RabbitMQ Client (#249)
  1. Redisson (#260)
  1. Thrift (#263)
  1. Spring Web (#270)
* Upgrade `opentracing-grpc` to 0.2.x (#242)
* Upgrade `opentracing-elasticsearch` to 0.1.6 (#251)
* Upgrade `opentracing-jdbc` to 0.2.3 (#268)
* Fix to: `elasticsearch-client-rest` plugin causing "authorization failed" (#240)
* Fix to: `GrpcTest` is unstable (#237)
* Fix to: `KafkaClientITest` is unstable (#262)
* Fix to: Termination due to absolute/relative file path mismatch (#225)

## v1.5.1 (2019-11-15)
* New [instrumentation plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Akka Http Server, OkHttp 4, Play Framework (server and client).
* Include external instrumentation plugins (those which are not included in the SpecialAgent, such as proprietary plugins).
* Fix to `ClassLoaderAgentRule` bug leading to `NoClassDefFoundException`.
* Static Deferred Attach for Spring Boot versions 1.0.0.RELEASE to LATEST.
* Automatic activation of Static Deferred Attach for Spring Boot applications.

## v1.5.0 (2019-11-01)
* Migrate integration tests for: OkHttp, JDBC API, Concurrent API, MongoDB Driver, Servlet API (Jetty and Tomcat), AWS SDK 1 and 2, Cassandra Driver, JMS 1 and 2, ElasticSearch 6 and 7, RxJava 2, Akka Http.
* Expanded ranges of versions tested in integration-test phase.
* Updated [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins) to most recent versions.
* Updated package and class names for all [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins) to conform to naming convention.
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Akka Http Client.

## v1.4.2 (2019-10-18)
* Fix bug in config properties import process ([#179](https://github.com/opentracing-contrib/java-specialagent/issues/179)).
* Fix bug in `servlet` plugin resulting in duplicate requests ([#178](https://github.com/opentracing-contrib/java-specialagent/issues/178)).
* Introduced "plugin groups", with new plugin naming convention.
* Linked integration tests: OkHttp, JDBC.
* Updated [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Spring 4 Web, Spring 4 Web MVC.
* New [Instrumentation Plugins](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins): Lettuce 5.2.

## v1.4.1 (2019-10-03)
* Fix class loader issues in JDBC Plugin.
* Fix J9+ cross-module readability issues.
* Support ASYNC mode in `web-servlet-filter`.
* Integrate [Wavefront Tracer Plugin](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java) into SpecialAgent.
* Exclusively rely on Fingerprint Algo to qualify plugin compatibility in all cases.
* Update Lettuce and Lettuce-50 plugins to load mutually exclusively.
* Update Spring Web and WebMVC plugins (for versions 3, 4, and 5) to load mutually exclusively.

## v1.4.0 (2019-09-20)
* Improved rigor in requirements imposed onto JUnit tests by `AgentRunner`.
* Improved class loading architecture.
* Improved class loading policy for `RuleClassLoader`.
* Improved stability and resilience to `LinkageError`s.
* Support Tomcat 7.0.96 with `web-servlet-filter`.
* Optimized load time and runtime performance.

## v1.3.6 (2019-09-06)
* Resolve minimum versions of 3rd-party libraries supported by each [Instrumentation Plugin](https://github.com/opentracing-contrib/java-specialagent/#supported-instrumentation-plugins).
* Resolve false negative compatibility tests in fingerprinting algorithm.
* Remove reliance of JMS-2 plugin onto JMS-1 plugin.
* Improve performance of fingerprint generation.
* Fix issue that may result in NoClassDefFoundError during JVM shutdown hook.

## v1.3.5 (2019-08-23)
* Resolve class signature conflicts in `web-servlet-filter` Plugin.
* Upgrade core libraries.
* Improve stability of tests.
* Improve speed of fingerprint creation.
* Resolve conflict with Java Logging system properties.
* Isolate TracerResolver into non-interfering class loader.
* Remove Hystrix Rule, as its functionality is convered by Java Concurrent Plugin.

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
* Resolve issue [#86](https://github.com/opentracing-contrib/java-specialagent/issues/86): DynamoDB Calls regarding bad headers when using LightStep.
* Resolve issue [#87](https://github.com/opentracing-contrib/java-specialagent/issues/87): Apache HttpClient Plugin regarding default HTTP port.
* Resolve issue [#88](https://github.com/opentracing-contrib/java-specialagent/issues/88): Redis Plugin regarding PubSub.

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