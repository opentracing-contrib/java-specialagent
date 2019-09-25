# Java Agent for OpenTracing

> Automatically instruments 3rd-party libraries in Java applications

[![Build Status](https://travis-ci.org/opentracing-contrib/java-specialagent.svg?branch=master)](https://travis-ci.org/opentracing-contrib/java-specialagent)
[![Coverage Status](https://coveralls.io/repos/github/opentracing-contrib/java-specialagent/badge.svg?branch=master)](https://coveralls.io/github/opentracing-contrib/java-specialagent?branch=master)
[![Javadocs](https://www.javadoc.io/badge/io.opentracing.contrib.specialagent/opentracing-specialagent.svg)](https://www.javadoc.io/doc/io.opentracing.contrib.specialagent/opentracing-specialagent)
[![Released Version](https://img.shields.io/maven-central/v/io.opentracing.contrib.specialagent/specialagent.svg)](https://mvnrepository.com/artifact/io.opentracing.contrib.specialagent/opentracing-specialagent)

<sub>_Note: The coverage statistic is not correct, because Jacoco cannot properly instrument code that is instrumented at the bytecode level._</sub>

## What is SpecialAgent?

<ins>SpecialAgent</ins> automatically instruments 3rd-party libraries in Java applications. The architecture of <ins>SpecialAgent</ins> was designed to involve contributions from the community, whereby its platform integrates and automates OpenTracing <ins>Instrumentation Plugins</ins> written by individual contributors. In addition to <ins>Instrumentation Plugins</ins>, the <ins>SpecialAgent</ins> also supports <ins>Tracer Plugins</ins>, which connect an instrumented runtime to OpenTracing-compliant tracer vendors, such as LightStep or Jaeger. Both the <ins>Instrumentation Plugins</ins> and the <ins>Tracer Plugins</ins> are decoupled from <ins>SpecialAgent</ins> -- i.e. neither kinds of plugins need to know anything about <ins>SpecialAgent</ins>. At its core, the <ins>SpecialAgent</ins> is itself nothing more than an engine that abstracts the functionality for automatic installation of <ins>Instrumentation Plugins</ins>, and then connecting them to <ins>Tracer Plugins</ins>. A benefit of this approach is that the <ins>SpecialAgent</ins> intrinsically embodies and encourages community involvement.

In additiona to its engine, the <ins>SpecialAgent</ins> packages a set of pre-supported [<ins>Instrumentation Plugins</ins>](#61-instrumentation-plugins) and [<ins>Tracer Plugins</ins>](#62-tracer-plugins).

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

To use the [<ins>SpecialAgent</ins>](#41-specialagent) on an application, first download the JAR:

##### 2.1.1.1 Stable

The latest stable release is: [1.4.0][main-release].

```bash
wget -O opentracing-specialagent-1.4.0.jar "http://central.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.0/opentracing-specialagent-1.4.0.jar"
```

##### 2.1.1.2 Development

The latest development release is: <ins>1.4.1-SNAPSHOT</ins>.
```bash
wget -O opentracing-specialagent-1.4.1-SNAPSHOT.jar "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.opentracing.contrib.specialagent&a=opentracing-specialagent&v=LATEST"
```

This is the main artifact that contains within it the [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) from the [opentracing-contrib][opentracing-contrib] organization for which [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) have been implemented. This JAR can be specified as the `-javaagent` target for [<ins>Static Attach</ins>](#221-static-attach) to an application. This JAR can also be executed, standalone, with an argument representing the PID of a target process to which it should dynamically attach. Please refer to [Usage](#usage) section for usage instructions.

#### 2.1.2 For Development

For development of [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin), import the `opentracing-specialagent-api` and `test-jar` of the `opentracing-specialagent`.

```xml
<dependency>
  <groupId>io.opentracing.contrib.specialagent</groupId>
  <artifactId>opentracing-specialagent-api</artifactId>
  <version>1.4.0</version> <!--version>1.4.1-SNAPSHOT<version-->
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>io.opentracing.contrib.specialagent</groupId>
  <artifactId>opentracing-specialagent</artifactId>
  <version>1.4.0</version> <!--version>1.4.1-SNAPSHOT<version-->
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

This is the test artifact that contains within it the `AgentRunner`, which is a JUnit runner class provided for testing of the ByteBuddy auto-instrumentation rules. This JAR does not contain within it any [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) themselves, and is only intended to be applied to the test phase of the build lifecycle of a single plugin for an [<ins>Instrumentation Plugin</ins>](#44-instrumentation-plugin) implementation. For direction with the `AgentRunner`, please refer to the [`opentracing-specialagent-api`][api] module.

##### 2.1.2.1 Building

_**Prerequisite**: The [<ins>SpecialAgent</ins>](#41-specialagent) requires [Oracle Java](https://www.oracle.com/technetwork/java/javase/downloads/) to build. Thought the [<ins>SpecialAgent</ins>](#41-specialagent) supports OpenJDK for general application use, it only supports Oracle Java for building and testing._

The [<ins>SpecialAgent</ins>](#41-specialagent) is built in 2 passes that utilize different profiles:

1. The `default` profile is used for development of [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule). It builds and runs tests for each rule, but _does not bundle the rules_ into [`opentracing-specialagent-1.4.0.jar`][main-release]

    To run this profile:
    ```bash
    mvn clean install
    ```

1. The `assemble` profile is used to bundle the [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule) into [`opentracing-specialagent-1.4.0.jar`][main-release]. It builds each rule, but _does not run tests._ Once the build with the `assemble` profile is finished, the [`opentracing-specialagent-1.4.0.jar`][main-release] will contain the built rules inside it.

    _**Note**: If you do not run this step, the [`opentracing-specialagent-1.4.0.jar`][main-release] from the previous step will not contain any [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) within it!_

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
java -javaagent:opentracing-specialagent-1.4.0.jar -jar MyApp.jar
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
    java -Xbootclasspath/a:$JAVA_HOME/lib/tools.jar -jar opentracing-specialagent-1.4.0.jar <PID>
    ```

1. For jdk9+
    ```bash
    java -jar opentracing-specialagent-1.4.0.jar <PID>
    ```

**Note:** If you encounter an exception stating `Unable to open socket file`, make sure the attaching VM is executed with the same permissions as the target VM.

#### 2.2.3 <ins>Static Deferred Attach</ins>

With [<ins>Static Attach</ins>](#221-static-attach), the application is executed with the `-javaagent` argument, and the agent initialization occurs before the application is started. This mode requires 1 command from the command line.

With [<ins>Dynamic Attach</ins>](#222-dynamic-attach), the application is allowed to start first, afterwhich an agent VM is dynamically attached to the application's PID. This mode requires 2 commands from the command line: the first for the application, and the second for the agent VM.

With <ins>Static Deferred Attach</ins>, the application is executed with the `-javaagent` argument, but the agent initialization is deferred until the application is started. This mode requires 1 command from the command line, and is designed specifically for Spring runtimes that have complex initialization lifecycles. The [<ins>SpecialAgent</ins>](#41-specialagent) relies on the `ContextRefreshedEvent` to signify that the application is ready, and thus to cue agent initialization. This approach works for all versions of Spring and Spring Boot.

The following command can be used as an example:

```bash
java -javaagent:opentracing-specialagent-1.4.0.jar -Dsa.spring -jar MySpringApp.jar
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

1. Verbose Mode: `-Dsa.instrumentation.plugins.verbose`, `-Dsa.instrumentation.plugin.${PLUGIN_NAME}.verbose`

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

    The `-Dsa.tracer=${TRACER_PLUGIN}` property is used on the command-line to specify which [<ins>Tracer Plugin</ins>](#43-tracer-plugin) will be used. The value of `${TRACER_PLUGIN}` is the short name of the [<ins>Tracer Plugin</ins>](#43-tracer-plugin), i.e. `jaeger` or `lightstep`.

1. **External [<ins>Tracer Plugins</ins>](#43-tracer-plugin)**

    The [<ins>SpecialAgent</ins>](#41-specialagent) allows external [<ins>Tracer Plugins</ins>](#43-tracer-plugin) to be attached to the runtime.

    The `-Dsa.tracer=${TRACER_JAR}` property is used on the command-line to specify the JAR path of the [<ins>Tracer Plugin</ins>](#43-tracer-plugin) to be used. The `${TRACER_JAR}` must be a JAR that conforms to the [`TracerFactory`](https://github.com/opentracing-contrib/java-tracerresolver#tracer-factory) API of the [TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver) project.

_**NOTE**: If a tracer is not specified with the `-Dsa.tracer=...` property, the [<ins>SpecialAgent</ins>](#41-specialagent) will present a warning in the log that states: `Tracer NOT RESOLVED`._

### 3.4 Disabling [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin)

The [<ins>SpecialAgent</ins>](#41-specialagent) has all of its [<ins>Instrumentation Plugins</ins>](#44-instrumentation-plugin) enabled by default, and allows them to be disabled.

#### 3.4.1 Disabling All Instrumentation Plugins

To disable _all **instrumentation** plugins_, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
sa.instrumentation.plugins.disable
```

#### 3.4.2 Disabling One Instrumentation Plugin

To disable _an individual **instrumentation** plugin_, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
sa.instrumentation.plugin.${PLUGIN_NAME}.disable
```

The value of `${PLUGIN_NAME}` is the name of the plugin as declared in the plugin's POM ([Project Object Model][pom]). The names follow a consice pattern, such as `okhttp` for the `specialagent-okhttp` plugin artifactId, and `web-servlet-filter` for the `specialagent-web-servlet-filter` plugin artifactId.

#### 3.4.3 Disabling `AgentRule`s of an Instrumentation Plugin

To disable _an individual `AgentRule` of an **instrumentation** plugin_, specify a system property, either on the command-line or in the properties file referenced by `-Dconfig=${PROPERTIES_FILE}`.

```
sa.instrumentation.plugin.${PLUGIN_NAME}.${AGENT_RULE_SIMPLE_CLASS_NAME}.disable
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

The value of `${SHORT_NAME}` is the short name of the plugin, such as `lightstep` or `jaeger`.

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
* [`rules/specialagent-okhttp`][specialagent-okhttp]
* [`rules/specialagent-jdbc`][specialagent-jdbc]
* [`rules/specialagent-jms-1`][specialagent-jms-1]
* [`rules/specialagent-jms-2`][specialagent-jms-2]

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

| Integration | Min Version | Max Version |
|:-|:-:|:-:|
| [Akka Actor](https://github.com/opentracing-contrib/java-akka) | 2.5.0 | LATEST |
| [Apache Camel](https://github.com/apache/camel/tree/master/components/camel-opentracing) | 2.24.0 | LATEST |
| [Apache HttpClient](https://github.com/opentracing-contrib/java-apache-httpclient) | 4.4 | LATEST |
| [Async Http Client](https://github.com/opentracing-contrib/java-asynchttpclient) | 2.7.0 | LATEST |
| [AWS SDK 1](https://github.com/opentracing-contrib/java-aws-sdk) | 1.11.79 | LATEST |
| [AWS SDK 2](https://github.com/opentracing-contrib/java-aws-sdk) | 2.1.4 | LATEST |
| [Cassandra 3 Driver](https://github.com/opentracing-contrib/java-cassandra-driver) | 3.0.0 | 3.7.2 |
| [Cassandra 4 Driver](https://github.com/opentracing-contrib/java-cassandra-driver) | 4.0.0 | LATEST |
| [Elasticsearch Client](https://github.com/opentracing-contrib/java-elasticsearch-client) | 6.4.0 | LATEST |
| [Feign](https://github.com/OpenFeign/feign-opentracing/tree/master/feign-opentracing) | 9.0.0 | LATEST |
| [Grizzly AsyncHttpClient](https://github.com/opentracing-contrib/java-grizzly-ahc) | 1.15 | LATEST |
| [Grizzly HTTP Server](https://github.com/opentracing-contrib/java-grizzly-http-server) | 2.3.35 | LATEST |
| [GRPC](https://github.com/opentracing-contrib/java-grpc) | 1.6.0 | LATEST |
| [Hazelcast](https://github.com/opentracing-contrib/opentracing-hazelcast) | 3.7 | LATEST |
| [Java Concurrent API \[`java.util.concurrent`\]](https://github.com/opentracing-contrib/java-concurrent) | 1.5 | LATEST |
| [Java JDBC API \[`java.sql`\]][java-jdbc] | 3.1 | LATEST |
| [Java JMS API (`javax.jms`)][java-jms] | 1.1 | LATEST |
| [Java Servlet API \[`javax.servlet`\]](https://github.com/opentracing-contrib/java-web-servlet-filter) | 2.3 | LATEST |
| &nbsp;&nbsp;&nbsp;&nbsp;Jetty | 7.6.21.v20160908 | LATEST |
| &nbsp;&nbsp;&nbsp;&nbsp;Tomcat | 7.0.96 | LATEST |
| [JAX-RS Client](https://github.com/opentracing-contrib/java-jaxrs) | 2.0 | LATEST |
| [Jedis Client](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-jedis) | 2.7.0 | LATEST |
| [Kafka Client](https://github.com/opentracing-contrib/java-kafka-client) | 1.1.0 | LATEST |
| [Lettuce Client](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-lettuce) | 5.1.0.RELEASE | LATEST |
| [Mongo Driver](https://github.com/opentracing-contrib/java-mongo-driver) | 3.9.0 | LATEST |
| [OkHttp3][java-okhttp] | 3.5.0 | 3.14.3 |
| [RabbitMQ Client](https://github.com/opentracing-contrib/java-rabbitmq-client) | 5.0.0 | LATEST |
| [Reactor](https://github.com/opentracing-contrib/java-reactor) | 3.2.3.RELEASE | LATEST |
| [Redisson](https://github.com/opentracing-contrib/java-redis-client/tree/master/opentracing-redis-redisson) | 3.6.0 | LATEST |
| [RxJava 2](https://github.com/opentracing-contrib/java-rxjava) | 2.1.0 | LATEST |
| [Spring `@Async` and `@Scheduled`](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-core) | 5.0.0.RELEASE | LATEST |
| [Spring JMS](https://github.com/opentracing-contrib/java-jms/tree/master/opentracing-jms-spring) | 5.0.0.RELEASE | LATEST |
| [Spring Kafka](https://github.com/opentracing-contrib/java-kafka-client/tree/master/opentracing-kafka-spring) | 2.2.0.RELEASE | LATEST |
| [Spring Messaging](https://github.com/opentracing-contrib/java-spring-messaging) | 5.1.1.RELEASE | LATEST |
| [Spring RabbitMQ](https://github.com/opentracing-contrib/java-spring-rabbitmq) | 2.0.0.RELEASE | LATEST |
| [Spring Web](https://github.com/opentracing-contrib/java-spring-web) | 5.0.0.RELEASE | LATEST |
| &nbsp;&nbsp;&nbsp;&nbsp;[Spring 3 Web](https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/specialagent-spring-web) | 3.0.3.RELEASE | 3.2.18.RELEASE |
| [Spring Web MVC](https://github.com/opentracing-contrib/java-spring-web) | 5.0.0.RELEASE | LATEST |
| &nbsp;&nbsp;&nbsp;&nbsp;[Spring 3 Web MVC](https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/specialagent-spring-webmvc) | 3.0.2.RELEASE | 3.2.18.RELEASE |
| [Spring WebFlux](https://github.com/opentracing-contrib/java-spring-web) | 5.1.0.RELEASE | LATEST |
| [Spring WebSocket STOMP](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-websocket-starter) | 5.1.0.RELEASE | LATEST |
| [Spymemcached](https://github.com/opentracing-contrib/java-memcached-client/tree/master/opentracing-spymemcached) | 2.11.0 | LATEST |
| [Thrift](https://github.com/opentracing-contrib/java-thrift) | 0.12.0 | LATEST |
| [Zuul](https://github.com/opentracing-contrib/java-spring-cloud/tree/master/instrument-starters/opentracing-spring-cloud-zuul-starter) | 1.0.0 | LATEST |

### 6.2 [<ins>Tracer Plugins</ins>](#43-tracer-plugin)

The following OpenTracing tracer service providers have [<ins>Tracer Plugins</ins>](#43-tracer-plugin) implemented.
Here is a [demo](https://github.com/opentracing-contrib/java-specialagent-demo).

1. [Jaeger Tracer Plugin](https://github.com/opentracing-contrib/java-opentracing-jaeger-bundle) ([Configuration reference](https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md#configuration-via-environment))
1. [LightStep Tracer Plugin](https://github.com/lightstep/lightstep-tracer-java/tree/master/lightstep-tracer-jre-bundle)

### 6.3 [<ins>Instrumented libraries by existing rules</ins>](#46-instrumented-libs)

The following libraries are instrumented by existing [<ins>Instrumentation Rules</ins>](#45-instrumentation-rule).

1. [Solr Client](https://github.com/opentracing-contrib/java-solr-client)
1. [JDBI](https://github.com/opentracing-contrib/java-jdbi)
1. [Hystrix](https://github.com/OpenFeign/feign-opentracing/tree/master/feign-hystrix-opentracing)
1. [Spring Cloud](https://github.com/opentracing-contrib/java-spring-cloud)

## 7 Credits

Thank you to the following contributors for developing instrumentation plugins:

* [Sergei Malafeev](https://github.com/malafeev)
* [Jose Montoya](https://github.com/jam01)
* [Przemyslaw Maciolek](https://github.com/pmaciolek)

Thank you to the following contributors for developing tracer plugins:

* [Carlos Alberto Cortez](https://github.com/carlosalberto)

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

[agentrunner-config]: https://github.com/opentracing-contrib/java-specialagent/tree/master/opentracing-specialagent-api#51-configuring-agentrunner
[api]: https://github.com/opentracing-contrib/java-specialagent/tree/master/opentracing-specialagent-api
[java-jdbc]: https://github.com/opentracing-contrib/java-jdbc
[java-jms]: https://github.com/opentracing-contrib/java-jms
[java-okhttp]: https://github.com/opentracing-contrib/java-okhttp
[main-release]: http://central.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.0/opentracing-specialagent-1.4.0.jar
[main-snapshot]: https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.1-SNAPSHOT
[opentracing-contrib]: https://github.com/opentracing-contrib/
[pom]: https://maven.apache.org/pom.html
[specialagent-jdbc]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/specialagent-jdbc
[specialagent-jms-1]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/specialagent-jms-1
[specialagent-jms-2]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/specialagent-jms-2
[specialagent-okhttp]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/specialagent-okhttp
[test-release]: http://central.maven.org/maven2/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.0/opentracing-specialagent-1.4.0-tests.jar
[test-snapshot]: https://oss.sonatype.org/content/repositories/snapshots/io/opentracing/contrib/specialagent/opentracing-specialagent/1.4.1-SNAPSHOT