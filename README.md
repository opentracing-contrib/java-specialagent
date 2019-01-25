# Java Agent for OpenTracing,<br>with automatic instrumentation

> Automatically instruments 3rd-party libraries in Java applications

[![Build Status](https://travis-ci.org/opentracing-contrib/java-specialagent.png)](https://travis-ci.org/opentracing-contrib/java-specialagent)
[![Coverage Status](https://coveralls.io/repos/github/opentracing-contrib/java-specialagent/badge.svg)](https://coveralls.io/github/opentracing-contrib/java-specialagent)

## Overview

_Java SpecialAgent_ automatically instruments Java applications to produce trace events via the OpenTracing API. This file contains the operational instructions for the use of _Java SpecialAgent_.

## Supported Instrumentation Plugins

1. [OkHttp3](https://github.com/opentracing-contrib/java-okhttp)
2. [`java.util.Concurrent`](https://github.com/opentracing-contrib/java-concurrent)
3. [JDBC](https://github.com/opentracing-contrib/java-jdbc)
4. [Java Web Servlet Filter](https://github.com/opentracing-contrib/java-web-servlet-filter)
5. [Mongo Driver](https://github.com/opentracing-contrib/java-mongo-driver)
6. [Apache Camel](https://github.com/apache/camel/tree/master/components/camel-opentracing)

## Operation

When _Java SpecialAgent_ attaches to an application, either statically or dynamically, it will automatically load the OpenTracing instrumentation plugins explicitly specified as dependencies in its POM.

Any exception that occurs during the execution of the bootstrap process will not adversely affect the stability of the target application. It is, however, possible that the instrumentation plugin code may result in exceptions that are not properly handled, and would destabilize the target application.

## Goals

1. The _Java SpecialAgent_ must allow any Java instrumentation plugin available in `opentracing-contrib` to be automatically installable in applications that utilize a 3rd-party library for which an instrumentation plugin exists.
2. The _Java SpecialAgent_ must automatically install the instrumentation plugin for each 3rd-party library for which a plugin exists, regardless in which `ClassLoader` the 3rd-party library is loaded.
3. The _Java SpecialAgent_ must not adversely affect the runtime stability of the application on which it is intended to be used. This goal applies only to the code in the _Java SpecialAgent_, and cannot apply to the code of the instrumentation plugins made available in `opentracing-contrib`.
4. The _Java SpecialAgent_ must support static and dynamic attach to applications running on JVM versions 1.7 to latest.
5. The _Java SpecialAgent_ must implement a lightweight test methodology that can be easily applied to a module that implements instrumentation for a 3rd-party plugin. This test must simulate:
   1) Launch the test in a process with the `-javaagent` vm argument that points to the _Java SpecialAgent_ (in order to test automatic instrumentation functionality of the `otarules.btm` file).
   2) Elevate the test code to be executed from a custom `ClassLoader` that is disconnected from the system `ClassLoader` (in order to test bytecode injection into an isolated `ClassLoader` that cannot resolve classes on the system classpath).
   3) Initialize a `MockTracer` as `GlobalTracer`, and provide a reference to the `Tracer` instance in the test method for assertions with JUnit.
The _Java SpecialAgent_ must provide a means by which instrumentation plugins can be configured before use on a target application. 

## Non-Goals

1. The _Java SpecialAgent_ is not designed to modify application code, beyond the installation of OpenTracing instrumentation plugins. For example, there is no facility for dynamically tracing arbitrary code.

## Installation

The _Java SpecialAgent_ is built with Maven, and produces 2 artifacts:

1. `opentracing-specialagent-<version>.jar`

    This is the main artifact that contains within it all applicable instrumentation plugins from the `opentracing-contrib` project. This JAR can be specified as the `-javaagent` target for static attach to an application. This JAR can also be executed, standalone, with an argument representing the PID of a target process to which it should dynamically attach.

2. `opentracing-specialagent-<version>-tests.jar`

    This is the test artifact that contains within it the `AgentRunner`, which is a JUnit runner class provided for testing of `otarules.btm` files in instrumentation plugins. This JAR does not contain within it any instrumentation plugins themselves, and is only intended to be applied to the test phase of the build lifecycle of a single instrumentation plugin implementation. This JAR can be used in the same manner as the main JAR, both for static and dynamic attach.

## Usage

The _Java SpecialAgent_ uses Java’s Instrumentation interface to transform the behavior of a target application. The entrypoint into the target application is performed via Java’s Agent convention. _Java SpecialAgent_ supports both static and dynamic attach.

### Static Attach

Statically attaching to a Java application involves the use of the `-javaagent` vm argument at the time of startup of the target Java application. The following command can be used as an example:

```bash
java -javaagent:opentracing-specialagent.jar -jar myapp.jar
```

This command statically attaches _Java SpecialAgent_ into the application in `myapp.jar`.

### Dynamic Attach

Dynamically attaching to a Java application involves the use of a running application’s PID, after the application’s startup. The following commands can be used as an example:

1. Call this to obtain the `PID` of the target application:
    ```bash
    jps 
    ```

2. For jdk1.8
    ```bash
    java -Xbootclasspath/a:$JAVA_HOME/lib/tools.jar -jar opentracing-specialagent.jar <PID>
    ```

3. For jdk9+
    ```bash
    java -jar opentracing-specialagent.jar <PID>
    ```

## Debugging

The `-DSpecialAgentLog` system property can be used to set the logging level for SpecialAgent. Acceptable values are: `SEVERE WARNING INFO CONFIG FINE FINER FINEST`, or any numerical log level value is acecpted also. The default logging level is set to `WARNING`.

## Test Usage

The _Java SpecialAgent_ uses the JUnit Runner API to implement a lightweight test methodology that can be easily applied to modules that implement instrumentation for 3rd-party plugins. This runner is named `AgentRunner`, and allows developers to implement tests using vanilla JUnit patterns, transparently providing the following behavior:

1. Launch the test in a process with the `-javaagent` vm argument that points to the _Java SpecialAgent_ (in order to test automatic instrumentation functionality of the `otarules.btm` file).
2. Elevate the test code to be executed from a custom `ClassLoader` that is disconnected from the system `ClassLoader` (in order to test bytecode injection into an isolated `ClassLoader` that cannot resolve classes on the system classpath).
3. Initialize a `MockTracer` as `GlobalTracer`, and provide a reference to the `Tracer` instance in the test method for assertions with JUnit.

The `AgentRunner` is available in the test jar of the _Java SpecialAgent_ module. It can be imported with the following dependency spec:

```xml
<dependency>
  <groupId>io.opentracing.contrib</groupId>
  <artifactId>opentracing-specialagent</artifactId>
  <version>${project.version}</version>
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

To use the `AgentRunner` in a JUnit test class, provide the following annotation to the class in question:

```java
@RunWith(AgentRunner.class)
```

In addition to the `@RunWith` annotation, each method annotated with `@Test` must declare a parameter of type `MockTracer`, as such:

```java
@Test
public void test(MockTracer tracer) {}
```

Similarly, each method annotated with `@Before`, `@After`, `@BeforeClass`, and `@AfterClass` must declare a parameter of type `MockTracer`, as such:

```java
@BeforeClass
public static void beforeClass(MockTracer tracer) {}

@AfterClass
public static void afterClass(MockTracer tracer) {}

@Before
public void before(MockTracer tracer) {}

@After
public void after(MockTracer tracer) {}
```

The `MockTracer` class can be referenced by importing the following dependency spec:

```xml
<dependency>
  <groupId>io.opentracing</groupId>
  <artifactId>opentracing-mock</artifactId>
  <version>${version.opentracing-mock}</version>
  <scope>test</scope>
</dependency>
```

Upon execution of the test class, in either the IDE or with Maven, the `AgentRunner` will execute each test method via the 3 step workflow described above.

### Configuring `AgentRunner`

The `AgentRunner` can be configured via the `@AgentRunner.Config(...)` annotation. The annotation supports the following properties:

1. `debug`: If set to `true`, `FINEST` level root logging will be enabled. Default: `false`.
2. `verbose`: If set to `true`, Byteman verbose logging will be enabled. Default: `false`.
3. `isolateClassLoader`: If set to `true`, tests will be run from a `ClassLoader` that is isolated from the system `ClassLoader`. If set to `false`, tests will be run from the system `ClassLoader`. Default: `true`.
4. `instrumenter`: The retransformation instrumentation manager (`BYTEMAN` or `BYTEBUDDY`). Default: `BYTEBUDDY`.

### Developing Instrumentation Plugins for SpecialAgent

The `opentracing-contrib` repository contains 40+ OpenTracing instrumentation plugins for Java. Only a handful of these plugins are currently [supported by SpecialAgent](#supported-instrumentation-plugins).

If you are interested in contributing to the SpecialAgent project by integrating support for existing plugins in the `opentracing-contrib` repository, or by implementing a new plugin with support for SpecialAgent, the following guide is for you:...

#### Implementing the Instrumentation Logic

The `opentracing-contrib` repository contains instrumentation plugins for a wide variety of 3rd-party libraries, as well as Java standard APIs. The plugins instrument a 3rd-party library of interest by implementing custom library-specific hooks that integrate with the OpenTracing API. To see examples, explore projects named with the prefix **java-...** in the `opentracing-contrib` repository.

#### Implementing the Auto-Instrumentation Script

_The SpecialAgent_ uses Byteman to perform bytecode injection for the purpose of auto-instrumentation. Instrumentation scripts mustb e named `otarules.btm`, and be placed as a resource in the default package. Please refer to the following scripts as examples:

1. [otarules.btm for OkHttp3](https://github.com/opentracing-contrib/java-okhttp/src/main/resources/otarules.btm)
2. [otarules.btm for `java.util.Concurrent`](https://github.com/opentracing-contrib/java-concurrent/src/main/resources/otarules.btm)
3. [otarules.btm for JDBC](https://github.com/opentracing-contrib/java-jdbc/src/main/resources/otarules.btm)
4. [otarules.btm for Java Web Servlet Filter](https://github.com/opentracing-contrib/java-web-servlet-filter/src/main/resources/otarules.btm)
5. [otarules.btm for Mongo Driver](https://github.com/opentracing-contrib/java-mongo-driver/src/main/resources/otarules.btm)
6. [otarules.btm for Apache Camel](https://github.com/apache/camel/blob/master/components/camel-opentracing/src/main/resources/otarules.btm)

#### Packaging

_The SpecialAgent_ has specific requirements for packaging of instrumentation plugins:

1. If the library being instrumented is 3rd-party (i.e. it does not belong to the standard Java APIs), then the dependency artifacts for the library must be non-transitive (i.e. declared with `<scope>test</scope>`, or with `<scope>provided</scope>`).
    * The dependencies for the 3rd-party libraries are not necessary when the plugin is applied to a target application, as the application must already have these dependencies for the plugin to be used.
    * Declaring the 3rd-party libraries as non-transitive dependencies greatly reduces the size of the SpecialAgent package, as all of the instrumentation plugins as contained within it.
    * If 3rd-party libraries are _not_ declared as non-transitive, there is a risk that target applications may experience class loading exceptions due to inadvertant loading of incompatibile classes.
    * Many of the currently implemented instrumentation plugins _do not_ declare the 3rd-party libraries which they are instrumenting as non-transitive. In this case, an `<exclude>` tag must be specified for each 3rd-party artifact dependency when referring to the instrumentation plugin artifact. An example of this can be seen with the instrumentation plugin for the Mongo Driver [here](https://github.com/opentracing-contrib/java-specialagent/blob/master/rules/opentracing-specialagent-mongo-driver/pom.xml#L37-L44).
2. The package must contain a `fingerprint.bin` file. This file provides the SpecialAgent with a fingerprint of the 3rd-party library that the plugin is instrumenting. This fingerprint allows the SpecialAgent to determine if the plugin is compatible with the relevant 3rd-party library in a target application.
    1. To generate the fingerprint, it is first necessary to identify which Maven artifacts are intended to be fingerprinted. To mark an artifact to be fingerprinted, you must add `<optional>true</optional>` to the dependency's spec. Please see the [pom.xml for OkHttp3](https://github.com/opentracing-contrib/java-specialagent/blob/master/rules/opentracing-specialagent-okhttp/pom.xml) as an example.
    2. Next, include the following plugin in the project's POM:
        ```xml
        <plugin>
          <groupId>io.opentracing.contrib</groupId>
          <artifactId>specialagent-maven-plugin</artifactId>
          <version>0.0.1-SNAPSHOT</version>
          <executions>
            <execution>
              <goals>
                <goal>fingerprint</goal>
              </goals>
              <phase>generate-resources</phase>
              <configuration>
                <destFile>${project.build.directory}/generated-resources/fingerprint.bin</destFile>
              </configuration>
            </execution>
          </executions>
        </plugin>
        ```
3. The package must contain a `dependencies.tgf` file. This file allows the SpecialAgent to distinguish instrumentation plugin dependency JARs from test JARs and API JARs. To generate this file, include the following plugin in the project's POM:
    ```xml
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-dependency-plugin</artifactId>
      <executions>
        <execution>
          <goals>
            <goal>tree</goal>
          </goals>
          <phase>generate-resources</phase>
          <configuration>
            <outputType>tgf</outputType>
            <outputFile>${project.build.directory}/generated-resources/dependencies.tgf</outputFile>
          </configuration>
        </execution>
      </executions>
    </plugin>
    ```

#### Testing

The SpecialAgent provides a convenient methodolofy for testing of the auto-instrumentation of plugins via `AgentRunner`. Please refer to the section on [Test Usage](#test-usage) for instructions.

#### Including the Instrumentation Plugin in the SpecialAgent

Instrumentation plugins must be explicitly packaged into the main JAR of the SpecialAgent. Please refer to the `<id>deploy</id>` profile in the [`POM`](https://github.com/opentracing-contrib/java-specialagent/blob/master/opentracing-specialagent/pom.xml) for an example of the usage.

### Building

The SpecialAgent is built in 2 profiles:

1. The `default` profile is used for development of plugins. It builds and runs tests for each plugin, but _does not include the plugins_ in `opentracing-specialagent-<version>.jar`

    To run this profile:
    ```bash
    mvn clean install
    ```

2. The `deploy` profile is used for packaging of plugins into the `opentracing-specialagent-<version>.jar`. It builds each plugin, but does not run their tests. Once the build is finished, the `opentracing-specialagent-<version>.jar` will contain the built plugins inside it.

    To run this profile:
    ```bash
    mvn -Ddeploy clean install
    ```

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

### License

This project is licensed under the Apache 2 License - see the [LICENSE.txt](LICENSE.txt) file for details.