# Java Agent for OpenTracing

> Automatically instruments 3rd-party libraries in Java applications

[![Build Status](https://travis-ci.org/opentracing-contrib/java-specialagent.svg?branch=master)][travis]
[![Coverage Status](https://coveralls.io/repos/github/opentracing-contrib/java-specialagent/badge.svg?branch=master)](https://coveralls.io/github/opentracing-contrib/java-specialagent?branch=master)
[![Javadocs](https://www.javadoc.io/badge/io.opentracing.contrib.specialagent/opentracing-specialagent.svg)](https://www.javadoc.io/doc/io.opentracing.contrib.specialagent/opentracing-specialagent)
[![Released Version](https://img.shields.io/maven-central/v/io.opentracing.contrib.specialagent/specialagent.svg)](https://mvnrepository.com/artifact/io.opentracing.contrib.specialagent/opentracing-specialagent)

<sub>_Note: The coverage statistic is not correct, because Jacoco cannot properly instrument code that is instrumented at the bytecode level._</sub>

## What is SpecialAgent?

<ins>SpecialAgent</ins> automatically instruments 3rd-party libraries in Java applications. The architecture of <ins>SpecialAgent</ins> was designed to involve contributions from the community, whereby its platform integrates and automates OpenTracing <ins>Instrumentation Plugins</ins> written by individual contributors. In addition to <ins>Instrumentation Plugins</ins>, the <ins>SpecialAgent</ins> also supports <ins>Tracer Plugins</ins>, which connect an instrumented runtime to OpenTracing-compliant tracer vendors, such as [LightStep][lightstep], [Wavefront][wavefront], or [Jaeger][jaeger]. Both the <ins>Instrumentation Plugins</ins> and the <ins>Tracer Plugins</ins> are decoupled from <ins>SpecialAgent</ins> -- i.e. neither kinds of plugins need to know anything about <ins>SpecialAgent</ins>. At its core, the <ins>SpecialAgent</ins> is itself nothing more than an engine that abstracts the functionality for automatic installation of <ins>Instrumentation Plugins</ins>, and then connecting them to <ins>Tracer Plugins</ins>. A benefit of this approach is that the <ins>SpecialAgent</ins> intrinsically embodies and encourages community involvement.

In addition to its engine, the <ins>SpecialAgent</ins> packages a set of pre-supported [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins) and [<ins>Tracer Plugins</ins>](#62-tracer-plugins).

## Table of Contents

<samp>&nbsp;&nbsp;</samp>1 [Introduction](#1-introduction)<br>
<samp>&nbsp;&nbsp;</samp>2 [Quick Start](#2-quick-start)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1 [Installation](#21-installation)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.1 [In Application](#211-in-application)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.1.1 [Stable](#2111-stable)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.1.2 [Development](#2112-development)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.2 [For Development](#212-for-development)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.2.1 [<ins>Instrumentation Plugins</ins>](#2121-instrumentation-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.2.2 [<ins>Tracer Plugins</ins>](#2122-tracer-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.2.2.1 [<ins>Short Name</ins>](#21221-short-name)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.2 [Usage](#22-usage)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.2.1 [<ins>Static Attach</ins>](#221-static-attach)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.2.2 [<ins>Dynamic Attach</ins>](#222-dynamic-attach)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.2.3 [<ins>Static Deferred Attach</ins>](#223-static-deferred-attach)<br>
<samp>&nbsp;&nbsp;</samp>3 [Configuration](#3-configuration)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.1 [Overview](#31-overview)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.2 [Properties](#32-properties)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.3 [Selecting the <ins>Tracer Plugin</ins>](#33-selecting-the-tracer-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.4 [Disabling <ins>Instrumentation Plugins</ins>](#34-disabling-instrumentation-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.4.1 [Disabling All Instrumentation Plugins](#341-disabling-all-instrumentation-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.4.2 [Disabling (or enabling) One Instrumentation Plugin](#342-disabling-or-enabling-one-instrumentation-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.4.3 [Disabling `AgentRule`s of an Instrumentation Plugin](#343-disabling-agentrules-of-an-instrumentation-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.5 [Disabling <ins>Tracer Plugins</ins>](#35-disabling-tracer-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.6 [Including custom <ins>Instrumentation Plugins</ins>](#36-including-custom-instrumentation-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.7 [<ins>Rewritable Tracer</ins>](#37-rewritable-tracer)<br>
<samp>&nbsp;&nbsp;</samp>4 [Definitions](#4-definitions)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>4.1 [<ins>SpecialAgent</ins>](#41-specialagent)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>4.2 [<ins>Tracer</ins>](#42-tracer)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>4.3 [<ins>Tracer Plugin</ins>](#43-tracer-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>4.4 [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>4.5 [<ins>Instrumentation Rule</ins>](#45-instrumentation-rule)<br>
<samp>&nbsp;&nbsp;</samp>5 [Objectives](#5-objectives)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>5.1 [Goals](#51-goals)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>5.2 [Non-Goals](#52-non-goals)<br>
<samp>&nbsp;&nbsp;</samp>6 [Supported Plugins](#6-supported-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>6.1 [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>6.2 [<ins>Tracer Plugins</ins>](#62-tracer-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>6.3 [<ins>Instrumented libraries by existing rules</ins>](#63-instrumented-libraries-by-existing-rules)<br>
<samp>&nbsp;&nbsp;</samp>7 [Credits](#7-credits)<br>
<samp>&nbsp;&nbsp;</samp>8 [Contributing](#8-contributing)<br>
<samp>&nbsp;&nbsp;</samp>9 [License](#9-license)

## 1 Introduction

This file contains the operational instructions for the use and development of [<ins>SpecialAgent</ins>](#41-specialagent).

## 2 Quick Start

When [<ins>SpecialAgent</ins>](#41-specialagent) attaches to an application (either [statically or dynamically](#22-usage)), it will load the bundled [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin).

The [<ins>SpecialAgent</ins>](#41-specialagent) is stable -- any exception that occurs during attachment of [<ins>SpecialAgent</ins>](#41-specialagent) will not adversely affect the stability of the target application. It is, however, important to note that [<ins>SpecialAgent</ins>](#41-specialagent) bundles [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) that are developed by 3rd parties and individual contributors. We strive to assert the stability of [<ins>SpecialAgent</ins>](#41-specialagent) with rigorous [integration tests][travis], yet it is still possible that the code in a bundled [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) may result in an exception that is not properly handled, which could potentially destabilize a target application.

### 2.1 Installation

The Maven build of the [<ins>SpecialAgent</ins>](#41-specialagent) project generates 2 artifacts: **main** and **test**. These artifacts can be obtained by cloning this repository and following the [Development Instructions](#212-for-development), or downloading directly from [Maven's Central Repository](https://repo1.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.6.1/).

#### 2.1.1 In Application

The [<ins>SpecialAgent</ins>](#41-specialagent) is contained in a single JAR file. This JAR file is the **main** artifact that is built by Maven, and bundles the [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) from the [opentracing-contrib][opentracing-contrib] organization for which [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) have been implemented.

To use the [<ins>SpecialAgent</ins>](#41-specialagent) on an application, please download the [stable](#2111-stable) or [development](#2112-development) **main** artifact.

The artifact JAR can be provided to an application with the `-javaagent:${SPECIAL_AGENT_JAR}` vm argument for [<ins>Static Attach</ins>](#221-static-attach) and [<ins>Static Deferred Attach</ins>](#223-static-deferred-attach). The artifact JAR can also be executed in standalone fashion, which requires an argument to be passed for the PID of a target process to which [<ins>SpecialAgent</ins>](#41-specialagent) should [<ins>dynamically attach</ins>](#222-dynamic-attach). Please refer to [Usage](#22-usage) section for usage instructions.

##### 2.1.1.1 Stable

The latest stable release is: [1.6.1][main-release]

```bash
wget -O opentracing-specialagent-1.6.1.jar "https://repo1.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.6.1/opentracing-specialagent-1.6.1.jar"
```

##### 2.1.1.2 Development

The latest development release is: [1.6.2-SNAPSHOT][main-snapshot]

```bash
wget -O opentracing-specialagent-1.6.2-SNAPSHOT.jar "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.opentracing.contrib.specialagent&a=opentracing-specialagent&v=LATEST"
```

**Note**: Sometimes the web service call (in the line above) to retrieve the latest SNAPSHOT build fails to deliver the correct download. In order to work around this issue, please consider using the following command (for Linux and Mac OS):

```bash
wget -O opentracing-specialagent-1.6.2-SNAPSHOT.jar $(curl -s https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/specialagent/opentracing-specialagent/1.6.2-SNAPSHOT/ | grep '".*\d\.jar"' | tail -1 | awk -F\" '{print $2}')
```

#### 2.1.2 For Development

The [<ins>SpecialAgent</ins>](#41-specialagent) is built in 2 passes that rely on different profiles:

1. The `default` profile is used for development of [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule). It builds and runs tests for each rule, but _does not bundle the rules_ into [`opentracing-specialagent-1.6.1.jar`][main-release]

    To run this profile:
    ```bash
    mvn clean install
    ```

    _**Note**: If you skip tests, the `assemble` profile will display an error stating that tests have not been run. See [Convenient One-Liners](#convenient-one-liners) for quick ways to build and package [<ins>SpecialAgent</ins>](#41-specialagent)_.

1. The `assemble` profile is used to bundle the [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) into [`opentracing-specialagent-1.6.1.jar`][main-release]. It builds each rule, but _does not run tests._ Once the build with the `assemble` profile is finished, the [`opentracing-specialagent-1.6.1.jar`][main-release] will contain the built rules inside it.

    _**Note**: If you do not run this step, the [`opentracing-specialagent-1.6.1.jar`][main-release] from the previous step will not contain any [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin)!_

    _**Note**: It is important to **not** run Maven's `clean` lifecycle when executing the `assemble` profile._

    To run this profile:
    ```bash
    mvn -Dassemble install
    ```

* For a one-line build command to build [<ins>SpecialAgent</ins>](#41-specialagent), its rules, run all tests, and create the `assemble` package:

    ```bash
    mvn clean install && mvn -Dassemble install
    ```

##### Convenient One-Liners

1. Skipping tests when building [<ins>SpecialAgent</ins>](#41-specialagent).

   ```bash
   mvn -DskipTests clean install
   ```

1. Skipping compatibility tests when building [<ins>SpecialAgent</ins>](#41-specialagent) rules.

   ```bash
   mvn -DskipCompatibilityTests clean install
   ```

1. Packaging [<ins>SpecialAgent</ins>](#41-specialagent) with rules that skipped test execution.

   ```bash
   mvn -Dassemble -DignoreMissingTestManifest install
   ```

##### 2.1.2.1 [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin)

For development of [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin), import the `opentracing-specialagent-api` and `test-jar` of the `opentracing-specialagent`.

```xml
<dependency>
  <groupId>io.opentracing.contrib.specialagent</groupId>
  <artifactId>opentracing-specialagent-api</artifactId>
  <version>1.6.1</version> <!--version>1.6.2-SNAPSHOT<version-->
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>io.opentracing.contrib.specialagent</groupId>
  <artifactId>opentracing-specialagent</artifactId>
  <version>1.6.1</version> <!--version>1.6.2-SNAPSHOT<version-->
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

The `test-jar` is the **test** artifact that contains the `AgentRunner` class, which is a JUnit runner provided for testing of the [ByteBuddy](https://bytebuddy.net/) auto-instrumentation rules. This JAR does not contain [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) themselves, and is only intended to be applied to the [test phase](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html#Build_Lifecycle_Basics) of the build lifecycle of a single plugin for an [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) implementation. For direction with the `AgentRunner`, please refer to the [`opentracing-specialagent-api`][api] module.

##### 2.1.2.2 [<ins>Tracer Plugins</ins>](#43-tracer-plugin)

[<ins>Tracer Plugins</ins>](#43-tracer-plugin) integrate with the [<ins>SpecialAgent</ins>](#41-specialagent) via the [OpenTracing TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver), which connect the [<ins>SpecialAgent</ins>](#41-specialagent) to a [<ins>Tracer</ins>](#42-tracer). [<ins>Tracer Plugins</ins>](#43-tracer-plugin) integrate to the [<ins>SpecialAgent</ins>](#41-specialagent) via the [SPI mechanism](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html) defined in the [TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver), and are therefore not coupled to the [<ins>SpecialAgent</ins>](#41-specialagent).

[<ins>Tracer Plugins</ins>](#43-tracer-plugin) must be provided as "fat JARs" that contain the full set of all classes necessary for operation.

If the external [<ins>Tracer Plugin</ins>](#43-tracer-plugin) JAR imports any `io.opentracing:opentracing-*` dependencies, the `io.opentracing.contrib:opentracing-tracerresolver`, or any other OpenTracing dependencies that are guaranteed to be provided by <ins>SpecialAgent</ins>, then these dependencies **MUST BE** excluded from the JAR, as well as from the dependency spec.

[<ins>Tracer Plugins</ins>](#43-tracer-plugin) are integrated with the [<ins>SpecialAgent</ins>](#41-specialagent) by specifying a "provided" dependency in the `!itest` profile in the [root POM][specialagent-pom]. For instance, the dependency for the [Jaeger Tracer Plugin](https://github.com/opentracing-contrib/java-opentracing-jaeger-bundle) is:

```xml
<dependency>
  <groupId>io.opentracing.contrib</groupId>
  <artifactId>jaeger-client-bundle</artifactId>
  <version>1.0.0</version>
  <scope>provided</scope>
</dependency>
```

###### 2.1.2.2.1 <ins>Short Name</ins>

Each [<ins>Tracer Plugin</ins>](#43-tracer-plugin) integrated with the [<ins>SpecialAgent</ins>](#41-specialagent) must define a <ins>Short Name</ins>, which is a string that is used to reference the plugin with the `-Dsa.plugin=<SHORT_NAME>` system property. To provide a <ins>Short Name</ins> for the [<ins>Tracer Plugin</ins>](#43-tracer-plugin), you must define a Maven property in the [root POM][specialagent-pom] with the name matching the `artifactId` of the [<ins>Tracer Plugin</ins>](#43-tracer-plugin) module. For instance, the <ins>Short Name</ins> for the [Jaeger Tracer Plugin](https://github.com/opentracing-contrib/java-opentracing-jaeger-bundle) is defined as:

```xml
<properties>
...
  <jaeger-client-bundle>jaeger</jaeger-client-bundle>
...
</properties>
```

### 2.2 Usage

The [<ins>SpecialAgent</ins>](#41-specialagent) is used by attaching to a target application. Once attached, the [<ins>SpecialAgent</ins>](#41-specialagent) relies on [Java’s Instrumentation mechanism](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html) to transform the behavior of the application.

[<ins>SpecialAgent</ins>](#41-specialagent) supports the following attach modes:

| Attach Mode | Number of Required<br>Commands to Attach | Plugin and Rule<br>Initialization Timeline |
|:-|:-:|:-:|
| [<ins>Static Attach</ins>](#221-static-attach)<br>&nbsp; | 1 (sync)<br>&nbsp; | Before app start<br><sup>(any application)</sup> |
| [<ins>Dynamic Attach</ins>](#222-dynamic-attach)<br>&nbsp; | 2 (async)<br>&nbsp; | After app start<br><sup>(any application)</sup> |
| [<ins>Static Deferred Attach</ins>](#223-static-deferred-attach)<br>&nbsp; | 1 (sync)<br>&nbsp; | After app start<br><sup>([some applications](#static-deferred-attach-is-currently-supported-for))</sup> |

#### 2.2.1 <ins>Static Attach</ins>

With [<ins>Static Attach</ins>](#221-static-attach), the application is executed with the `-javaagent` argument, and the agent initialization occurs before the application is started. This mode requires 1 command from the command line.

Statically attaching to a Java application involves the use of the `-javaagent` vm argument at the time of startup of the target Java application. The following command can be used as an example:

```bash
java -javaagent:opentracing-specialagent-1.6.1.jar -jar MyApp.jar
```

This command statically attaches [<ins>SpecialAgent</ins>](#41-specialagent) into the application in `MyApp.jar`.

#### 2.2.2 <ins>Dynamic Attach</ins>

With [<ins>Dynamic Attach</ins>](#222-dynamic-attach), the application is allowed to start first, afterwhich an agent VM is dynamically attached to the application's PID. This mode requires 2 commands from the command line: the first for the application, and the second for the agent VM.

Dynamically attaching to a Java application involves the use of a running application’s PID, after the application’s startup. The following commands can be used as an example:

1. To obtain the `PID` of the target application:
    ```bash
    jps
    ```

1. To attach to the target `PID`:
   * For jdk1.8
     ```bash
     java -Xbootclasspath/a:$JAVA_HOME/lib/tools.jar -jar opentracing-specialagent-1.6.1.jar <PID>
     ```

   * For jdk9+
     ```bash
     java -jar opentracing-specialagent-1.6.1.jar <PID>
     ```

**Note:** Properties that are provided in the command to dynamically attach will be absorbed by the target application. This applies to properties specific to SpecialAgent, such as `-Dsa.log.level=FINER`, as well as other properties such as `-Djava.util.logging.config.file=out.log`.

**Troubleshooting:** If you encounter an exception stating `Unable to open socket file`, make sure the attaching VM is executed with the same permissions as the target VM.

#### 2.2.3 <ins>Static Deferred Attach</ins>

With <ins>Static Deferred Attach</ins>, the application is executed with the `-javaagent` argument, but the agent initialization is deferred until the application is started. This mode requires 1 command from the command line, and is designed specifically for runtimes that have complex initialization lifecycles that may result in extraneously lengthy startup times when attached with [<ins>Static Attach</ins>](#221-static-attach).

##### <ins>Static Deferred Attach</ins> is currently supported for:

1. Spring WebMVC (1.0 to LATEST).
1. Spring Boot (1.0.0.RELEASE to LATEST).

If the above supported application environment is detected, <ins>Static Deferred Attach</ins> is automatically activated.

**To deactivate** <ins>Static Deferred Attach</ins>, specify the following system property on the command line:

```bash
-Dsa.init.defer=false
```

The following command can be used as an example:

```bash
java -javaagent:opentracing-specialagent-1.6.1.jar -Dsa.init.defer=false -jar MySpringBootApp.jar
```

## 3 Configuration

### 3.1 Overview

The [<ins>SpecialAgent</ins>](#41-specialagent) exposes a simple pattern for configuration of [<ins>SpecialAgent</ins>](#41-specialagent), the [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin), as well as [<ins>Tracer Plugins</ins>](#43-tracer-plugin). The configuration pattern is based on system properties, which can be defined on the command-line, in a properties file, or in [@AgentRunner.Config][agentrunner-config] for JUnit tests:

**Configuration Layers**

1. Properties passed on the command-line via `-D${PROPERTY}=...` override same-named properties defined in the subsequent layers.

1. The [@AgentRunner.Config][agentrunner-config] annotation allows one to define log level and re/transformation event logging settings. Properties defined in the `@Config` annotation override same-named properties defined in the subsequent layers.

1. The `-Dsa.config=${PROPERTIES_FILE}` command-line argument can be specified for [<ins>SpecialAgent</ins>](#41-specialagent) to load property names from a `${PROPERTIES_FILE}`. Properties defined in the `${PROPERTIES_FILE}` override same-named properties defined in the subsequent layer.

1. The [<ins>SpecialAgent</ins>](#41-specialagent) has a `default.properties` file that defines default values for properties that need to be defined.

### 3.2 Properties

The following properties are supported by all [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin):

1. Logging:

   * `-Dsa.log.level`

     Set the logging level for <ins>SpecialAgent</ins>. Acceptable values are: `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, or `FINEST`, or any numerical log level value is accepted also. The default logging level is set to `WARNING`.

   * `-Dsa.log.events`

     Set the re/transformation events to be logged: `DISCOVERY`, `IGNORED`, `TRANSFORMATION`, `ERROR`, `COMPLETE`. The property accepts a comma-delimited list of event names. By default, the `ERROR` event is logged (only when run with `AgentRunner`).

   * `-Dsa.log.file`

     Set the logging output file for <ins>SpecialAgent</ins>.

1. Verbose Mode:

   &nbsp;&nbsp;&nbsp;&nbsp;`-Dsa.instrumentation.plugin.*.verbose`<br>
   &nbsp;&nbsp;&nbsp;&nbsp;`-Dsa.instrumentation.plugin.${RULE_NAME_PATTERN}.verbose`

   Sets verbose mode for all plugins (i.e. `*`) or one plugin (i.e. `${RULE_NAME_PATTERN}`). This property can also be set in an `AgentRunner` JUnit test with the `@AgentRunner.Config(verbose=true)` for all tests in a JUnit class, or `@AgentRunner.TestConfig(verbose=true)` for an individual JUnit test method.

   The [Java Concurrent API plugin](https://github.com/opentracing-contrib/java-concurrent) supports verbose mode, which is disabled by default. To enable, set `sa.concurrent.verbose=true`. In non-verbose mode, parent span context is propagating to task execution (if a parent span context exists). In verbose mode, a parent span is always created upon task submission to the executor, and a child span is created when the task is started.

1. Skip fingerprint verification:

   &nbsp;&nbsp;&nbsp;&nbsp;`-Dsa.fingerprint.skip`

   Tells the [<ins>SpecialAgent</ins>](#41-specialagent) to skip the fingerprint verification when linking [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) into class loaders. This option allows one to work around an unexpected fingerprint verification failure, which can happen in complex runtimes that do not contain all class definitions on the class path. It must be noted, however, that if the fingerprint verification is disabled, the [<ins>SpecialAgent</ins>](#41-specialagent) will indiscriminately install all plugins regardless of library version compatibility issues, which may lead to `NoClassDefFoundError`, `IllegalAccessError`, `AbstractMethodError`, `LinkageError`, etc.

### 3.3 Selecting the [<ins>Tracer Plugin</ins>](#43-tracer-plugin)

The [<ins>SpecialAgent</ins>](#41-specialagent) supports OpenTracing-compatible tracers. There are 2 ways to connect a tracer to the [<ins>SpecialAgent</ins>](#41-specialagent) runtime:

1. **Bundled [<ins>Tracer Plugins</ins>](#43-tracer-plugin)**

    The [<ins>SpecialAgent</ins>](#41-specialagent) bundles the following [<ins>Tracer Plugins</ins>](#43-tracer-plugin):

    1. [Jaeger Tracer Plugin](https://github.com/opentracing-contrib/java-opentracing-jaeger-bundle)
    1. [LightStep Tracer Plugin](https://github.com/lightstep/lightstep-tracer-java/tree/master/lightstep-tracer-jre-bundle)
    1. [Wavefront Tracer Plugin](https://github.com/wavefrontHQ/wavefront-opentracing-bundle-java)
    1. [OpenTelemetry Bridge Tracer Plugin](https://github.com/opentracing-contrib/java-opentelemetry-bridge)
    1. [`MockTracer`](https://github.com/opentracing/opentracing-java/blob/master/opentracing-mock/)

    The `-Dsa.tracer=${TRACER_PLUGIN}` property specifies which [<ins>Tracer Plugin</ins>](#43-tracer-plugin) is to be used. The value of `${TRACER_PLUGIN}` is the [<ins>Short Name</ins>](#21221-short-name) of the [<ins>Tracer Plugin</ins>](#43-tracer-plugin), i.e. `jaeger`, `lightstep`, `wavefront`, or `otel`.

1. **External [<ins>Tracer Plugins</ins>](#43-tracer-plugin)**

    The [<ins>SpecialAgent</ins>](#41-specialagent) allows external [<ins>Tracer Plugins</ins>](#43-tracer-plugin) to be attached to the runtime.

    The `-Dsa.tracer=${TRACER_JAR}` property specifies the JAR path of the [<ins>Tracer Plugin</ins>](#43-tracer-plugin) to be used. The `${TRACER_JAR}` must be a JAR that conforms to the [`TracerFactory`](https://github.com/opentracing-contrib/java-tracerresolver#tracer-factory) API of the [TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver) project.

_**NOTE**: If a tracer is not specified with the `-Dsa.tracer=...` property, the [<ins>SpecialAgent</ins>](#41-specialagent) will present a warning in the log that states: `Tracer NOT RESOLVED`._

### 3.4 Disabling [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin)

[<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) bundled with the [<ins>SpecialAgent</ins>](#41-specialagent) are enabled by default.

Multiple properties to <ins>disable</ins> or to <ins>enable</ins> all or individual plugins can be declared via the [Configuration Pattern](#3-configuration). The processing order of the properties is equal to the order of their declaration.

#### 3.4.1 Disabling All Instrumentation Plugins

To <ins>disable</ins> _all **instrumentation** plugins_:

```
sa.instrumentation.plugin.*.disable
```
<sup>The suffix `.disable` is interchangeable with `.enable=false`.</sup>

#### 3.4.2 Disabling (or enabling) One Instrumentation Plugin

To <ins>disable</ins> _an individual **instrumentation** plugin_:

```
sa.instrumentation.plugin.${RULE_NAME_PATTERN}.disable
```
<sup>The suffix `.disable` is interchangeable with `.enable=false`.</sup>

Conversely, to <ins>enable</ins> _an individual **instrumentation** plugin_.

```
sa.instrumentation.plugin.${RULE_NAME_PATTERN}.enable
```
<sup>The suffix `.enable` is interchangeable with `.disable=false`.</sup>

##### Rule Name Pattern

The value of `${RULE_NAME_PATTERN}` represents the Rule Name, as specified in [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins) ("SpecialAgent Rule" column). The `${RULE_NAME_PATTERN}` allows for the use of `*` and `?` characters to match multiple rules simultaneously. For instance:

1. `lettuce:5.?`<br>Matches all <ins>Lettuce</ins> rules, including `lettuce:5.0`, `lettuce:5.1`, and `lettuce:5.2`.
1. `spring:web:*`<br>Matches all <ins>Spring Web</ins> rules, including `spring:web:3` and `spring:web:5`.
1. `spring:web*`<br>Matches all <ins>Spring Web</ins> and <ins>Spring WebMVC</ins> rules, including `spring:web:3`, `spring:web:5`, `spring:webmvc`, `spring:webmvc:3`, `spring:webmvc:4`, and `spring:webmvc:5`.
1. `spring:webmvc`<br>Matches all <ins>Spring WebMVC</ins> rules, including `spring:webmvc`, `spring:webmvc:3`, `spring:webmvc:4`, and `spring:webmvc:5`.

If the _version part_ of the `${RULE_NAME_PATTERN}` does not end with a `*` or `?` character, a `*` will be appended automatically. Therefore:

1. `lettuce:5`<br>Matches all <ins>Lettuce</ins> v5 rules, including `lettuce:5.0`, `lettuce:5.1`, and `lettuce:5.2`.
1. `spring:web`<br>Matches all <ins>Spring Web</ins> rules, including `spring:web:3` and `spring:web:5`.
1. `spring`<br>Matches all <ins>Spring</ins> rules.
1. `spring:w`<br>Does not match any rules.

#### 3.4.3 Disabling `AgentRule`s of an Instrumentation Plugin

To disable _an individual `AgentRule` of an **instrumentation** plugin_:

```
sa.instrumentation.plugin.${PLUGIN_NAME}#${AGENT_RULE_SIMPLE_CLASS_NAME}.disable
```
<sup>The suffix `.disable` is interchangeable with `.enable=false`.</sup>

The value of `${AGENT_RULE_SIMPLE_CLASS_NAME}` is the simple class name of the `AgentRule` subclass that is to be disabled.

### 3.5 Disabling [<ins>Tracer Plugins</ins>](#43-tracer-plugin)

The [<ins>SpecialAgent</ins>](#41-specialagent) has all of its [<ins>Tracer Plugins</ins>](#43-tracer-plugin) enabled by default, and allows them to be disabled.

To disable _all **tracer** plugins_:

```
sa.tracer.plugins.disable
```
<sup>The suffix `.disable` is interchangeable with `.enable=false`.</sup>

To disable _an individual **tracer** plugin_:

```
sa.tracer.plugin.${SHORT_NAME}.disable
```
<sup>The suffix `.disable` is interchangeable with `.enable=false`.</sup>

The value of `${SHORT_NAME}` is the [<ins>Short Name</ins>](#21221-short-name) of the plugin, such as `jaeger`, `lightstep`, `wavefront`, or `otel`.

### 3.6 Including Custom Instrumentation Plugins

Custom plugins and `AgentRule`s can be implemented by following the [SpecialAgent Rule API](https://github.com/opentracing-contrib/java-specialagent/tree/master/opentracing-specialagent-api). JARs containing custom plugins and `AgentRule`s can be loaded by [<ins>SpecialAgent</ins>](#41-specialagent) with:

```bash
-Dsa.instrumentation.plugin.include=<JARs>
```

Here, `<JARs>` refers to a pathSeparator-delimited (`:` for \*NIX, `;` for Windows) string of JARs containing the custom rules.

### 3.7 Rewritable Tracer

The [<ins>Rewritable Tracer</ins>](#37-rewritable-tracer) allows one to rewrite data in the spans created by [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins) without having to modify the source code.

The [<ins>Rewritable Tracer</ins>](#37-rewritable-tracer) is a rules engine that is configured via JSON files [that conform to a specification][rewrite].

For example:

* The following JSON defines a rule for all [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins) to drop all **tag**s in spans matching `key` literal `http.url` and `value` regex `.*secret.*`.

  ```json
  {
    "*": [
      {
        "input": {
          "type": "tag",
          "key": "http.url",
          "value": ".*secret.*"
        }
      }
    ]
  }
  ```

* The following JSON defines a rule for the `jedis` [<ins>Instrumentation Plugin</ins>](#61-instrumentation-plugins) to rewrite all **log**s matching `key` literal `http.method` as a **tag**.

```json
{
  "jedis": [
    {
      "input": {
        "type": "log",
        "key": "http.method",
      },
      "output": {
        "type": "tag"
      }
    }
  ]
}
```

For a configuration spec and other use-case examples, please refer to the [`rewrite` plugin][rewrite].

## 4 Definitions

The following terms are used throughout this documentation.

#### 4.1 <ins>SpecialAgent</ins>

The [<ins>SpecialAgent</ins>](#41-specialagent) is software that attaches to Java applications, and automatically instruments 3rd-party libraries integrated in the application. The [<ins>SpecialAgent</ins>](#41-specialagent) uses the OpenTracing API for [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins) that instrument 3rd-party libraries, as well as [<ins>Tracer Plugins</ins>](#62-tracer-plugins) that implement OpenTracing tracer service providers. Both the [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins), as well as the [<ins>Tracer Plugins</ins>](#62-tracer-plugins) are open-source, and are developed and supported by the OpenTracing community.

The [<ins>SpecialAgent</ins>](#41-specialagent) supports Oracle Java and OpenJDK.

#### 4.2 [<ins>Tracer</ins>](#42-tracer)

Service provider of the OpenTracing standard, providing an implementation of the `io.opentracing.Tracer` interface.

Examples:
* [Jaeger Tracer][jaeger]
* [LightStep Tracer][lightstep]
* [Wavefront Tracer][wavefront]

<sub>_[<ins>Tracers</ins>](#42-tracer) **are not** coupled to the [<ins>SpecialAgent</ins>](#41-specialagent)._</sub>

#### 4.3 [<ins>Tracer Plugin</ins>](#43-tracer-plugin)

A bridge providing automatic discovery of [<ins>Tracers</ins>](#42-tracer) in a runtime instrumented with the OpenTracing API. This bridge implements the `TracerFactory` interface of [TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver/blob/master/opentracing-tracerresolver/), and is distributed as a single "fat JAR" that can be conveniently added to the classpath of a Java process.

<sub>_[<ins>Tracer Plugins</ins>](#43-tracer-plugin) **are not** coupled to the [<ins>SpecialAgent</ins>](#41-specialagent)._</sub>

#### 4.4 [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin)

An OpenTracing Instrumentation project that exist as individual repositories under [opentracing-contrib][opentracing-contrib].

Examples:
* [`opentracing-contrib/java-okhttp`][java-okhttp]
* [`opentracing-contrib/java-jdbc`][java-jdbc]
* [`opentracing-contrib/java-jms`][java-jms]

<sub>_[<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) **are not** coupled to the [<ins>SpecialAgent</ins>](#41-specialagent)._</sub>

#### 4.5 [<ins>Instrumentation Rule</ins>](#45-instrumentation-rule)

A submodule of the [<ins>SpecialAgent</ins>](#41-specialagent) that implements the auto-instrumentation rules for [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) via the [`opentracing-specialagent-api`][api].

Examples:
* [`rule/okhttp`][okhttp]
* [`rule/jdbc`][jdbc]
* [`rule/jms`][jms]

<sub>_[<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) **are** coupled to the [<ins>SpecialAgent</ins>](#41-specialagent)._</sub>

## 5 Objectives

### 5.1 Goals

1. The [<ins>SpecialAgent</ins>](#41-specialagent) must allow any [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) available in [opentracing-contrib][opentracing-contrib] to be automatically installable in applications that utilize a 3rd-party library for which an [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) exists.
1. The [<ins>SpecialAgent</ins>](#41-specialagent) must automatically install the [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) for each 3rd-party library for which a module exists, regardless in which class loader the 3rd-party library is loaded.
1. The [<ins>SpecialAgent</ins>](#41-specialagent) must not adversely affect the runtime stability of the application on which it is intended to be used. This goal applies only to the code in the [<ins>SpecialAgent</ins>](#41-specialagent), and cannot apply to the code of the [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) made available in [opentracing-contrib][opentracing-contrib].
1. The [<ins>SpecialAgent</ins>](#41-specialagent) must [<ins>Static Attach</ins>](#221-static-attach) and [<ins>Dynamic Attach</ins>](#222-dynamic-attach) to applications running on JVM versions 1.7, 1.8, 9, and 11.
1. The [<ins>SpecialAgent</ins>](#41-specialagent) must implement a lightweight test methodology that can be easily applied to a module that implements instrumentation for a 3rd-party library. This test must simulate:
   1. Launch the test in a process simulating the `-javaagent` vm argument that points to the [<ins>SpecialAgent</ins>](#41-specialagent) (in order to test auto-instrumentation functionality).
   1. Elevate the test code to be executed from a custom class loader that is disconnected from the system class loader (in order to test bytecode injection into an isolated class loader that cannot resolve classes on the system classpath).
   1. Allow tests to specify their own `Tracer` instances via `GlobalTracer`, or initialize a `MockTracer` if no instance is specified. The test must provide a reference to the `Tracer` instance in the test method for assertions with JUnit.
1. The [<ins>SpecialAgent</ins>](#41-specialagent) must provide a means by which [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) can be configured before use on a target application.

### 5.2 Non-Goals

1. The [<ins>SpecialAgent</ins>](#41-specialagent) is not designed to modify application code, beyond the installation of [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin). For example, there is no facility for dynamically augmenting arbitrary code.

## 6 Supported Plugins

### 6.1 [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin)

Intrinsically, the [<ins>SpecialAgent</ins>](#41-specialagent) includes support for the instrumentation of the following 3rd-party libraries. Each row refers to an [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin), the [<ins>Instrumentation Rule</ins>](#45-instrumentation-rule), and the minimum and maximum version tested by the build.

The  following plugins have [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) implemented.
Direction for development of [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) is available in the [`opentracing-specialagent-api`][api] module.

| OpenTracing Plugin<br/><sup>(link to <ins>plugin</ins> implementation)</sup> | SpecialAgent Rule<br/><sup>(link to <ins>rule</ins> implementation)</sup> | Min Version<br/><sup>(min supported)</sup> | Max Version<br/><sup>(max supported)</sup> |
|:-|:-|:-:|:-:|
| [Akka Actor](https://github.com/opentracing-contrib/java-akka) | [`akka:actor`][akka-actor] | 2.5.0 | LATEST |
| Akka Http | [`akka:http`][akka-http] | 10.1.0 | LATEST |
| [Apache Camel](https://github.com/apache/camel/tree/master/components/camel-opentracing) | [`camel`][camel] | 2.24.0 | 2.24.2 |
| Apache CXF | [`cxf`][cxf] | 3.3.3 | LATEST |
| Apache Dubbo | [`dubbo:2.6`][dubbo-2.6] | 2.6.2 | 2.6.7 |
| | [`dubbo:2.7`][dubbo-2.7] | 2.7.1 | LATEST |
| [Apache HttpClient](https://github.com/opentracing-contrib/java-apache-httpclient) | [`apache:httpclient`][apache-httpclient] | 4.4 | LATEST |
| [Async Http Client](https://github.com/opentracing-contrib/java-asynchttpclient) | [`asynchttpclient`][asynchttpclient] | 2.7.0 | LATEST |
| [AWS SDK](https://github.com/opentracing-contrib/java-aws-sdk) | [`aws:sdk:1`][aws-sdk-1] | 1.11.79 | LATEST |
| | [`aws:sdk:2`][aws-sdk-2] | 2.1.4 | **FIXME** |
| [Cassandra Driver](https://github.com/opentracing-contrib/java-cassandra-driver) | [`cassandra:driver:3`][cassandra-driver-3] | 3.0.0 | 3.7.2 |
| | [`cassandra:driver:4`][cassandra-driver-4] | 4.0.0 | LATEST |
| Couchbase Client | [`couchbase-client`][couchbase-client] | 2.7.3 | 2.7.13 |
| Dynamic<br>&nbsp; | [`dynamic`<br><sup>(configurable)</sup>][dynamic] | **\***<br>&nbsp; | **\***<br>&nbsp; |
| [Elasticsearch Client<br>&nbsp;](https://github.com/opentracing-contrib/java-elasticsearch-client) | [`elasticsearch:client-transport`][elasticsearch-7-transport-client]<br>[`elasticsearch:client-rest`][elasticsearch-7-rest-client] | 6.4.0<br>6.4.0 | LATEST<br>6.8.7 |
| [Feign](https://github.com/OpenFeign/feign-opentracing/tree/master/feign-opentracing) | [`feign`][feign] | 9.0.0 | LATEST |
| Google Http Client | [`google-http-client`][google-http-client] | 1.19.0 | LATEST |
| [Grizzly AsyncHttpClient](https://github.com/opentracing-contrib/java-grizzly-ahc) | [`grizzly:ahc`][grizzly-ahc] | 1.15 | LATEST |
| [Grizzly HTTP Server](https://github.com/opentracing-contrib/java-grizzly-http-server) | [`grizzly:http-server`][grizzly-http-server] | 2.3.35 | LATEST |
| [GRPC](https://github.com/opentracing-contrib/java-grpc) | [`grpc`][grpc] | 1.6.1 | LATEST |
| [Hazelcast](https://github.com/opentracing-contrib/opentracing-hazelcast) | [`hazelcast`][hazelcast] | 3.12 | 3.12.6 |
| [Java Concurrent API \[`java.util.concurrent`\]](https://github.com/opentracing-contrib/java-concurrent) | [`concurrent`][concurrent] | 1.5 | 11 |
| [Java JDBC API \[`java.sql`\]][java-jdbc]<br>&nbsp; | [`jdbc`<br><sup>(configurable)</sup>][jdbc] | 3.1<br>&nbsp; | 4.3<br>&nbsp; |
| [Java JMS API \[`javax.jms`\]][java-jms] | [`jms`][jms] | 1.1-rev-1 | LATEST |
| | [`jms`][jms] | 2.0.1 | LATEST |
| [Java Servlet API \[`javax.servlet`\]](https://github.com/opentracing-contrib/java-web-servlet-filter)<br>&nbsp; | [`servlet`<br><sup>(configurable)</sup>][servlet] | 2.3<br>&nbsp; | 3.1<br>&nbsp; |
| &nbsp;&nbsp;&nbsp;&nbsp;Jetty | | 7.6.21.v20160908 | 9.2.15.v20160210 |
| &nbsp;&nbsp;&nbsp;&nbsp;Tomcat | | 7.0.65 | 9.0.27 |
| Java Thread [`java.lang.Thread`] | [`thread`][thread] | 1.0 | 11 |
| HttpURLConnection [`java.net.HttpURLConnection`] | [`httpurlconnection`][httpurlconnection] | 1.1 | 11 |
| [JAX-RS Client](https://github.com/opentracing-contrib/java-jaxrs) | [`jax-rs`][jaxrs] | 2.0 | LATEST |
| [Jedis Client](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-jedis) | [`jedis`][jedis] | 2.7.0 | 3.1.0 |
| [Kafka Client](https://github.com/opentracing-contrib/java-kafka-client) | [`kafka:client`][kafka-client] | 1.1.0 | LATEST |
| [Kafka Streams](https://github.com/opentracing-contrib/java-kafka-client) | [`kafka:streams`][kafka-streams] | 1.1.0 | LATEST |
| [Lettuce Client](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-lettuce) | [`lettuce:5.0`][lettuce-5.0] | 5.0.0.RELEASE | 5.0.5.RELEASE |
| | [`lettuce:5.1`][lettuce-5.1] | 5.1.0.RELEASE | 5.1.8.RELEASE |
| | [`lettuce:5.2`][lettuce-5.2] | 5.2.0.RELEASE | LATEST |
| [MongoDB Driver](https://github.com/opentracing-contrib/java-mongo-driver) | [`mongo:driver`][mongo-driver] | 3.9.0 | LATEST |
| Mule 4 Artifact Module | [`mule:artifact-module:4`][mule-4-module-artifact] | 4.2.2 | LATEST |
| Mule 4 Http Service | [`mule:http-service:4`][mule-4-http-service] | 1.4.7 | LATEST |
| Mule 4 Core | [`mule:core:4`][mule-4-core] | 4.2.2 | LATEST |
| [Neo4j Driver](https://github.com/opentracing-contrib/java-neo4j-driver) | [`neo4j:driver`][neo4j-driver] | 4.0.0 | LATEST |
| Netty | [`netty`][netty] | 4.1.0 | 4.1.46.Final |
| [OkHttp][java-okhttp] | [`okhttp`][okhttp] | 3.5.0 | LATEST |
| Play Framework | [`play`][play] | 2.6.0 | LATEST |
| Play WS | [`play:ws`][play-ws] | 2.0.0 | **FIXME** |
| Pulsar Client | [`pulsar:client`][pulsar-client] | 2.2.0 | **FIXME** |
| Pulsar Functions <br>&nbsp; | [`pulsar-functions`<br><sup>(configurable)</sup>][pulsar-functions] | 2.2.0<br>&nbsp; | **FIXME**<br>&nbsp; |
| [RabbitMQ Client](https://github.com/opentracing-contrib/java-rabbitmq-client) | [`rabbitmq:client`][rabbitmq-client] | 5.0.0 | LATEST |
| [Reactor](https://github.com/opentracing-contrib/java-reactor) | [`reactor`][reactor] | 3.2.3.RELEASE | LATEST |
| [Redisson](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-redisson) | [`redisson`][redisson] | 3.11.0 | 3.11.5 |
| [RxJava](https://github.com/opentracing-contrib/java-rxjava) | [`rxjava:2`][rxjava-2] | 2.1.0 | LATEST |
| | [`rxjava:3`][rxjava-3] | 3.0.0 | LATEST |
| [Spring JMS](https://github.com/opentracing-contrib/java-jms/tree/master/opentracing-jms-spring) | [`spring:jms`][spring-jms] | 5.0.0.RELEASE | LATEST |
| [Spring Kafka](https://github.com/opentracing-contrib/java-kafka-client/tree/master/opentracing-kafka-spring) | [`spring:kafka`][spring-kafka] | 2.2.0.RELEASE | LATEST |
| [Spring Messaging](https://github.com/opentracing-contrib/java-spring-messaging) | [`spring:messaging`][spring-messaging] | 5.1.0.RELEASE | LATEST |
| [Spring RabbitMQ](https://github.com/opentracing-contrib/java-spring-rabbitmq) | [`spring:rabbitmq`][spring-rabbitmq] | 2.0.0.RELEASE | LATEST |
| [Spring WebFlux](https://github.com/opentracing-contrib/java-spring-web) | [`spring:webflux`][spring-webflux] | 5.1.0.RELEASE | LATEST |
| [Spring Boot WebSocket STOMP](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-websocket-starter) | [`spring:websocket`][spring-websocket] | 2.1.0.RELEASE | LATEST |
| [Spring \[`@Async` and `@Scheduled`\]](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-core) | [`spring:scheduling`][spring-scheduling] | 5.0.0.RELEASE | LATEST |
| [Spring Web](https://github.com/opentracing-contrib/java-spring-web) | [`spring:web:3`][spring-web-3] | 3.0.3.RELEASE | 3.2.18.RELEASE |
| | [`spring:web:4.0`][spring-web-4.0] | 4.0.0.RELEASE | 4.0.9.RELEASE |
| | [`spring:web:4.x`][spring-web-4] | 4.1.0.RELEASE | 4.3.25.RELEASE |
| | [`spring:web:5`][spring-web-5] | 5.0.0.RELEASE | LATEST |
| [Spring Web MVC](https://github.com/opentracing-contrib/java-spring-web) | [`spring:webmvc:3`][spring-webmvc-3] | 3.0.2.RELEASE | 3.2.18.RELEASE |
| | [`spring:webmvc:4`][spring-webmvc-4] | 4.0.0.RELEASE | 4.3.25.RELEASE |
| | [`spring:webmvc:5`][spring-webmvc-5] | 5.0.0.RELEASE | LATEST |
| [Spymemcached](https://github.com/opentracing-contrib/java-memcached-client/tree/master/opentracing-spymemcached) | [`spymemcached`][spymemcached] | 2.11.0 | LATEST |
| [Thrift](https://github.com/opentracing-contrib/java-thrift) | [`thrift`][thrift] | 0.10.0 | 0.13.0 |
| [Zuul](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-zuul-starter) | [`zuul`][zuul] | 1.0.0 | 2.1.1 |

### 6.2 [<ins>Tracer Plugins</ins>](#43-tracer-plugin)

Intrinsically, the [<ins>SpecialAgent</ins>](#41-specialagent) includes support for the following [<ins>Tracer Plugins</ins>](#43-tracer-plugin). A demo can be referenced [here](https://github.com/opentracing-contrib/java-specialagent-demo).

| OpenTracing Plugin<br/><sup>(link to <ins>plugin</ins> implementation)</sup> | [Short Name](#21221-short-name)<br/><sup>(`-Dsa.tracer=<short_name>`)</sup> |
|:-|:-|
| [Jaeger Tracer Plugin](https://github.com/opentracing-contrib/java-opentracing-jaeger-bundle)<br/><sup>[(configuration reference)](https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md#configuration-via-environment)</sup> | `jaeger`<br/>&nbsp; |
| [LightStep Tracer Plugin](https://github.com/lightstep/lightstep-tracer-java/tree/master/lightstep-tracer-jre-bundle)<br/><sup>[(configuration reference)](https://docs.lightstep.com/docs/create-projects-for-your-environments)</sup> | `lightstep`<br/>&nbsp; |
| [Wavefront Tracer Plugin](https://github.com/wavefrontHQ/wavefront-opentracing-bundle-java)<br/><sup>[(configuration reference)](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#quickstart)</sup> | `wavefront`<br/>&nbsp; |
| [OpenTelemetry Bridge Tracer Plugin](https://github.com/opentracing-contrib/java-opentelemetry-bridge)<br/><sup><ins>(configuration reference)</ins></sup> | `otel`<br/>&nbsp; |
| [`MockTracer`](https://github.com/opentracing/opentracing-java/blob/master/opentracing-mock/)| `mock` |

### 6.3 [<ins>Instrumented libraries by existing rules</ins>](#46-instrumented-libs)

The following libraries are instrumented by existing [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule).

| OpenTracing Plugin<br/><sup>(link to <ins>plugin</ins> implementation)</sup> | SpecialAgent Rule<br/><sup>(link to <ins>rule</ins> implementation)</sup> | Min Version<br/><sup>(min supported)</sup> | Max Version<br/><sup>(max supported)</sup> |
|:-|:-|:-:|:-:|
| [Hystrix](https://github.com/OpenFeign/feign-opentracing/tree/master/feign-hystrix-opentracing) | [`concurrent`][concurrent] | 1.5 | 11 |
| [JDBI](https://github.com/opentracing-contrib/java-jdbi)<br>&nbsp; | [`jdbc`<br><sup>(configurable)</sup>][jdbc] | \*<br>&nbsp; | \*<br>&nbsp; |
| Ratpack | [`netty`][netty] | 1.4.0 | LATEST |
| Solr Client | [`apache:httpclient`][apache-httpclient] | 4.0.0 | LATEST |
| SparkJava | [`javax.servlet`][servlet] | 2.2 | LATEST |
| Spring Data<br>&nbsp; | [`jdbc`<br><sup>(configurable)</sup>][jdbc] | \*<br>&nbsp; | \*<br>&nbsp; |

**TBD**

1. [Spring Cloud](https://github.com/opentracing-contrib/java-spring-cloud)
1. [Twilio][apache-httpclient]

## 7 Credits

Thank you to the following contributors for developing instrumentation plugins:

* [Sergei Malafeev](https://github.com/malafeev)
* [Jose Montoya](https://github.com/jam01)
* [Przemyslaw Maciolek](https://github.com/pmaciolek)
* [Jianshao Wu](https://github.com/jianshaow)
* [Gregor Zeitlinger](https://github.com/zeitlinger)

Thank you to the following contributors for developing tracer plugins:

* [Carlos Alberto Cortez](https://github.com/carlosalberto)
* [Han Zhang](https://github.com/hanwavefront)

Thank you to the following developers for filing issues and helping us fix them:

* [Louis-Etienne](https://github.com/ledor473)
* [Marcos Trejo Munguia](https://github.com/mtrejo)
* [@kaushikdeb](https://github.com/kaushikdeb)
* [@deepakgoenka](https://github.com/deepakgoenka)
* [Prometheus](https://github.com/etsangsplk)
* [Randall Theobald](https://github.com/randallt)

Thank you to the following individuals for all other general contributions to the codebase:

* [Daniel Rodriguez Hernandez](https://github.com/drodriguezhdez)
* [qudongfang](https://github.com/qudongfang)
* [Pontus Rydin](https://github.com/prydin)

Finally, thanks for all of the feedback! Please share your comments [as an issue](https://github.com/opentracing-contrib/java-specialagent/issues)!

## 8 Contributing

Pull requests are welcome. For major changes, please [open an issue](https://github.com/opentracing-contrib/java-specialagent/issues) first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## 9 License

This project is licensed under the Apache 2 License - see the [LICENSE.txt](LICENSE.txt) file for details.

[akka-actor]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/akka-actor
[akka-http]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/akka-http
[apache-httpclient]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/apache-httpclient
[asynchttpclient]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/asynchttpclient
[aws-sdk-1]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/aws-sdk-1
[aws-sdk-2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/aws-sdk-2
[camel]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/camel
[cxf]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/cxf
[cassandra-driver-3]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/cassandra-driver-3
[cassandra-driver-4]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/cassandra-driver-4
[concurrent]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/concurrent
[couchbase-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/couchbase-client
[dubbo-2.6]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/dubbo-2.6
[dubbo-2.7]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/dubbo-2.7
[dynamic]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/dynamic
[elasticsearch-7-rest-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/elasticsearch-7-rest-client
[elasticsearch-7-transport-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/elasticsearch-7-transport-client
[feign]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/feign
[google-http-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/google-http-client
[grizzly-ahc]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/grizzly-ahc
[grizzly-http-server]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/grizzly-http-server
[grpc]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/grpc
[hazelcast]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/hazelcast
[httpurlconnection]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/httpurlconnection
[jaxrs]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/jaxrs
[jdbc]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/jdbc
[jedis]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/jedis
[jms]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/jms
[kafka-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/kafka-client
[kafka-streams]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/kafka-streams
[lettuce-5.0]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/lettuce-5.0
[lettuce-5.1]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/lettuce-5.1
[lettuce-5.2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/lettuce-5.2
[mongo-driver]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/mongo-driver
[mule-4-core]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/mule-4-core
[mule-4-http-service]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/mule-4-http-service
[mule-4-module-artifact]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/mule-4-module-artifact
[neo4j-driver]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/neo4j-driver
[netty]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/netty
[okhttp]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/okhttp
[play-ws]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/play-ws
[play]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/play
[pulsar-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/pulsar-client
[pulsar-functions]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/pulsar-functions
[rabbitmq-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/rabbitmq-client
[reactor]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/reactor
[redisson]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/redisson
[rewrite]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rewrite
[rxjava-2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/rxjava-2
[rxjava-3]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/rxjava-3
[servlet]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/servlet
[spring-jms]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-jms
[spring-kafka]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-kafka
[spring-messaging]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-messaging
[spring-rabbitmq]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-rabbitmq
[spring-scheduling]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-scheduling
[spring-web-3]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-web-3
[spring-web-4.0]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-web-4.0
[spring-web-4]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-web-4
[spring-web-5]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-web-5
[spring-webflux]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-webflux
[spring-webmvc-3]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-webmvc-3
[spring-webmvc-4]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-webmvc-4
[spring-webmvc-5]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-webmvc-5
[spring-websocket]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spring-websocket
[spymemcached]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/spymemcached
[thread]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/thread
[thrift]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/thrift
[zuul]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/zuul

[jaeger]: https://github.com/jaegertracing/jaeger
[lightstep]: https://github.com/lightstep/lightstep-tracer-java
[wavefront]: https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java

[agentrunner-config]: https://github.com/opentracing-contrib/java-specialagent/tree/master/opentracing-specialagent-api#51-configuring-agentrunner
[api]: https://github.com/opentracing-contrib/java-specialagent/tree/master/opentracing-specialagent-api
[java-jdbc]: https://github.com/opentracing-contrib/java-jdbc
[java-jms]: https://github.com/opentracing-contrib/java-jms
[java-okhttp]: https://github.com/opentracing-contrib/java-okhttp
[opentracing-contrib]: https://github.com/opentracing-contrib/
[specialagent-pom]: https://github.com/opentracing-contrib/java-specialagent/blob/master/pom.xml
[travis]: https://travis-ci.org/opentracing-contrib/java-specialagent

[main-release]: https://repo1.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.6.1/opentracing-specialagent-1.6.1.jar
[main-snapshot]: https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/specialagent/opentracing-specialagent/1.6.2-SNAPSHOT