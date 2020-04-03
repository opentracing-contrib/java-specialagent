# SpecialAgent Rule API

> API for auto-instrumentation of OpenTracing <ins>Integrations</ins>

[![Build Status](https://travis-ci.org/opentracing-contrib/java-specialagent.png)](https://travis-ci.org/opentracing-contrib/java-specialagent)
[![Coverage Status](https://coveralls.io/repos/github/opentracing-contrib/java-specialagent/badge.svg?branch=master)](https://coveralls.io/github/opentracing-contrib/java-specialagent?branch=master)
[![Javadocs](https://www.javadoc.io/badge/io.opentracing.contrib.specialagent/opentracing-specialagent.svg)](https://www.javadoc.io/doc/io.opentracing.contrib.specialagent/opentracing-specialagent)
[![Released Version](https://img.shields.io/maven-central/v/io.opentracing.contrib.specialagent/specialagent.svg)](https://mvnrepository.com/artifact/io.opentracing.contrib.specialagent/opentracing-specialagent)

## Table of Contents

<samp>&nbsp;&nbsp;</samp>1 [Introduction](#1-introduction)<br>
<samp>&nbsp;&nbsp;</samp>2 [Developing <ins>Integration Rules</ins> for <ins>SpecialAgent</ins>](#2-developing-integration-rules-for-specialagent)<br>
<samp>&nbsp;&nbsp;</samp>3 [Implementing the <ins>Integration Rules</ins>](#3-implementing-the-integration-rules)<br>
<samp>&nbsp;&nbsp;</samp>4 [`AgentRule` Usage](#4-agentrule-usage)<br>
<samp>&nbsp;&nbsp;</samp>5 [`AgentRunner` Usage](#5-agentrunner-usage)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;</samp>5.1 [Configuring `AgentRunner`](#51-configuring-agentrunner)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>6 [Packaging](#6-packaging)<br>
<samp>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>6.1 [Including the <ins>Integration Rule</ins> in the <ins>SpecialAgent</ins>](#61-including-the-integration-rule-in-the-specialagent)<br>
<samp>&nbsp;&nbsp;</samp>7 [Compatibility Testing](#7-compatibility-testing)<br>
<samp>&nbsp;&nbsp;</samp>8 [Integration Testing](#8-integration-testing)<br>
<samp>&nbsp;&nbsp;</samp>9 [Debugging](#9-debugging)<br>
<samp>&nbsp;&nbsp;</samp>10 [Contributing](#10-contributing)<br>
<samp>&nbsp;&nbsp;</samp>11 [License](#11-license)

## 1 Introduction

This project provides the API for the implementation of <ins>Integration Rules</ins> that allow <ins>Integrations</ins> to become auto-installable with <ins>SpecialAgent</ins>. The API is a light wrapper on top of [ByteBuddy][bytebuddy], which enables a developer to use the full breadth of ByteBuddy's `@Advice` intercept API.

### 2 Developing <ins>Integration Rules</ins> for <ins>SpecialAgent</ins>

The [opentracing-contrib][opentracing-contrib] organization contains 40+ OpenTracing <ins>Integrations</ins> for Java. Many of these <ins>Integrations</ins> are currently [supported by SpecialAgent](https://github.com/opentracing-contrib/java-specialagent/#41-integrations).

If you are interested in contributing to the <ins>SpecialAgent</ins> project by implementing support for existing <ins>Integrations</ins> in the [opentracing-contrib][opentracing-contrib] organization, or by implementing a new <ins>Integration</ins> with support for <ins>SpecialAgent</ins>, the following guide is for you:...

#### 3 Implementing the <ins>Integration Rules</ins>

The [opentracing-contrib][opentracing-contrib] organization contains <ins>Integrations</ins> for a wide variety of 3rd-party libraries, as well as Java standard APIs. The <ins>Integrations</ins> instrument a 3rd-party library of interest by implementing custom library-specific hooks that integrate with the OpenTracing API. To see examples, explore projects named with the prefix **java-...** in the [opentracing-contrib][opentracing-contrib] organization.

The <ins>SpecialAgent</ins> uses [ByteBuddy][bytebuddy] as the re/transformation manager for auto-instrumentation. This module defines the API and patterns for implementation of <ins>Integration Rules</ins> for OpenTracing <ins>Integrations</ins>.

## 4 `AgentRule` Usage

All <ins>Integration Rules</ins> belong to the [`java-specialagent`](https://github.com/opentracing-contrib/java-specialagent/) codebase, and are coupled to the <ins>SpecialAgent Rule API</ins>. When implementing an <ins>Integration Rule</ins>:

1. **Implement the `AgentRule` interface**

   The `AgentRule` interface defines one method:

   ```java
   Iterable<? extends AgentBuilder> buildAgent(String agentArgs) throws Exception;
   ```

   An example implementation for an <ins>Integration Rule</ins> that instruments the `com.example.TargetBuilder#build(String)` method in an example 3rd-party library:

   ```java
   // This class CANNOT directly reference any 3rd-party library classes, because when this class is loaded, the 3rd-party
   // library will not be available, as it will be loaded in premain.
   public class TargetAgentRule implements AgentRule {
     public AgentBuilder buildAgentChainedGlobal1(final AgentBuilder builder) {
       return builder.                                 // All rules must be based off of the `builder` variable
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
             return
               builder.visit(advice(typeDescription)   // The method `advice(TypeDescription)` is defined in `AgentRule`
               .to(TargetAgentRule.class)              // A class literal reference to this class.
               .on(named("builder")                    // The method name which to intercept on the "com.example.TargetBuilder" class.
                 .and(takesArguments(String.class)))); // Additional specification for the method intercept.
           }}));
       }

     // The @Advice method that defines the intercept callback. It is important this method does not require any
     // classes of the 3rd-party library to be loaded, because the classes may not be present in the class loader
     // from where the intercept rule is being defined. All of the OpenTracing integration logic into the
     // 3rd-party library must be defined in the TargetAgentIntercept class (in this example).
     @Advice.OnMethodExit
     public static void exit(final @ClassName String className, final @Advice.Origin String origin, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) throws Exception {
       if (isAllowed(className, origin))               // The call to isAllowed(className, origin) is required.
         returned = TargetAgentIntercept.exit(returned);
     }
   }

   // This class CAN reference 3rd-party library classes, because this class will only be loaded at intercept time,
   // where the target object's class loader is guaranteed to have the 3rd-party classes either loaded or on the
   // class path.
   public class TargetAgentIntercept {
     public static Builder exit(final Object returned) {
       // The OpenTracing integration logic goes here
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

The <ins>SpecialAgent</ins> uses the JUnit Runner API to implement a lightweight test methodology that can be easily applied to modules that implement instrumentation for 3rd-party libraries. This runner is named `AgentRunner`, and allows developers to implement tests using vanilla JUnit patterns, transparently providing the following behavior:

1. Launch the test in a process simulating the `-javaagent` vm argument that points to the <ins>SpecialAgent</ins> (in order to test auto-instrumentation functionality).
1. Elevate the test code to be executed from a custom class loader that is disconnected from the system class loader (in order to test bytecode injection into an isolated class loader that cannot resolve classes on the system classpath).
1. Initialize a `MockTracer` as `GlobalTracer`, and provide a reference to the `Tracer` instance in the test method for assertions with JUnit.

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

Upon execution of the test class, in either the IDE or with Maven, the `AgentRunner` will execute each test method via the 3 step workflow described above.

### 5.1 Configuring `AgentRunner`

The `AgentRunner` can be configured via the `@AgentRunner.Config(...)` annotation. The annotation supports the following properties:

1. `log`<br>The Java Logging Level, which can be set to `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, or `FINEST`.<br>**Default:** `WARNING`.
1. `events`<br>The re/transformation events to log: `DISCOVERY`, `IGNORED`, `TRANSFORMATION`, `ERROR`, `COMPLETE`.<br>**Default:** `{ERROR}`.
1. `disable`<br>Names of Integration Rules or Trace Exporters to disable during execution.<br>**Default:** `{}`.
1. `properties`<br>System properties to be set in the test runtime.<br>Specification: `{"NAME_1=VALUE_1", "NAME_2=VALUE_2", ..., "NAME_N=VALUE_N"}`.<br>**Default:** `{}`.
1. `verbose`<br>Sets verbose mode for the rule being tested.<br>**Default:** `false`.

## 6 Packaging

The <ins>SpecialAgent</ins> has specific requirements for packaging of <ins>Integration Rules</ins>:

1. **Does the rule have an external JAR that implements the instrumentation logic (i.e. an external <ins>Integration</ins>)?**

   Many <ins>Integration Rules</ins> in <ins>SpecialAgent</ins> have the instrumentation logic implemented in external projects. An example of this is the [OkHttp Integration Rule][okhttp] and [OkHttp Integration][java-okhttp]. This separation is preferred, because it allows the <ins>Integration</ins> ([OkHttp Integration][java-okhttp]) to be used _without <ins>SpecialAgent</ins>_ via manual instrumentation. The [OkHttp Integration Rule][okhttp] therefore is merely a bridge between <ins>SpecialAgent</ins> and the ([OkHttp Integration][java-okhttp]).

   * If the rule you are implementing has an external JAR that implements the instrumentation logic (i.e. an external <ins>Integration</ins>), then its maven dependency must be specified in the rule's POM as:

     ```xml
     <dependency>
       ...
       <optional>true</optional>
       ...
     </dependency>
     ```

     For example, for the [OkHttp Integration Rule][okhttp] the dependency for the [OkHttp Integration][java-okhttp] is:

     ```xml
     <dependency>
       <groupId>io.opentracing.contrib</groupId>
       <artifactId>opentracing-okhttp3</artifactId>
       <optional>true</optional>
       <version>${version.plugin}</version>
       <exclusions>
         <exclusion>
           <groupId>io.opentracing.contrib</groupId>
           <artifactId>opentracing-concurrent</artifactId>
         </exclusion>
         <exclusion>
           <groupId>io.opentracing</groupId>
           <artifactId>opentracing-api</artifactId>
         </exclusion>
       </exclusions>
     </dependency>
     ```

   * If the external <ins>Integration</ins> JAR imports any `io.opentracing:opentracing-*` dependencies, the `io.opentracing.contrib:opentracing-tracerresolver`, or any other OpenTracing dependencies that are guaranteed to be provided by <ins>SpecialAgent</ins>, then these dependencies **MUST BE** excluded in the dependency spec (as shown in the example for OkHttp just above).

     _If this is not done, it may lead to `LinkageError` due to the existence of multiple versions of the same class in different class loaders._

1. **What is the required library that must be present in a target runtime for this rule to be compatible?**

   For <ins>Integration Rules</ins> that have external <ins>Integrations</ins>, this required library is effectively the dependency that the <ins>Integration</ins> uses to implement its instrumentation logic for the specific 3rd-party library.

   For <ins>Integration Rules</ins> that do not have an external <ins>Integrations</ins>, this is the effectively the same. However, in this case, the <ins>Integration Rules</ins> import this/these dependencies directly.

   The <ins>SpecialAgent</ins> needs to know **what is the required library that must be present in a target runtime**, so it can create a `fingerprint.bin` that will later be used to determine compatibility with target runtimes.

   **Required libraries that must be present** must be declared as a dependency in the <ins>Integration Rule's</ins> POM as:

     ```xml
     <dependency>
       ...
       <optional>true</optional>
       <scope>provided</scope>
       ...
     </dependency>
     ```

     For example, for the [OkHttp Rule][okhttp] the required dependency (coming from the [OkHttp Integration][java-okhttp]) is:

     ```xml
     <dependency>
       <groupId>com.squareup.okhttp3</groupId>
       <artifactId>okhttp</artifactId>
       <version>${version.library}</version>
       <optional>true</optional>
       <scope>provided</scope>
     </dependency>
     ```

1. **Important note!**

   _All_ dependencies declared in a <ins>Integration Rule's</ins> POM must have `<optional>true</optional>` spec.

   In case of `<scope>test</scope>` dependencies, this is also true.

1. **Important note!**

   The <ins>Integration Rule</ins> and/or <ins>Integration</ins> is instrumenting a 3rd-party library. This library is guaranteed to be present in a target runtime for the integration to be instrumentable (i.e. if the integration finds its way to a runtime that does not have the 3rd-party library, its presence is moot). For non-moot use-cases, since the 3rd-party library is guaranteed to be present, it is important that the dependency scope for the 3rd-party library artifacts is set to `provided`. This will prevent from runtime linkage errors due to duplicate class definitions in different class loaders.

1. Each rule **MUST** declare a unique name. To declare a name, the rule's `pom.xml` must specify the following:
   ```xml
   <project>
    ...
    <properties>
      <sa.rule.name>NAME</sa.rule.name>
    </properties>
    ...
   </project>
   ```

   The value of `sa.rule.name` must follow the [Rule Name Pattern](https://github.com/opentracing-contrib/java-specialagent/#rule-name-pattern) pattern: `<WORD>[:WORD][:NUMBER]`. The first `<WORD>` is required, the second `[:WORD]` is optional, and the `[:NUMBER]` suffix is also optional. Please refer to the link in the previous sentence for a description of the use and meaning of this spec.

1. Each <ins>Integration Rule</ins> can declare a priority for order when rules are loaded by the SpecialAgent. To declare a priority, the rule's `pom.xml` must specify the following:
   ```xml
   <project>
    ...
    <properties>
      <sa.rule.priority>VALUE</sa.rule.priority>
    </properties>
    ...
   </project>
   ```

   The value of `sa.rule.priority` can be between `0` and `2147483647`. Rules with the highest `sa.rule.priority` value are loaded last (i.e. the order for loading of rules is as per inverse `sa.rule.priority`).

   If the `sa.rule.priority` property is missing, a priority of `0` is used as default.

### 6.1 Including the <ins>Integration Rule</ins> in the <ins>SpecialAgent</ins>

<ins>Integration Rules</ins> must be explicitly packaged into the main JAR of the <ins>SpecialAgent</ins>. Please refer to the `<id>assemble</id>` profile in the [`POM`][specialagent-pom] for an example of the usage.

## 7 Compatibility Testing

<ins>Integration Rules</ins> must provide a spec for compatibility tests. These tests assert compatibility with different versions of a particular 3rd-party library. For instance, with OkHttp, the API significantly changed when it was upgraded from OkHttp3 to OkHttp4. This information is essential for <ins>SpecialAgent</ins> to be able to assert proper functioning of its fingerprinting utility so as to prevent a target runtime from potential failure due to incompatible instrumentation.

The <ins>Compatibility Testing</ins> spec is described for `pass` and `fail` conditions. The `pass` condition requires the compatibility test to pass for a particular 3rd-party library, and the `fail` condition requires the compatibility test to fail.

The POM of each <ins>Integration Rule</ins> must describe at least one `pass` compatibility test. This test can be expressed in 2 forms:

1. **Short Form**

   The Short Form is provided in the `<properties>` element of the <ins>Integration Rule's</ins> POM in a `<passCompatibility>` sub-element. The body of the sub-element must be a `groupId:artifactId:versionRange`. For a description of Version Ranges, please refer to [this link](http://www.mojohaus.org/versions-maven-plugin/examples/resolve-ranges.html).

   For example, the `<passCompatibility>` spec for the [OkHttp Rule][okhttp] is:

   ```xml
   <passCompatibility>com.squareup.okhttp3:okhttp:[3.5.0,]</passCompatibility>
   ```

   This spec says that all versions of the `com.squareup.okhttp3:okhttp` artifact must pass compatibility from `3.5.0` until most current available version in the Maven Central Repository.

   Multiple artifact specs can be provided in a single `<passCompatibility>` element, delimited with the `;` character.

1. **Long Form**

   The Long Form is provided in the `specialagent-maven-plugin` configuration of the <ins>Integration Rule's</ins> POM. This form allows for the description of complex compatibility situations. Let's look at the `spring-messaging` rule as an example:

   ```xml
   <plugin>
     <groupId>io.opentracing.contrib.specialagent</groupId>
     <artifactId>specialagent-maven-plugin</artifactId>
     <executions>
       <execution>
         <id>test-compatibility</id>
         <configuration>
           <passes>
             <pass>
               <dependencies>
                 <dependency>
                   <groupId>org.springframework.integration</groupId>
                   <artifactId>spring-integration-core</artifactId>
                   <version>${min.version}</version> <!-- NOTE: Single version. -->
                 </dependency>
                 <dependency>
                   <groupId>org.springframework</groupId>
                   <artifactId>spring-jcl</artifactId>
                   <version>org.springframework:spring-messaging:[${min.version},]</version>
                 </dependency>
                 <dependency>
                   <groupId>org.springframework</groupId>
                   <artifactId>spring-core</artifactId>
                   <version>org.springframework:spring-messaging:[${min.version},]</version>
                 </dependency>
                 <dependency>
                   <groupId>org.springframework</groupId>
                   <artifactId>spring-messaging</artifactId>
                   <version>org.springframework:spring-messaging:[${min.version},]</version>
                 </dependency>
               </dependencies>
             </pass>
           </passes>
         </configuration>
       </execution>
     </executions>
   </plugin>
   ```

   In this example, the compatibility rule describes a situation where there are 4 Maven artifacts that must be present for the rule to be compatible. The `<version>` element in each dependency can either be a range or a static version. In the first dependency, the `<version>` is static. However, the subsequent 3 are ranges that are all pinned to the available versions of the `org.springframework:spring-messaging` artifact, starting from `${min.version}` and ending with the most recently available version in the Maven Central Repository.

   **Important note!**

   If a complex compatibility spec is needed, then all the versions of each artifact must be "pinned" to a single artifact's version range. This is required, because otherwise it would be impossible to automatically deduce which version of which artifact aligns with a version of another artifact.

## 8 Integration Testing

<ins>Integration Rules</ins> must provide an integration test demonstrating proper functionality of the rule against a runtime with the 3rd-party being instrumented. These tests belong to the [`/test/`](https://github.com/opentracing-contrib/java-specialagent/tree/master/test/) sub-module. These tests resemble the `AgentRunner` tests, but are simpler, because there is no complex class loading architecture involved. The Integration Tests are intended to be as simple as possible, and are only required to demonstrate the proper instrumentation of the particular 3rd-party library.

**Important note!**

All Integration Tests must provide a class with a `public void main(String[] args)` in a sub-package of `io.opentracing.contrib.specialagent.test.<sub-package>`. The `<sub-package>` here should align to the package of the `AgentRule` subclass whose functionality is being asserted.

**Important note!**

Before Integration Tests can be run, the <ins>SpecialAgent</ins> must be built and assembled:

```bash
mvn clean install && mvn -Dassemble install
```

This is required, because the Integration Tests use the packaged SpecialAgent JAR with the `-javaagent:...` argument, as whould be done in a real use-case.

**Important note!**

Once you are ready to commit your integration test, make sure to add it to [`.travis.yml`](https://github.com/opentracing-contrib/java-specialagent/blob/master/.travis.yml). Just copy+paste some other integration test job pair, and modify for your tests.

## 9 Debugging

The `-Dsa.log.level` system property can be used to set the logging level for <ins>SpecialAgent</ins>. Acceptable values are: `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, or `FINEST`, or any numerical log level value is accepted also. The default logging level is set to `WARNING`.

The `-Dsa.log.events` system property can be used to set the re/transformation events to log: `DISCOVERY`, `IGNORED`, `TRANSFORMATION`, `ERROR`, `COMPLETE`. The property accepts a comma-delimited list of event names. By default, the `ERROR` event is logged (only when run with `AgentRunner`).

The `-Dsa.log.file` system property can be used to set the logging output file for <ins>SpecialAgent</ins>.

## 10 Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## 11 License

This project is licensed under the Apache 2 License - see the [LICENSE.txt](LICENSE.txt) file for details.

[bytebuddy]: http://bytebuddy.net/
[mongodriver-pom]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rules/mongo-driver/pom.xml#L37-L44
[okhttp-pom]: https://github.com/opentracing-contrib/java-specialagent/blob/master/rules/okhttp/pom.xml
[opentracing-contrib]: https://github.com/opentracing-contrib/
[specialagent-pom]: https://github.com/opentracing-contrib/java-specialagent/blob/master/opentracing-specialagent/pom.xml

[okhttp]: https://github.com/opentracing-contrib/java-specialagent/tree/master/rule/okhttp
[java-okhttp]: https://github.com/opentracing-contrib/java-okhttp