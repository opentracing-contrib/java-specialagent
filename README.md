# Java Agent for OpenTracing

> Automatically instruments 3rd-party libraries in Java applications

[![Build Status](https://travis-ci.org/opentracing-contrib/java-specialagent.svg?branch=master)](https://travis-ci.org/opentracing-contrib/java-specialagent)
[![Coverage Status](https://coveralls.io/repos/github/opentracing-contrib/java-specialagent/badge.svg?branch=master)](https://coveralls.io/github/opentracing-contrib/java-specialagent?branch=master)
[![Javadocs](https://www.javadoc.io/badge/io.opentracing.contrib.specialagent/opentracing-specialagent.svg)](https://www.javadoc.io/doc/io.opentracing.contrib.specialagent/opentracing-specialagent)
[![Released Version](https://img.shields.io/maven-central/v/io.opentracing.contrib.specialagent/specialagent.svg)](https://mvnrepository.com/artifact/io.opentracing.contrib.specialagent/opentracing-specialagent)

<sub>_Note: The coverage statistic is not correct, because Jacoco cannot properly instrument code that is instrumented at the bytecode level._</sub>

## What is SpecialAgent?

<ins>SpecialAgent</ins> automatically instruments 3rd-party libraries in Java applications. The architecture of <ins>SpecialAgent</ins> was designed to involve contributions from the community, whereby its platform integrates and automates OpenTracing <ins>Instrumentation Plugins</ins> written by individual contributors. In addition to <ins>Instrumentation Plugins</ins>, the <ins>SpecialAgent</ins> also supports <ins>Tracer Plugins</ins>, which connect an instrumented runtime to OpenTracing-compliant tracer vendors, such as LightStep, Wavefront, or Jaeger. Both the <ins>Instrumentation Plugins</ins> and the <ins>Tracer Plugins</ins> are decoupled from <ins>SpecialAgent</ins> -- i.e. neither kinds of plugins need to know anything about <ins>SpecialAgent</ins>. At its core, the <ins>SpecialAgent</ins> is itself nothing more than an engine that abstracts the functionality for automatic installation of <ins>Instrumentation Plugins</ins>, and then connecting them to <ins>Tracer Plugins</ins>. A benefit of this approach is that the <ins>SpecialAgent</ins> intrinsically embodies and encourages community involvement.

In addition to its engine, the <ins>SpecialAgent</ins> packages a set of pre-supported [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins) and [<ins>Tracer Plugins</ins>](#62-tracer-plugins).

## Table of Contents

<samp>&nbsp;&nbsp;</samp>1 [Introduction](#1-introduction)<br>
<samp>&nbsp;&nbsp;</samp>2 [Quick Start](#2-quick-start)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1 [Installation](#21-installation)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.1 [In Application](#211-in-application)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.1.1 [Stable](#2111-stable)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.1.2 [Development](#2112-development)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.2 [For Development](#212-for-development)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.1.2.1 [Building](#2121-building)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.2 [Usage](#22-usage)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.2.1 [<ins>Static Attach</ins>](#221-static-attach)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.2.2 [<ins>Dynamic Attach</ins>](#222-dynamic-attach)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>2.2.3 [Static Deferred Attach](#223-static-deferred-attach)<br>
<samp>&nbsp;&nbsp;</samp>3 [Configuration](#3-configuration)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.1 [Overview](#31-overview)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.2 [Properties](#32-properties)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.3 [Selecting the <ins>Tracer Plugin</ins>](#33-selecting-the-tracer-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.4 [Disabling <ins>Instrumentation Plugins</ins>](#34-disabling-instrumentation-plugins)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.4.1 [Disabling All Instrumentation Plugins](#343-disabling-agentrules-of-an-instrumentation-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.4.2 [Disabling One Instrumentation Plugin](#342-disabling-one-instrumentation-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.4.3 [Disabling `AgentRule`s of an Instrumentation Plugin](#343-disabling-agentrules-of-an-instrumentation-plugin)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>3.5 [Disabling <ins>Tracer Plugins</ins>](#35-disabling-tracer-plugins)<br>
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

When [<ins>SpecialAgent</ins>](#41-specialagent) attaches to an application, either statically or dynamically, it will automatically load the [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) explicitly specified as dependencies in its POM ([Project Object Model][pom]).

Any exception that occurs during the execution of the bootstrap process will not adversely affect the stability of the target application. It is, however, possible that the [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) code may result in exceptions that are not properly handled, and could destabilize the target application.

### 2.1 Installation

The [<ins>SpecialAgent</ins>](#41-specialagent) has 2 artifacts: main and test. These artifacts are built by Maven, and can be obtained by cloning this repository and following the [Building](#2121-building) instructions, or downloading directly from Maven's Central Repository.

#### 2.1.1 In Application

The [<ins>SpecialAgent</ins>](#41-specialagent) is contained in a single JAR file. This JAR file is the main artifact that is built by Maven, and contains within it the [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) from the [opentracing-contrib][opentracing-contrib] organization for which [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) have been implemented.

To use the [<ins>SpecialAgent</ins>](#41-specialagent) on an application, please download the [stable](#2111-stable) or [development](#2112-development) artifact.

This JAR can then be specified with the `-javaagent:$SPECIAL_AGENT_JAR` vm argument for [<ins>Static Attach</ins>](#221-static-attach) and [Static Deferred Attach](#223-static-deferred-attach) to an application. This JAR can also be executed in standalone fashion, with an argument representing the PID of a target process to which it should [<ins>dynamically attach</ins>](#222-dynamic-attach). Please refer to [Usage](#usage) section for usage instructions.

##### 2.1.1.1 Stable

The latest stable release is: [1.4.2][main-release].

```bash
wget -O opentracing-specialagent-1.4.2.jar "http://central.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.2/opentracing-specialagent-1.4.2.jar"
```

##### 2.1.1.2 Development

The latest development release is: <ins>1.4.3-SNAPSHOT</ins>.
```bash
wget -O opentracing-specialagent-1.4.3-SNAPSHOT.jar "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.opentracing.contrib.specialagent&a=opentracing-specialagent&v=LATEST"
```

**Note**: Sometimes the web service call to retrieve the latest SNAPSHOT build fails to deliver the correct download. In order to work around this issue, please consider using the following command:
```bash
wget -O opentracing-specialagent-1.4.3-SNAPSHOT.jar $(curl -s https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.3-SNAPSHOT/ | grep '".*\d\.jar"' | tail -1 | awk -F\" '{print $2}')
```

#### 2.1.2 For Development

For development of [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin), import the `opentracing-specialagent-api` and `test-jar` of the `opentracing-specialagent`.

```xml
<dependency>
  <groupId>io.opentracing.contrib.specialagent</groupId>
  <artifactId>opentracing-specialagent-api</artifactId>
  <version>1.4.2</version> <!--version>1.4.3-SNAPSHOT<version-->
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>io.opentracing.contrib.specialagent</groupId>
  <artifactId>opentracing-specialagent</artifactId>
  <version>1.4.2</version> <!--version>1.4.3-SNAPSHOT<version-->
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

This is the test artifact that contains within it the `AgentRunner`, which is a JUnit runner class provided for testing of the ByteBuddy auto-instrumentation rules. This JAR does not contain within it any [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) themselves, and is only intended to be applied to the test phase of the build lifecycle of a single plugin for an [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) implementation. For direction with the `AgentRunner`, please refer to the [`opentracing-specialagent-api`][api] module.

##### 2.1.2.1 Building

_**Prerequisite**: The [<ins>SpecialAgent</ins>](#41-specialagent) requires [Oracle Java](https://www.oracle.com/technetwork/java/javase/downloads/) to build. Thought the [<ins>SpecialAgent</ins>](#41-specialagent) supports OpenJDK for general application use, it only supports Oracle Java for building and testing._

The [<ins>SpecialAgent</ins>](#41-specialagent) is built in 2 passes that utilize different profiles:

1. The `default` profile is used for development of [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule). It builds and runs tests for each rule, but _does not bundle the rules_ into [`opentracing-specialagent-1.4.2.jar`][main-release]

    To run this profile:
    ```bash
    mvn clean install
    ```

1. The `assemble` profile is used to bundle the [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) into [`opentracing-specialagent-1.4.2.jar`][main-release]. It builds each rule, but _does not run tests._ Once the build with the `assemble` profile is finished, the [`opentracing-specialagent-1.4.2.jar`][main-release] will contain the built rules inside it.

    _**Note**: If you do not run this step, the [`opentracing-specialagent-1.4.2.jar`][main-release] from the previous step will not contain any [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) within it!_

    _**Note**: It is important to **not** run Maven's `clean` lifecycle when executing the `assemble` profile._

    To run this profile:
    ```bash
    mvn -Dassemble package
    ```

* For a one-line build command to build [<ins>SpecialAgent</ins>](#41-specialagent), its rules, run all tests, and create the `assemble` package:

    ```bash
    mvn clean install && mvn -Dassemble package
    ```

### 2.2 Usage

The [<ins>SpecialAgent</ins>](#41-specialagent) uses [Java’s Instrumentation mechanism](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html) to transform the behavior of a target application. The entrypoint into the target application is performed via Java’s Agent convention. [<ins>SpecialAgent</ins>](#41-specialagent) supports both [<ins>Static Attach</ins>](#221-static-attach) and [<ins>Dynamic Attach</ins>](#222-dynamic-attach).

#### 2.2.1 <ins>Static Attach</ins>

Statically attaching to a Java application involves the use of the `-javaagent` vm argument at the time of startup of the target Java application. The following command can be used as an example:

```bash
java -javaagent:opentracing-specialagent-1.4.2.jar -jar MyApp.jar
```

This command statically attaches [<ins>SpecialAgent</ins>](#41-specialagent) into the application in `myapp.jar`.

#### 2.2.2 <ins>Dynamic Attach</ins>

Dynamically attaching to a Java application involves the use of a running application’s PID, after the application’s startup. The following commands can be used as an example:

1. Call this to obtain the `PID` of the target application:
    ```bash
    jps
    ```

1. For jdk1.8
    ```bash
    java -Xbootclasspath/a:$JAVA_HOME/lib/tools.jar -jar opentracing-specialagent-1.4.2.jar <PID>
    ```

1. For jdk9+
    ```bash
    java -jar opentracing-specialagent-1.4.2.jar <PID>
    ```

**Note:** If you encounter an exception stating `Unable to open socket file`, make sure the attaching VM is executed with the same permissions as the target VM.

#### 2.2.3 <ins>Static Deferred Attach</ins>

With [<ins>Static Attach</ins>](#221-static-attach), the application is executed with the `-javaagent` argument, and the agent initialization occurs before the application is started. This mode requires 1 command from the command line.

With [<ins>Dynamic Attach</ins>](#222-dynamic-attach), the application is allowed to start first, afterwhich an agent VM is dynamically attached to the application's PID. This mode requires 2 commands from the command line: the first for the application, and the second for the agent VM.

With <ins>Static Deferred Attach</ins>, the application is executed with the `-javaagent` argument, but the agent initialization is deferred until the application is started. This mode requires 1 command from the command line, and is designed specifically for Spring runtimes that have complex initialization lifecycles. The [<ins>SpecialAgent</ins>](#41-specialagent) relies on the `ContextRefreshedEvent` to signify that the application is ready, and thus to cue agent initialization. This approach works for all versions of Spring and Spring Boot.

The following command can be used as an example:

```bash
java -javaagent:opentracing-specialagent-1.4.2.jar -Dsa.spring -jar MySpringApp.jar
```

## 3 Configuration

### 3.1 Overview

The [<ins>SpecialAgent</ins>](#41-specialagent) exposes a simple pattern for configuration of [<ins>SpecialAgent</ins>](#41-specialagent), the [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin), as well as [<ins>Tracer Plugins</ins>](#43-tracer-plugin). The configuration pattern is based on system properties, which can be defined on the command-line, in a properties file, or in [@AgentRunner.Config][agentrunner-config] for JUnit tests:

1. Properties passed on the command-line via `-D${PROPERTY}=...` override same-named properties defined in layers below...

1. The [@AgentRunner.Config][agentrunner-config] annotation allows one to define log level and transformation event logging settings. Properties defined in the `@Config` annotation override same-named properties defined in layers below...

1. The `-Dsa.config=${PROPERTIES_FILE}` command-line argument can be specified for [<ins>SpecialAgent</ins>](#41-specialagent) to load property names from a `${PROPERTIES_FILE}`. Properties defined in the `${PROPERTIES_FILE}` override same-named properties defined in the layer below...

1. The [<ins>SpecialAgent</ins>](#41-specialagent) has a `default.properties` file that defines default values for properties that need to be defined.

### 3.2 Properties

The following properties are supported by all instrumentation plugins:

1. Logging:

   The `-Dsa.log.level` system property can be used to set the logging level for <ins>SpecialAgent</ins>. Acceptable values are: `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, or `FINEST`, or any numerical log level value is accepted also. The default logging level is set to `WARNING`.

   The `-Dsa.log.events` system property can be used to set the re/transformation events to log: `DISCOVERY`, `IGNORED`, `TRANSFORMATION`, `ERROR`, `COMPLETE`. The property accepts a comma-delimited list of event names. By default, the `ERROR` event is logged (only when run with `AgentRunner`).

   The `-Dsa.log.file` system property can be used to set the logging output file for <ins>SpecialAgent</ins>.

1. Verbose Mode: `-Dsa.instrumentation.plugin.*.verbose`, `-Dsa.instrumentation.plugin.${RULE_NAME_PATTERN}.verbose`

   Sets verbose mode for all or one plugin (Default: false). This property can also be set in an `AgentRunner` JUnit test with the `@AgentRunner.Config(verbose=true)` for all tests in a JUnit class, or `@AgentRunner.TestConfig(verbose=true)` for an individual JUnit test method.

   Concurrent plugin supports verbose mode which is disabled by default. To enable set `sa.concurrent.verbose=true`. In non verbose mode parent span context (if exists) is propagating to task execution. In verbose mode parent span is always created on task submission to executor and child span is created when task is started.

1. Skip fingerprint verification: `-Dsa.fingerprint.skip`

   Tells the [<ins>SpecialAgent</ins>](#41-specialagent) to skip the fingerprint verification when linking [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) into class loaders. This option allows one to work around an unexpected fingerprint verification failure, which can happen in complex runtimes that do not contain all class definitions on the class path. It must be noted, however, that if the fingerprint verification is disabled, the [<ins>SpecialAgent</ins>](#41-specialagent) will indiscriminately install all plugins regardless of library version compatibility issues, which may lead to `NoClassDefFoundError`, `IllegalAccessError`, `AbstractMethodError`, `LinkageError`, etc.

### 3.3 Selecting the [<ins>Tracer Plugin</ins>](#43-tracer-plugin)

The [<ins>SpecialAgent</ins>](#41-specialagent) supports OpenTracing-compatible tracers. There are 2 ways to connect a tracer to the [<ins>SpecialAgent</ins>](#41-specialagent) runtime:

1. **Internal [<ins>Tracer Plugins</ins>](#43-tracer-plugin)**

    The [<ins>SpecialAgent</ins>](#41-specialagent) includes the following [<ins>Tracer Plugins</ins>](#43-tracer-plugin):

    1. [Jaeger Tracer Plugin](https://github.com/opentracing-contrib/java-opentracing-jaeger-bundle)
    1. [LightStep Tracer Plugin](https://github.com/lightstep/lightstep-tracer-java/tree/master/lightstep-tracer-jre-bundle)
    1. [Wavefront Tracer Plugin](https://github.com/wavefrontHQ/wavefront-opentracing-bundle-java)

    The `-Dsa.tracer=${TRACER_PLUGIN}` property is used on the command-line to specify which [<ins>Tracer Plugin</ins>](#43-tracer-plugin) will be used. The value of `${TRACER_PLUGIN}` is the short name of the [<ins>Tracer Plugin</ins>](#43-tracer-plugin), i.e. `jaeger`, `lightstep`, or `wavefront`.

1. **External [<ins>Tracer Plugins</ins>](#43-tracer-plugin)**

    The [<ins>SpecialAgent</ins>](#41-specialagent) allows external [<ins>Tracer Plugins</ins>](#43-tracer-plugin) to be attached to the runtime.

    The `-Dsa.tracer=${TRACER_JAR}` property is used on the command-line to specify the JAR path of the [<ins>Tracer Plugin</ins>](#43-tracer-plugin) to be used. The `${TRACER_JAR}` must be a JAR that conforms to the [`TracerFactory`](https://github.com/opentracing-contrib/java-tracerresolver#tracer-factory) API of the [TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver) project.

_**NOTE**: If a tracer is not specified with the `-Dsa.tracer=...` property, the [<ins>SpecialAgent</ins>](#41-specialagent) will present a warning in the log that states: `Tracer NOT RESOLVED`._

### 3.4 Disabling [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin)

The [<ins>SpecialAgent</ins>](#41-specialagent) has all of its [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) enabled by default, and allows them to be disabled.

#### 3.4.1 Disabling All Instrumentation Plugins

To disable _all **instrumentation** plugins_, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
sa.instrumentation.plugin.*.disable
```

#### 3.4.2 Disabling One Instrumentation Plugin

To disable _an individual **instrumentation** plugin_, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
sa.instrumentation.plugin.${RULE_NAME_PATTERN}.disable
```

The value of `${RULE_NAME_PATTERN}` represents the Rule Name, as specified in [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins). The `${RULE_NAME_PATTERN}` allows for the use of `*` and `?` characters to match multiple rules simultaneously. For instance:

1. `lettuce:5.?`<br>Matches all Lettuce plugins, including `lettuce:5.0`, `lettuce:5.1`, and `lettuce:5.2`.
1. `spring:web:*`<br>Matches all Spring Web plugins, including `spring:web:3` and `spring:web:5`.
1. `spring:web*`<br>Matches all Spring Web and WebMVC plugins, including `spring:web:3`, `spring:web:5`, `spring:webmvc:3`, and `spring:webmvc:5`.

If the _version part_ of the `${RULE_NAME_PATTERN}` does not end with a `*` or `?` character, a `*` will be appended automatically. Therefore:

1. `lettuce:5`<br>Matches all Lettuce v5 plugins, including `lettuce:5.0`, `lettuce:5.1`, and `lettuce:5.2`.
1. `spring:web`<br>Matches all Spring Web plugins, including `spring:web:3` and `spring:web:5`.
1. `spring`<br>Matches all Spring plugins.
1. `spring:w`<br>Does not match any plugins.

#### 3.4.3 Disabling `AgentRule`s of an Instrumentation Plugin

To disable _an individual `AgentRule` of an **instrumentation** plugin_, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
sa.instrumentation.plugin.${PLUGIN_NAME}#${AGENT_RULE_SIMPLE_CLASS_NAME}.disable
```

The value of `${AGENT_RULE_SIMPLE_CLASS_NAME}` is the simple class name of the `AgentRule` subclass that is to be disabled.

### 3.5 Disabling [<ins>Tracer Plugins</ins>](#43-tracer-plugin)

The [<ins>SpecialAgent</ins>](#41-specialagent) has all of its [<ins>Tracer Plugins</ins>](#43-tracer-plugin) enabled by default, and allows them to be disabled.

To disable _all **tracer** plugins_, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
sa.tracer.plugins.disable
```

To disable _an individual **tracer** plugin_, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
sa.tracer.plugin.${SHORT_NAME}.disable
```

The value of `${SHORT_NAME}` is the short name of the plugin, such as `lightstep`, `wavefront`, or `jaeger`.

## 4 Definitions

The following terms are used throughout this documentation.

#### 4.1 <ins>SpecialAgent</ins>

The [<ins>SpecialAgent</ins>](#41-specialagent) is software that attaches to Java applications, and automatically instruments 3rd-party libraries integrated in the application. The [<ins>SpecialAgent</ins>](#41-specialagent) uses the OpenTracing API for [<ins>Instrumentation Plugins</ins>](#supported-instrumentation-plugins) that instrument 3rd-party libraries, as well as [<ins>Tracer Plugins</ins>](#supported-tracer-plugins) that implement OpenTracing tracer service providers. Both the [<ins>Instrumentation Plugins</ins>](#supported-instrumentation-plugins), as well as the [<ins>Tracer Plugins</ins>](#supported-tracer-plugins) are open-source, and are developed and supported by the OpenTracing community.

The [<ins>SpecialAgent</ins>](#41-specialagent) supports Oracle Java and OpenJDK. When building [<ins>SpecialAgent</ins>](#41-specialagent) from source, only Oracle Java is supported.

#### 4.2 [<ins>Tracer</ins>](#42-tracer)

Service provider of the OpenTracing standard, providing an implementation of the `io.opentracing.Tracer` interface.

Examples:
* [Jaeger Tracer](https://github.com/jaegertracing/jaeger)
* [LightStep Tracer](https://github.com/lightstep/lightstep-tracer-java)
* [Wavefront Tracer](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java)
* [`MockTracer`](https://github.com/opentracing/opentracing-java/blob/master/opentracing-mock/)

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
* [`rules/specialagent-okhttp`][okhttp]
* [`rules/specialagent-jdbc`][jdbc]
* [`rules/specialagent-jms-1`][jms-1]
* [`rules/specialagent-jms-2`][jms-2]

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

1. The [<ins>SpecialAgent</ins>](#41-specialagent) is not designed to modify application code, beyond the installation of [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin). For example, there is no facility for dynamically tracing arbitrary code.

## 6 Supported Plugins

### 6.1 [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin)

The following plugins have [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) implemented.

| OpenTracing Plugin | SpecialAgent Rule | Min Version | Max Version |
|:-|:-|:-:|:-:|
| [Akka Actor](https://github.com/opentracing-contrib/java-akka) | [`akka:actor`][akka] | 2.5.0 | 2.5.25 |
| [Apache Camel](https://github.com/apache/camel/tree/master/components/camel-opentracing) | [`camel`][camel] | 2.24.0 | 2.24.2 |
| [Apache HttpClient](https://github.com/opentracing-contrib/java-apache-httpclient) | [`apache:httpclient`][apache-httpclient] | 4.4 | 4.5.9 |
| [Async Http Client](https://github.com/opentracing-contrib/java-asynchttpclient) | [`asynchttpclient`][asynchttpclient] | 2.7.0 | 2.10.1 |
| [AWS SDK](https://github.com/opentracing-contrib/java-aws-sdk) | [`aws:sdk:1`][aws-sdk-1] | 1.11.79 | 1.11.570 |
| | [`aws:sdk:2`][aws-sdk-2] | 2.1.4 | 2.7.15 |
| [Cassandra Driver](https://github.com/opentracing-contrib/java-cassandra-driver) | [`cassandra:driver:3`][cassandra-driver-3] | 3.0.0 | 3.7.2 |
| | [`cassandra:driver:4`][cassandra-driver-4] | 4.0.0 | 4.2.0 |
| [Elasticsearch Client<br>&nbsp;](https://github.com/opentracing-contrib/java-elasticsearch-client) | [`elasticsearch:client-transport`][elasticsearch-7-transport-client]<br>[`elasticsearch:client-rest`][elasticsearch-7-rest-client] | 6.4.0<br>&nbsp; | 7.3.1<br>&nbsp; |
| [Feign](https://github.com/OpenFeign/feign-opentracing/tree/master/feign-opentracing) | [`feign`][feign] | 9.0.0 | 10.4.0 |
| [Grizzly AsyncHttpClient](https://github.com/opentracing-contrib/java-grizzly-ahc) | [`grizzly:ahc`][grizzly-ahc] | 1.15 | **1.15** |
| [Grizzly HTTP Server](https://github.com/opentracing-contrib/java-grizzly-http-server) | [`grizzly:http-server`][grizzly-http-server] | 2.3.35 | **2.3.35** |
| [GRPC](https://github.com/opentracing-contrib/java-grpc) | [`grpc`][grpc] | 1.6.0 | 1.23.0 |
| [Hazelcast](https://github.com/opentracing-contrib/opentracing-hazelcast) | [`hazelcast`][hazelcast] | 3.7 | 3.12.2 |
| [Java Concurrent API \[`java.util.concurrent`\]](https://github.com/opentracing-contrib/java-concurrent) | [`concurrent`][concurrent] | 1.5 | 11 |
| [Java JDBC API \[`java.sql`\]][java-jdbc] | [`jdbc`][jdbc] | 3.1 | 4.3 |
| [Java JMS API \[`javax.jms`\]][java-jms] | [`jms:1`][jms-1] | 1.1 | 1.1 |
| | [`jms:2`][jms-2] | 2.0 | 2.0a |
| [Java Servlet API \[`javax.servlet`\]](https://github.com/opentracing-contrib/java-web-servlet-filter) | [`servlet`][servlet] | 2.3 | 3.1 |
| &nbsp;&nbsp;&nbsp;&nbsp;Jetty | | 7.6.21.v20160908 | 9.2.15.v20160210 |
| &nbsp;&nbsp;&nbsp;&nbsp;Tomcat | | 7.0.65 | **7.0.65** |
| Java Thread [`java.lang.Thread`] | [`thread`][thread] | 1.0 | 11 |
| [JAX-RS Client](https://github.com/opentracing-contrib/java-jaxrs) | [`jax-rs`][jaxrs] | 2.0 | 2.1 |
| [Jedis Client](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-jedis) | [`jedis`][jedis] | 2.7.0 | 3.1.0 |
| [Kafka Client](https://github.com/opentracing-contrib/java-kafka-client) | [`kafka:client`][kafka-client] | 1.1.0 | 2.3.0 |
| [Lettuce Client](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-lettuce) | [`lettuce:5.0`][lettuce-5.0] | 5.0.0.RELEASE | 5.0.5.RELEASE |
| | [`lettuce:5.1`][lettuce-5.1] | 5.1.0.M1 | 5.1.8.RELEASE |
| | [`lettuce:5.2`][lettuce-5.2] | 5.2.0.RELEASE | **5.2.0.RELEASE** |
| [MongoDB Driver](https://github.com/opentracing-contrib/java-mongo-driver) | [`mongo:driver`][mongo-driver] | 3.9.0 | 3.11.0 |
| [OkHttp][java-okhttp] | [`okhttp`][okhttp] | 3.5.0 | 3.14.2 |
| [RabbitMQ Client](https://github.com/opentracing-contrib/java-rabbitmq-client) | [`rabbitmq:client`][rabbitmq-client] | 5.0.0 | 5.7.3 |
| [Reactor](https://github.com/opentracing-contrib/java-reactor) | [`reactor`][reactor] | 3.2.3.RELEASE | **3.2.3.RELEASE** |
| [Redisson](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-redisson) | [`redisson`][redisson] | 3.6.0 | 3.11.3 |
| [RxJava 2](https://github.com/opentracing-contrib/java-rxjava) | [`rxjava:2`][rxjava-2] | 2.1.0 | 2.2.12 |
| [Spring Boot JMS](https://github.com/opentracing-contrib/java-jms/tree/master/opentracing-jms-spring) | [`spring:jms`][spring-jms] | 1.5.22.RELEASE | 2.1.8.RELEASE |
| [Spring Boot Kafka](https://github.com/opentracing-contrib/java-kafka-client/tree/master/opentracing-kafka-spring) | [`spring:kafka`][spring-kafka] | 2.1.0.RELEASE | 2.1.8.RELEASE |
| [Spring Boot Messaging](https://github.com/opentracing-contrib/java-spring-messaging) | [`spring:messaging`][spring-messaging] | 2.1.0.RELEASE | 2.1.8.RELEASE |
| [Spring Boot RabbitMQ](https://github.com/opentracing-contrib/java-spring-rabbitmq) | [`spring:rabbitmq`][spring-rabbitmq] | 2.0.0.RELEASE | 2.1.8.RELEASE |
| [Spring Boot WebFlux](https://github.com/opentracing-contrib/java-spring-web) | [`spring:webflux`][spring-webflux] | 2.1.0.RELEASE | 2.1.8.RELEASE |
| [Spring Boot WebSocket STOMP](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-websocket-starter) | [`spring:websocket`][spring-websocket] | 2.1.0.RELEASE | 2.1.8.RELEASE |
| [Spring [`@Async`] and `@Scheduled`](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-core) | [`spring:scheduling`][spring-scheduling] | 1.5.22.RELEASE | 2.1.8.RELEASE |
| [Spring Web](https://github.com/opentracing-contrib/java-spring-web) | [`spring:web:3`][spring-web-3] | 3.0.3.RELEASE | 3.2.18.RELEASE |
| | [`spring:web:4.0`][spring-web-4.0] | 4.0.0.RELEASE | 4.0.9.RELEASE |
| | [`spring:web:4.x`][spring-web-4] | 4.1.0.RELEASE | 4.3.25.RELEASE |
| | [`spring:web:5`][spring-web-5] | 5.0.0.RELEASE | 5.1.9.RELEASE |
| [Spring Web MVC](https://github.com/opentracing-contrib/java-spring-web) | [`spring:webmvc:3`][spring-webmvc-3] | 3.0.2.RELEASE | 3.2.18.RELEASE |
| | [`spring:webmvc:4`][spring-webmvc-4] | 4.0.0.RELEASE | 4.3.25.RELEASE |
| | [`spring:webmvc:5`][spring-webmvc-5] | 5.0.0.RELEASE | 5.1.9.RELEASE |
| [Spymemcached](https://github.com/opentracing-contrib/java-memcached-client/tree/master/opentracing-spymemcached) | [`spymemcached`][spymemcached] | 2.11.0 | 2.12.3 |
| [Thrift](https://github.com/opentracing-contrib/java-thrift) | [`thrift`][thrift] | 0.12.0 | **0.12.0** |
| [Zuul](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-zuul-starter) | [`zuul`][zuul] | 1.0.0 | 2.1.8 |

### 6.2 [<ins>Tracer Plugins</ins>](#43-tracer-plugin)

The following OpenTracing tracer service providers have [<ins>Tracer Plugins</ins>](#43-tracer-plugin) implemented.
Here is a [demo](https://github.com/opentracing-contrib/java-specialagent-demo).

1. [Jaeger Tracer Plugin](https://github.com/opentracing-contrib/java-opentracing-jaeger-bundle) ([Configuration reference](https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md#configuration-via-environment))
1. [LightStep Tracer Plugin](https://github.com/lightstep/lightstep-tracer-java/tree/master/lightstep-tracer-jre-bundle)
1. [Wavefront Tracer Plugin](https://github.com/wavefrontHQ/wavefront-opentracing-bundle-java)

### 6.3 [<ins>Instrumented libraries by existing rules</ins>](#46-instrumented-libs)

The following libraries are instrumented by existing [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule).

1. [Solr Client](https://github.com/opentracing-contrib/java-solr-client)
1. [JDBI 3.8.2](https://github.com/opentracing-contrib/java-jdbi)
1. [Hystrix 1.5.18](https://github.com/OpenFeign/feign-opentracing/tree/master/feign-hystrix-opentracing)
1. [Spring Cloud](https://github.com/opentracing-contrib/java-spring-cloud)

## 7 Credits

Thank you to the following contributors for developing instrumentation plugins:

* [Sergei Malafeev](https://github.com/malafeev)
* [Jose Montoya](https://github.com/jam01)
* [Przemyslaw Maciolek](https://github.com/pmaciolek)

Thank you to the following contributors for developing tracer plugins:

* [Carlos Alberto Cortez](https://github.com/carlosalberto)
* [Han Zhang](https://github.com/hanwavefront)

Thank you to the following developers for filing issues and helping us fix them:

* [Louis-Etienne](https://github.com/ledor473)
* [Marcos Trejo Munguia](https://github.com/mtrejo)
* [@kaushikdeb](https://github.com/kaushikdeb)
* [@deepakgoenka](https://github.com/deepakgoenka)
* [@etsangsplk](https://github.com/etsangsplk)
* [@Vovan2006](https://github.com/Vovan2006)

Thank you to the following individuals for noticing typographic errors and sending PRs:

* [Daniel Rodriguez Hernandez](https://github.com/drodriguezhdez)
* [qudongfang](https://github.com/qudongfang)

Finally, thanks for all of the feedback! Please share your comments [as an issue](https://github.com/opentracing-contrib/java-specialagent/issues)!

## 8 Contributing

Pull requests are welcome. For major changes, please [open an issue](https://github.com/opentracing-contrib/java-specialagent/issues) first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## 9 License

This project is licensed under the Apache 2 License - see the [LICENSE.txt](LICENSE.txt) file for details.

[akka]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/akka
[camel]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/camel
[apache-httpclient]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/apache-httpclient
[asynchttpclient]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/asynchttpclient
[aws-sdk-1]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/aws-sdk-1
[aws-sdk-2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/aws-sdk-2
[cassandra-driver-3]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/cassandra-driver-3
[cassandra-driver-4]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/cassandra-driver-4
[elasticsearch-7-rest-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/elasticsearch-7-rest-client
[elasticsearch-7-transport-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/elasticsearch-7-transport-client
[feign]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/feign
[grizzly-ahc]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/grizzly-ahc
[grizzly-http-server]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/grizzly-http-server
[grpc]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/grpc
[hazelcast]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/hazelcast
[concurrent]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/concurrent
[jdbc]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/jdbc
[jms-1]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/jms-1
[jms-2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/jms-2
[servlet]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/servlet
[jaxrs]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/jaxrs
[jedis]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/jedis
[kafka-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/kafka-client
[lettuce-5.0]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/lettuce-5.0
[lettuce-5.1]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/lettuce-5.1
[lettuce-5.2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/lettuce-5.2
[mongo-driver]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/mongo-driver
[okhttp]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/okhttp
[rabbitmq-client]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/rabbitmq-client
[reactor]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/reactor
[redisson]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/redisson
[rxjava-2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/rxjava-2
[spring-jms]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-jms
[spring-kafka]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-kafka
[spring-messaging]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-messaging
[spring-rabbitmq]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-rabbitmq
[spring-scheduling]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-scheduling
[spring-web-3]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-web-3
[spring-web-4.0]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-web-4.0
[spring-web-4]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-web-4
[spring-web-5]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-web-5
[spring-webflux]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-webflux
[spring-webmvc-3]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-webmvc-3
[spring-webmvc-4]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-webmvc-4
[spring-webmvc-5]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-webmvc-5
[spring-websocket]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spring-websocket
[spymemcached]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/spymemcached
[thread]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/thread
[thrift]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/thrift
[zuul]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/zuul

[agentrunner-config]: https://github.com/opentracing-contrib/java-specialagent/tree/master/opentracing-specialagent-api#51-configuring-agentrunner
[api]: https://github.com/opentracing-contrib/java-specialagent/tree/master/opentracing-specialagent-api
[java-jdbc]: https://github.com/opentracing-contrib/java-jdbc
[java-jms]: https://github.com/opentracing-contrib/java-jms
[java-okhttp]: https://github.com/opentracing-contrib/java-okhttp
[opentracing-contrib]: https://github.com/opentracing-contrib/
[pom]: https://maven.apache.org/pom.html

[main-release]: http://central.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.2/opentracing-specialagent-1.4.2.jar
[main-snapshot]: https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.3-SNAPSHOT
[test-release]: http://central.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.2/opentracing-specialagent-1.4.2-tests.jar
[test-snapshot]: https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.3-SNAPSHOT