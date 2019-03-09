# Java Agent for OpenTracing,<br>with auto-instrumentation

> Automatically instruments 3rd-party libraries in Java applications

[![Build Status](https://travis-ci.org/opentracing-contrib/java-specialagent.png)](https://travis-ci.org/opentracing-contrib/java-specialagent)
[![Coverage Status](https://coveralls.io/repos/github/opentracing-contrib/java-specialagent/badge.svg)](https://coveralls.io/github/opentracing-contrib/java-specialagent)
[![Released Version](https://img.shields.io/maven-central/v/io.opentracing.contrib.specialagent/specialagent.svg?maxAge=2592000)](https://search.maven.org/search?q=a:opentracing-specialagent)

## Overview

This file contains the operational instructions for the use and development of <ins>SpecialAgent</ins>.

## Definitions

#### <ins>SpecialAgent</ins>

A Java agent that automatically instruments distinct 3rd-party libraries in Java applications via the OpenTracing API.

#### <ins>Tracer</ins>

Vendor implementation of the `io.opentracing.Tracer` interface.

Examples:
* [LightStep Tracer](https://github.com/lightstep/lightstep-tracer-java)
* [Jaeger Tracer](https://github.com/jaegertracing/jaeger)
* [DataDog Tracer](https://github.com/DataDog/dd-trace-java)
* [`MockTracer`](https://github.com/opentracing/opentracing-java/blob/master/opentracing-mock/)

_<ins>Tracers</ins> **ARE NOT** coupled to the <ins>SpecialAgent</ins>._

#### <ins>Tracer Plugin</ins>

A bridge providing automatic discovery of <ins>Tracers</ins> in a runtime instrumented with the OpenTracing API. This bridge implements the `TracerFactory` interface of [TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver/blob/master/opentracing-tracerresolver/), and is distributed as a single "fat JAR" that can be conveniently added to the classpath of a Java process.

_<ins>Tracer Plugins</ins> **ARE NOT** coupled to the <ins>SpecialAgent</ins>._

#### <ins>Instrumentation Plugin</ins>

An OpenTracing Instrumentation project that exist as individual repositories under [opentracing-contrib][opentracing-contrib].

Examples:
* [`opentracing-contrib/java-okhttp`][java-okhttp]
* [`opentracing-contrib/java-jdbc`][java-jdbc]
* [`opentracing-contrib/java-jms`][java-jms]

_<ins>Instrumentation Plugins</ins> **ARE NOT** coupled to the <ins>SpecialAgent</ins>._

#### <ins>Instrumentation Rules</ins>

A submodule of the <ins>SpecialAgent</ins> that implements the auto-instrumentation rules for <ins>Instrumentation Plugins</ins> via the [`opentracing-specialagent-api`][api].

Examples:
* [`rules/opentracing-specialagent-okhttp`][specialagent-okhttp]
* [`rules/opentracing-specialagent-jdbc`][specialagent-jdbc]
* [`rules/opentracing-specialagent-jms-1`][specialagent-jms-1]
* [`rules/opentracing-specialagent-jms-2`][specialagent-jms-2]

_<ins>Instrumentation Modules</ins> **ARE** coupled to the <ins>SpecialAgent</ins>._

## Supported <ins>Instrumentation Plugins</ins>

> These plugins have <ins>Instrumentation Rules</ins> implemented.

1. [OkHttp3][java-okhttp]
2. [JDBC API (`java.sql`)][java-jdbc]
3. [Concurrent API (`java.util.concurrent`)](https://github.com/opentracing-contrib/java-concurrent)
4. [Java Web Servlet API (`javax.servlet`)](https://github.com/opentracing-contrib/java-web-servlet-filter)
5. [Mongo Driver](https://github.com/opentracing-contrib/java-mongo-driver)
6. [Apache Camel](https://github.com/apache/camel/tree/master/components/camel-opentracing)
7. [AWS SDK](https://github.com/opentracing-contrib/java-aws-sdk)
8. [Cassandra Driver](https://github.com/opentracing-contrib/java-cassandra-driver)
9. [JMS API (`javax.jms` v1 & v2)][java-jms]
10. [Elasticsearch6 Client](https://github.com/opentracing-contrib/java-elasticsearch-client)
11. [RxJava 2](https://github.com/opentracing-contrib/java-rxjava)

## Goals

1. The <ins>SpecialAgent</ins> must allow any <ins>Instrumentation Plugin</ins> available in [opentracing-contrib][opentracing-contrib] to be automatically installable in applications that utilize a 3rd-party library for which an <ins>Instrumentation Plugin</ins> exists.
2. The <ins>SpecialAgent</ins> must automatically install the <ins>Instrumentation Plugin</ins> for each 3rd-party library for which a module exists, regardless in which class loader the 3rd-party library is loaded.
3. The <ins>SpecialAgent</ins> must not adversely affect the runtime stability of the application on which it is intended to be used. This goal applies only to the code in the <ins>SpecialAgent</ins>, and cannot apply to the code of the <ins>Instrumentation Plugins</ins> made available in [opentracing-contrib][opentracing-contrib].
4. The <ins>SpecialAgent</ins> must support static and dynamic attach to applications running on JVM versions 1.7, 1.8, 9, and 11.
5. The <ins>SpecialAgent</ins> must implement a lightweight test methodology that can be easily applied to a module that implements instrumentation for a 3rd-party library. This test must simulate:
   1. Launch the test in a process simulating the `-javaagent` vm argument that points to the <ins>SpecialAgent</ins> (in order to test auto-instrumentation functionality).
   2. Elevate the test code to be executed from a custom class loader that is disconnected from the system class loader (in order to test bytecode injection into an isolated class loader that cannot resolve classes on the system classpath).
   3. Allow tests to specify their own `Tracer` instances via `GlobalTracer`, or initialize a `MockTracer` if no instance is specified. The test must provide a reference to the `Tracer` instance in the test method for assertions with JUnit.
6. The <ins>SpecialAgent</ins> must provide a means by which <ins>Instrumentation Plugins</ins> can be configured before use on a target application.

## Non-Goals

1. The <ins>SpecialAgent</ins> is not designed to modify application code, beyond the installation of <ins>Instrumentation Plugins</ins>. For example, there is no facility for dynamically tracing arbitrary code.

## Operation

When <ins>SpecialAgent</ins> attaches to an application, either statically or dynamically, it will automatically load the <ins>Instrumentation Modules</ins> explicitly specified as dependencies in its POM.

Any exception that occurs during the execution of the bootstrap process will not adversely affect the stability of the target application. It is, however, possible that the <ins>Instrumentation Plugin</ins> code may result in exceptions that are not properly handled, and could destabilize the target application.

## Installation

The <ins>SpecialAgent</ins> has 2 artifacts: main and test. These artifacts are built by Maven, and can be created by cloning this repository and following the [Building](#building) instructions. These artifacts can also be downloaded directly from Maven's Central Repository.

1. &nbsp;&nbsp;&nbsp;&nbsp;RELEASE: [`opentracing-specialagent-0.9.0.jar`][main-release]<br>
   SNAPSHOT: [`opentracing-specialagent-0.9.1-SNAPSHOT.jar`][main-snapshot]

    This is the main artifact that contains within it all applicable <ins>Instrumentation Plugins</ins> from the [opentracing-contrib][opentracing-contrib] organization. This JAR can be specified as the `-javaagent` target for static attach to an application. This JAR can also be executed, standalone, with an argument representing the PID of a target process to which it should dynamically attach. Please refer to [Usage](#usage) section for usage instructions.

1. &nbsp;&nbsp;&nbsp;&nbsp;RELEASE: [`opentracing-specialagent-0.9.0-tests.jar`][test-release]<br>
   SNAPSHOT: [`opentracing-specialagent-0.9.1-SNAPSHOT-tests.jar`][test-snapshot]

    This is the test artifact that contains within it the `AgentRunner`, which is a JUnit runner class provided for testing of the ByteBuddy auto-instrumentation rules. This JAR does not contain within it any <ins>Instrumentation Plugins</ins> themselves, and is only intended to be applied to the test phase of the build lifecycle of a single plugin for an <ins>Instrumentation Plugin</ins> implementation. For direction with the `AgentRunner`, please refer to the [`opentracing-specialagent-api`][api] module.

## Usage

The <ins>SpecialAgent</ins> uses [Java’s Instrumentation mechanism](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html) to transform the behavior of a target application. The entrypoint into the target application is performed via Java’s Agent convention. <ins>SpecialAgent</ins> supports both static and dynamic attach.

### Static Attach

Statically attaching to a Java application involves the use of the `-javaagent` vm argument at the time of startup of the target Java application. The following command can be used as an example:

```bash
java -javaagent:opentracing-specialagent-0.9.0.jar -jar myapp.jar
```

This command statically attaches <ins>SpecialAgent</ins> into the application in `myapp.jar`.

### Dynamic Attach

Dynamically attaching to a Java application involves the use of a running application’s PID, after the application’s startup. The following commands can be used as an example:

1. Call this to obtain the `PID` of the target application:
    ```bash
    jps
    ```

2. For jdk1.8
    ```bash
    java -Xbootclasspath/a:$JAVA_HOME/lib/tools.jar -jar opentracing-specialagent-0.9.0.jar <PID>
    ```

3. For jdk9+
    ```bash
    java -jar opentracing-specialagent-0.9.0.jar <PID>
    ```

## Configuration

The <ins>SpecialAgent</ins> exposes a simple pattern for configuration of <ins>SpecialAgent</ins>, the <ins>Instrumentation Plugins</ins>, as well as <ins>Tracer Plugins</ins>. The configuration pattern is based on system properties, which can be defined on the command-line, in a properties file, or in [@AgentRunner.Config](#configuring-agentrunner):

1. Properties passed on the command-line via `-D${PROPERTY}=...` override same-named properties defined in layers below.

2. The [@AgentRunner.Config](#configuring-agentrunner) annotation allows one to define log level and transformation event logging settings. Properties defined in the `@Config` annotation override same-named properties defined in layers below.

3. The `-Dconfig=${PROPERTIES_FILE}` command-line argument can be specified to have the <ins>SpecialAgent</ins> load property names from a `${PROPERTIES_FILE}`. Properties defined in the `${PROPERTIES_FILE}` override same-named properties defined in the layer below.

4. The <ins>SpecialAgent</ins> has a `default.properties` file that defines default values for properties that need to be defined.

### Disabling <ins>Instrumentation Modules</ins>

The <ins>SpecialAgent</ins> allows <ins>Instrumentation Modules</ins> to be disabled. To disable a plugin, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
${PLUGIN_NAME}.enabled=false
```

The value of `${PLUGIN_NAME}` is the `artifactId` of the plugin as it is included in the <ins>SpecialAgent</ins>, such as `opentracing-specialagent-okhttp` or `opentracing-specialagent-web-servlet-filter`.

### Building

The <ins>SpecialAgent</ins> is built in 2 profiles:

1. The `default` profile is used for development of <ins>Instrumentation Rules</ins>. It builds and runs tests for each rule, but _does not put the rules_ into [`opentracing-specialagent-0.9.0.jar`][main-release]

    To run this profile:
    ```bash
    mvn clean package
    ```

2. The `assemble` profile is used for packaging of <ins>Instrumentation Rules</ins> into [`opentracing-specialagent-0.9.0.jar`][main-release]. It builds each rule, but does not run tests. Once the build with the `assemble` profile is finished, the [`opentracing-specialagent-0.9.0.jar`][main-release] will contain the built rules inside it.

    _**Note**: If you do not run this step, the [`opentracing-specialagent-0.9.0.jar`][main-release] from the previous step will not contain any <ins>Instrumentation Plugins</ins> within it!_

    _**Note**: It is important to **not** run Maven's `clean` lifecycle when creating the `assemble` package._

    To run this profile:
    ```bash
    mvn -Dassemble package
    ```

* For a one-line build command to build <ins>SpecialAgent</ins>, its rules, run all tests, and create the `assemble` package:

    ```bash
    mvn clean package && mvn -Dassemble package
    ```

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

### License

This project is licensed under the Apache 2 License - see the [LICENSE.txt](LICENSE.txt) file for details.

[api]: https://github.com/opentracing-contrib/java-specialagent/tree/master/opentracing-specialagent-api
[java-jdbc]: https://github.com/opentracing-contrib/java-jdbc
[java-jms]: https://github.com/opentracing-contrib/java-jms
[java-okhttp]: https://github.com/opentracing-contrib/java-okhttp
[main-release]: https://search.maven.org/remotecontent?filepath=io/opentracing/contrib/specialagent/opentracing-specialagent/0.9.0/opentracing-specialagent-0.9.0.jar
[main-snapshot]: https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/opentracing-specialagent/0.9.1-SNAPSHOT
[opentracing-contrib]: https://github.com/opentracing-contrib/
[specialagent-jdbc]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/opentracing-specialagent-jdbc
[specialagent-jms-1]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/opentracing-specialagent-jms-1
[specialagent-jms-2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/opentracing-specialagent-jms-2
[specialagent-okhttp]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/opentracing-specialagent-okhttp
[test-release]: https://search.maven.org/remotecontent?filepath=io/opentracing/contrib/specialagent/opentracing-specialagent/0.9.0/opentracing-specialagent-0.9.0-tests.jar
[test-snapshot]: https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/opentracing-specialagent/0.9.1-SNAPSHOT