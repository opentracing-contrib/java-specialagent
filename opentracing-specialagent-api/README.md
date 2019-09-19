# SpecialAgent Rule API

> API for auto-instrumentation of OpenTracing <ins>Instrumentation Plugins</ins>

[![Build Status](https://travis-ci.org/opentracing-contrib/java-specialagent.png)](https://travis-ci.org/opentracing-contrib/java-specialagent)
[![Coverage Status](https://coveralls.io/repos/github/opentracing-contrib/java-specialagent/badge.svg?branch=master)](https://coveralls.io/github/opentracing-contrib/java-specialagent?branch=master)
[![Javadocs](https://www.javadoc.io/badge/io.opentracing.contrib.specialagent/opentracing-specialagent.svg)](https://www.javadoc.io/doc/io.opentracing.contrib.specialagent/opentracing-specialagent)
[![Released Version](https://img.shields.io/maven-central/v/io.opentracing.contrib.specialagent/specialagent.svg)](https://mvnrepository.com/artifact/io.opentracing.contrib.specialagent/opentracing-specialagent)

## Table of Contents

<samp>&nbsp;&nbsp;</samp>1 [Introduction](#1-introduction)<br>
<samp>&nbsp;&nbsp;</samp>2 [Developing <ins>Instrumentation Rules</ins> for <ins>SpecialAgent</ins>](#2-developing-instrumentation-rules-for-specialagent)<br>
<samp>&nbsp;&nbsp;</samp>3 [Implementing the <ins>Instrumentation Rules</ins>](#3-implementing-the-instrumentation-rules)<br>
<samp>&nbsp;&nbsp;</samp>4 [Usage](#4-usage)<br>
<samp>&nbsp;&nbsp;</samp>5 [`AgentRunner` Usage](#5-agentrunner-usage)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>5.1 [Configuring `AgentRunner`](#51-configuring-agentrunner)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>5.1.1 [Packaging](#511-packaging)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>5.1.2 [Testing](#512-testing)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>5.1.3 [Including the <ins>Instrumentation Rule</ins> in the <ins>SpecialAgent</ins>](#513-including-the-instrumentation-rule-in-the-specialagent)<br>
<samp>&nbsp;&nbsp;</samp>6 [Debugging](#6-debugging)<br>
<samp>&nbsp;&nbsp;</samp>7 [Contributing](#7-contributing)<br>
<samp>&nbsp;&nbsp;</samp>8 [License](#8-license)

## 1 Introduction

This project provides the API for <ins>Instrumentation Plugins</ins> to integrate into <ins>SpecialAgent</ins>'s auto-instrumentation rules. The API is a light wrapper on top of [ByteBuddy][bytebuddy], which enables a plugin developer to use the full breadth of ByteBuddy's `@Advice` intercept API.

### 2 Developing <ins>Instrumentation Rules</ins> for <ins>SpecialAgent</ins>

The [opentracing-contrib][opentracing-contrib] organization contains 40+ OpenTracing <ins>Instrumentation Plugins</ins> for Java. Only a handful of these plugins are currently [supported by SpecialAgent](#supported-instrumentation-plugins).

If you are interested in contributing to the <ins>SpecialAgent</ins> project by integrating support for existing plugins in the [opentracing-contrib][opentracing-contrib] organization, or by implementing a new plugin with support for <ins>SpecialAgent</ins>, the following guide is for you:...

#### 3 Implementing the <ins>Instrumentation Rules</ins>

The [opentracing-contrib][opentracing-contrib] organization contains <ins>Instrumentation Plugins</ins> for a wide variety of 3rd-party libraries, as well as Java standard APIs. The plugins instrument a 3rd-party library of interest by implementing custom library-specific hooks that integrate with the OpenTracing API. To see examples, explore projects named with the prefix **java-...** in the [opentracing-contrib][opentracing-contrib] organization.

The <ins>SpecialAgent</ins> uses ByteBuddy as the re/transformation manager for auto-instrumentation. This module defines the API and patterns for implementation of auto-instrumentation rules for OpenTracing <ins>Instrumentation Plugins</ins>.

## 4 Usage

The <ins>SpecialAgent Rule API</ins> is intended to be integrated into an OpenTracing <ins>Instrumentation Plugin</ins>.

1. **Add the `opentracing-specialagent-api` and `bytebuddy` dependencies to the project's POM**

   Ensure the dependency scope is set to `provided`.

   ```xml
   <dependency>
     <groupId>io.opentracing.contrib.specialagent</groupId>
     <artifactId>opentracing-specialagent-api</artifactId>
     <version>1.3.6</version>
     <scope>provided</scope>
   </dependency>
   <dependency>
     <groupId>net.bytebuddy</groupId>
     <artifactId>byte-buddy</artifactId>
     <scope>provided</scope>
   </dependency>
   <dependency>
     <groupId>net.bytebuddy</groupId>
     <artifactId>byte-buddy-agent</artifactId>
     <scope>provided</scope>
   </dependency>
   ```

1. **Important note!**

   The <ins>Instrumentation Plugin</ins> is instrumenting a 3rd-party library. This library is guaranteed to be present in a target runtime for the plugin to be instrumentable (i.e. if the plugin finds its way to a runtime that does not have the 3rd-party library, its presence is moot). For non-moot use-cases, since the 3rd-party library is guaranteed to be present, it is important that the dependency scope for the 3rd-party library artifacts is set to `provided`. This will prevent from runtime linkage errors due to duplicate class definitions in different class loaders.

1. **Implement the `AgentRule` interface**

   The `AgentRule` interface defines one method:

   ```java
   Iterable<? extends AgentBuilder> buildAgent(String agentArgs) throws Exception;
   ```

   An example implementation for an <ins>Instrumentation Rule</ins> that instruments the `com.example.TargetBuilder#build(String)` method in an example 3rd-party library:

   ```java
     public class TargetAgentRule implements AgentRule {
       public Iterable<? extends AgentBuilder> buildAgent(final String agentArgs) throws Exception {
         return Arrays.asList(new AgentBuilder.Default()
           .with(RedefinitionStrategy.RETRANSFORMATION)  // Allows loaded classes to be retransformed.
           .with(InitializationStrategy.NoOp.INSTANCE)   // Singleton instantiation of loaded type initializers.
           .with(TypeStrategy.Default.REDEFINE)          // Allows loaded classes to be redefined.
           .type(named("com.example.TargetBuilder"))     // The type name to be intercepted. It is important that
                                                         // the class name is expressed in string form, as opposed
                                                         // to is(com.example.TargetBuilder.class), or
                                                         // named(com.example.TargetBuilder.class.getName()).
                                                         // Directly referencing the class will cause the JVM to
                                                         // attempt to load the class when the intercept rule is
                                                         // being defined. Such an operation may fail, because the
                                                         // class may not be present in the class loader from where
                                                         // the intercept rule is being defined.
           .transform(new Transformer() {
             @Override
             public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
               return builder.visit(Advice
                 .to(TargetAgentRule.class)            // A class literal reference to this class.
                 .on(named("builder")                    // The method name which to intercept on the "com.example.TargetBuilder" class.
                   .and(takesArguments(String.class)))); // Additional specification for the method intercept.
             }}));
         }

         // The @Advice method that defines the intercept callback. It is important this method does not require any
         // classes of the 3rd-party library to be loaded, because the classes may not be present in the class loader
         // from where the intercept rule is being defined. All of the OpenTracing instrumentation logic into the
         // 3rd-party library must be defined in the TargetAgentIntercept class (in this example).
         @Advice.OnMethodExit
         public static void exit(@Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) throws Exception {
           if (AgentRuleUtil.isEnabled())              // Prevents the SpecialAgent from instrumenting the tracer itself.
             returned = TargetAgentIntercept.exit(returned);
         }
       }

       // This class can reference 3rd-party library classes, because this class will only be loaded at intercept time,
       // where the target object's class loader is guaranteed to have the 3rd-party classes either loaded or on the
       // class path.
       public class TargetAgentIntercept {
         public static Builder exit(final Object returned) {
           // The OpenTracing instrumentation logic goes here
         }
       }
   ```

1. **Create a `otarules.mf` file**

   The `otarules.mf` file identifies the classes that implement `AgentRule`, so that the <ins>SpecialAgent</ins> knows to load them during startup.

   The `otarules.mf` file for this example will be:

   ```java
   io.opentracing.contrib.example.TargetAgentRule
   ```

   Multiple `AgentRule` implementations can be specified in the `otarules.mf` file, each of which will be loaded by <ins>SpecialAgent</ins> during startup.

   Put the file in `src/main/resources` for it to be found by <ins>SpecialAgent</ins>.

1. **Implement a JUnit test that uses `AgentRunner`**

   Please refer to the [`AgentRunner` Usage](#5-agentrunner-usage) section in the <ins>SpecialAgent</ins>.

## 5 `AgentRunner` Usage

The <ins>SpecialAgent</ins> uses the JUnit Runner API to implement a lightweight test methodology that can be easily applied to modules that implement instrumentation for 3rd-party plugins. This runner is named `AgentRunner`, and allows developers to implement tests using vanilla JUnit patterns, transparently providing the following behavior:

1. Launch the test in a process simulating the `-javaagent` vm argument that points to the <ins>SpecialAgent</ins> (in order to test auto-instrumentation functionality).
1. Elevate the test code to be executed from a custom class loader that is disconnected from the system class loader (in order to test bytecode injection into an isolated class loader that cannot resolve classes on the system classpath).
1. Initialize a `MockTracer` as `GlobalTracer`, and provide a reference to the `Tracer` instance in the test method for assertions with JUnit.

The `AgentRunner` is available in the test jar of the <ins>SpecialAgent</ins> module. It can be imported with the following dependency spec:

```xml
<dependency>
  <groupId>io.opentracing.contrib.specialagent</groupId>
  <artifactId>opentracing-specialagent</artifactId>
  <version>1.3.6</version>
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

To use the `AgentRunner` in a JUnit test class, provide the following annotation to the class in question:

```java
@RunWith(AgentRunner.class)
```

The `AgentRunner` can provide each test method in the test class with a reference to the `Tracer` instance that is registered with `GlobalTracer`. If the test class does not explicitly register the `Tracer` instance with `GlobalTracer`, a `MockTracer` instance is registered by default.

In addition to the `@RunWith` annotation, each method annotated with `@Test` _may_ declare a parameter of type `Tracer`. If a `MockTracer` is registered with `GlobalTracer`, the following method signature is acceptable:

```java
@Test
public void test(MockTracer tracer) {}
```

Similarly, each method annotated with `@Before`, `@After`, `@BeforeClass`, and `@AfterClass` _may_ declare a parameter of type `Tracer`. If `MockTracer` is the registered `Tracer`, the following method signatures are acceptable:

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

### 5.1 Configuring `AgentRunner`

The `AgentRunner` can be configured via the `@AgentRunner.Config(...)` annotation. The annotation supports the following properties:

1. `log`<br>The Java Logging Level, which can be set to `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, or `FINEST`.<br>**Default:** `WARNING`.
1. `events`<br>The re/transformation events to log: `DISCOVERY`, `IGNORED`, `TRANSFORMATION`, `ERROR`, `COMPLETE`.<br>**Default:** `{ERROR}`.
1. `disable`<br>Names of plugins to disable during execution.<br>**Default:** `{}`.
1. `verbose`<br>Sets verbose mode for the plugin being tested.<br>**Default:** `false`.
1. `isolateClassLoader`<br>If set to `true`, tests will be run from a class loader that is isolated from the system class loader. If set to `false`, tests will be run from the system class loader.<br>**Default:** `true`.

#### 5.1.1 Packaging

The <ins>SpecialAgent</ins> has specific requirements for packaging of <ins>Instrumentation Rules</ins>:

1. If the library being instrumented is 3rd-party (i.e. it does not belong to the standard Java APIs), then the dependency artifacts for the library must be non-transitive (i.e. declared with `<scope>test</scope>`, or with `<scope>provided</scope>`).
   * The dependencies for the 3rd-party libraries are not necessary when the plugin is applied to a target application, as the application must already have these dependencies for the plugin to be used.
   * Declaring the 3rd-party libraries as non-transitive dependencies greatly reduces the size of the <ins>SpecialAgent</ins> package, as all of the <ins>Instrumentation Plugins</ins> as contained within it.
   * If 3rd-party libraries are _not_ declared as non-transitive, there is a risk that target applications may experience class loading exceptions due to inadvertant loading of incompatibile classes.
   * Many of the currently implemented <ins>Instrumentation Plugins</ins> _do not_ declare the 3rd-party libraries which they are instrumenting as non-transitive. In this case, an `<exclude>` tag must be specified for each 3rd-party artifact dependency when referring to the <ins>Instrumentation Plugin</ins> artifact. An example of this can be seen with the [Mongo Driver Plugin][mongodriver-pom].
1. The package must contain a `fingerprint.bin` file. This file provides the <ins>SpecialAgent</ins> with a fingerprint of the 3rd-party library that the plugin is instrumenting. This fingerprint allows the <ins>SpecialAgent</ins> to determine if the plugin is compatible with the relevant 3rd-party library in a target application.
   1. To generate the fingerprint, it is first necessary to identify which Maven artifacts are intended to be fingerprinted. To mark an artifact to be fingerprinted, you must add `<optional>true</optional>` to the dependency's spec. Please see the [pom.xml for OkHttp3][okhttp-pom] as an example.
   1. Next, include the following plugin in the project's POM:
      ```xml
      <plugin>
        <groupId>io.opentracing.contrib.specialagent</groupId>
        <artifactId>agentrule-maven-plugin</artifactId>
        <version>1.3.6</version>
        <executions>
          <execution>
            <goals>
              <goal>fingerprint</goal>
            </goals>
            <phase>process-classes</phase>
            <configuration>
              <name>**NAME OF THE PLUGIN**</name>
            </configuration>
          </execution>
        </executions>
      </plugin>
      ```
      The `<name>` property specifies the name of the plugin. This name will be used by users to configure the plugin.

#### 5.1.2 Testing

The <ins>SpecialAgent</ins> provides a convenient methodology for testing of the auto-instrumentation of plugins via `AgentRunner`. Please refer to the section on [`AgentRunner` Usage](#5-agentrunner-usage) for instructions.

#### 5.1.3 Including the <ins>Instrumentation Rule</ins> in the <ins>SpecialAgent</ins>

<ins>Instrumentation Rules</ins> must be explicitly packaged into the main JAR of the <ins>SpecialAgent</ins>. Please refer to the `<id>assemble</id>` profile in the [`POM`][specialagent-pom] for an example of the usage.

## 6 Debugging

The `-Dsa.log.level` system property can be used to set the logging level for <ins>SpecialAgent</ins>. Acceptable values are: `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, or `FINEST`, or any numerical log level value is accepted also. The default logging level is set to `WARNING`.

The `-Dsa.log.events` system property can be used to set the re/transformation events to log: `DISCOVERY`, `IGNORED`, `TRANSFORMATION`, `ERROR`, `COMPLETE`. The property accepts a comma-delimited list of event names. By default, the `ERROR` event is logged (only when run with `AgentRunner`).

The `-Dsa.log.file` system property can be used to set the logging output file for <ins>SpecialAgent</ins>.

## 7 Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## 8 License

This project is licensed under the Apache 2 License - see the [LICENSE.txt](LICENSE.txt) file for details.

[bytebuddy]: http://bytebuddy.net/
[mongodriver-pom]: https://github.com/opentracing-contrib/java-specialagent/blob/master/rules/specialagent-mongo-driver/pom.xml#L37-L44
[okhttp-pom]: https://github.com/opentracing-contrib/java-specialagent/blob/master/rules/specialagent-okhttp/pom.xml
[opentracing-contrib]: https://github.com/opentracing-contrib/
[specialagent-pom]: https://github.com/opentracing-contrib/java-specialagent/blob/master/opentracing-specialagent/pom.xml