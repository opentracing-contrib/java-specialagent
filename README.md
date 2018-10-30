# Java Agent for OpenTracing,<br>with automatic instrumentation

> Automatically perform Java instrumentation for 3rd-party libraries

## Overview

_Java Instrumenter_ is a package that intends to allow automatic instrumentation for 3rd-party libraries in Java applications. This file contains the operational instructions for the use of _Java Instrumenter_.

## Operation

When _Java Instrumenter_ attaches to an application, either statically or dynamically, it will automatically load the OpenTracing instrumentation plugins explicitly specified as dependencies in its POM.

Any exception that occurs during the execution of the bootstrap process will not adversely affect the stability of the target application. It is, however, possible that the instrumentation plugin code may result in exceptions that are not properly handled, and would destabilize the target application.

## Goals

1) The _Java Instrumenter_ must allow any Java instrumentation plugin available in `opentracing-contrib` to be automatically installable in applications that utilize a 3rd-party library for which an instrumentation plugin exists.
2) The _Java Instrumenter_ must automatically install the instrumentation plugin for each 3rd-party library for which a plugin exists, regardless in which `ClassLoader` the 3rd-party library is loaded.
3) The _Java Instrumenter_ must not adversely affect the runtime stability of the application on which it is intended to be used. This goal applies only to the code in the _Java Instrumenter_, and cannot apply to the code of the instrumentation plugins made available in `opentracing-contrib`.
4) The _Java Instrumenter_ must support static and dynamic attach to applications running on JVM versions 1.7 to latest.
5) The _Java Instrumenter_ must implement a lightweight test methodology that can be easily applied to a module that implements instrumentation for a 3rd-party plugin. This test must simulate:
   1) Launch the test in a process with the `-javaagent` vm argument that points to the _Java Instrumenter_ (in order to test automatic instrumentation functionality of the `otarules.btm` file).
   2) Elevate the test code to be executed from a custom `ClassLoader` that is disconnected from the system `ClassLoader` (in order to test bytecode injection into an isolated `ClassLoader` that cannot resolve classes on the system classpath).
   3) Initialize a `MockTracer` as `GlobalTracer`, and provide a reference to the `Tracer` instance in the test method for assertions with JUnit.
The _Java Instrumenter_ must provide a means by which instrumentation plugins can be configured before use on a target application. 

## Non-Goals

1) The _Java Instrumenter_ is not designed to modify application code, beyond the installation of OpenTracing instrumentation plugins. For example, there is no facility for dynamically tracing arbitrary code.

## Installation

The _Java Instrumenter_ is built with Maven, and produces 2 artifacts:
1) `opentracing-instrumenter-<version>.jar`

    This is the main artifact that contains within it all applicable instrumentation plugins from the `opentracing-contrib` project. This JAR can be specified as the `-javaagent` target for static attach to an application. This JAR can also be executed, standalone, with an argument representing the PID of a target process to which it should dynamically attach.

1) `opentracing-instrumenter-<version>-tests.jar`

    This is the test artifact that contains within it the `InstrumenterRunner`, which is a JUnit runner class provided for testing of `otarules.btm` files in instrumentation plugins. This JAR does not contain within it any instrumentation plugins themselves, and is only intended to be applied to the test phase of the build lifecycle of a single instrumentation plugin implementation. This JAR can be used in the same manner as the main JAR, both for static and dynamic attach.

## Usage

The _Java Instrumenter_ uses Java’s Instrumentation interface to transform the behavior of a target application. The entrypoint into the target application is performed via Java’s Agent convention. _Java Instrumenter_ supports both static and dynamic attach.

### Static Attach

Statically attaching to a Java application involves the use of the `-javaagent` vm argument at the time of startup of the target Java application. The following command can be used as an example:

```bash
java -javaagent:opentracing-instrumenter.jar -jar myapp.jar
```

This command statically attaches _Java Instrumenter_ into the application in `myapp.jar`.

### Dynamic Attach

Dynamically attaching to a Java application involves the use of a running application’s PID, after the application’s startup. The following commands can be used as an example:

```bash
jps # Call this to obtain the PID of the target application
java -jar opentracing-instrumenter.jar <PID> # Replate <PID> with the PID from jps
```

## Test Usage

The _Java Instrumenter_ uses the JUnit Runner API to implement a lightweight test methodology that can be easily applied to modules that implement instrumentation for 3rd-party plugins. This runner is named `InstrumenterRunner`, and allows developers to implement tests using vanilla JUnit patterns, transparently providing the following behavior:

1) Launch the test in a process with the `-javaagent` vm argument that points to the _Java Instrumenter_ (in order to test automatic instrumentation functionality of the `otarules.btm` file).
2) Elevate the test code to be executed from a custom `ClassLoader` that is disconnected from the system `ClassLoader` (in order to test bytecode injection into an isolated `ClassLoader` that cannot resolve classes on the system classpath).
3) Initialize a `MockTracer` as `GlobalTracer`, and provide a reference to the `Tracer` instance in the test method for assertions with JUnit.

The `InstrumenterRunner` is available in the test jar of the _Java Instrumenter_ module. It can be imported with the following dependency spec:

```xml
<dependency>
  <groupId>io.opentracing.contrib</groupId>
  <artifactId>opentracing-instrumenter</artifactId>
  <version>${project.version}</version>
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

To use the `InstrumenterRunner` in a JUnit test class, provide the following annotation to the class in question:

```java
@RunWith(InstrumenterRunner.class)
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

Upon execution of the test class, in either the IDE or with Maven, the `InstrumenterRunner` will execute each test method via the 3 step workflow described above.

The `InstrumenterRunner` also provides a means by which debug logging can be turned on in case of unexpected failures. To turn on debug logging within the runner, include the following annotation on the test class:

```java
@InstrumenterRunner.Debug(true)
```