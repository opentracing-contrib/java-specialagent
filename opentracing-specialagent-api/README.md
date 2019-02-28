# SpecialAgent Plugin API

> API for auto-instrumentation of OpenTracing instrumentation plugins

### Developing Instrumentation Plugins for <ins>SpecialAgent</ins>

The [opentracing-contrib][opentracing-contrib] organization contains 40+ OpenTracing instrumentation plugins for Java. Only a handful of these plugins are currently [supported by SpecialAgent](#supported-instrumentation-plugins).

If you are interested in contributing to the <ins>SpecialAgent</ins> project by integrating support for existing plugins in the [opentracing-contrib][opentracing-contrib] organization, or by implementing a new plugin with support for <ins>SpecialAgent</ins>, the following guide is for you:...

## Overview

This project provides the API for instrumentation plugins to integrate into _SpecialAgent_'s auto-instrumentation hooks. The API is a light wrapper on top of [ByteBuddy](http://bytebuddy.net/), which enables a plugin developer to use the full breadth of ByteBuddy's @Advice intercept API.

#### Implementing the Instrumentation Logic

The [opentracing-contrib][opentracing-contrib] organization contains instrumentation plugins for a wide variety of 3rd-party libraries, as well as Java standard APIs. The plugins instrument a 3rd-party library of interest by implementing custom library-specific hooks that integrate with the OpenTracing API. To see examples, explore projects named with the prefix **java-...** in the [opentracing-contrib][opentracing-contrib] organization.

#### Implementing the Auto-Instrumentation Rules

The <ins>SpecialAgent</ins> uses ByteBuddy as the re/transformation manager for auto-instrumentation. This module defines the API and patterns for implementation of auto-instrumentation rules for OpenTracing Instrumentation Plugins.

## Usage

The _SpecialAgent Plugin API_ is intended to be integrated into an OpenTracing instrumentation plugin.

1. **Add the `opentracing-specialagent-api` and `bytebuddy` dependencies to the project's POM**

    Ensure the dependency scope is set to `provided`.

    ```xml
    <dependency>
      <groupId>io.opentracing.contrib</groupId>
      <artifactId>opentracing-specialagent-api</artifactId>
      <version>${project.version}</version>
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

2. **Important note!**

    The instrumentation plugin is instrumenting a 3rd-party library. This library is guaranteed to be present in a target runtime for the plugin to be instrumentable (i.e. if the plugin finds its way to a runtime that does not have the 3rd-party library, its presence is moot). For non-moot use-cases, since the 3rd-party library is guaranteed to be present, it is important that the dependency scope for the 3rd-party library artifacts is set to `provided`. This will prevent from runtime linkage errors due to duplicate class definitions in different class loaders.

3. **Implement the `AgentPlugin` interface**

    The `AgentPlugin` interface defines one method:

    ```java
    Iterable<? extends AgentBuilder> buildAgent(String agentArgs) throws Exception;
    ```

    An example implementation for an instrumentation plugin that instruments the `com.example.TargetBuilder#build(String)` method in an example 3rd-party library:

    ```java
      public class TargetAgentPlugin implements AgentPlugin {
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
                  .to(TargetAgentPlugin.class)            // A class literal reference to this class.
                  .on(named("builder")                    // The method name which to intercept on the "com.example.TargetBuilder" class.
                    .and(takesArguments(String.class)))); // Additional specification for the method intercept.
              }}));
          }

          // The @Advice method that defines the intercept callback. It is important this method does not require any
          // classes of the 3rd-party library to be loaded, because the classes may not be present in the class loader
          // from where the intercept rule is being defined. All of the OpenTracing instrumentation logic into the
          // 3rd-party library must be defined in the TargetAgentIntercept class (in this example).
          @Advice.OnMethodExit
          public static void exit(@Advice.Origin Method method, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) throws Exception {
            returned = TargetAgentIntercept.exit(returned);
          }
        }

        // This class can reference 3rd-party library classes, because this class will only be loaded at intercept time,
        // where the target object's class loader is guaranteed to have the 3rd-party classes either loaded or on the
        // class path.
        public class TargetAgentIntercept {
          public static Builder exit(final Object returned) {
            // The OpenTracing instrumentation logic
          }
        }
    ```

4. **Create a `otaplugins.txt` file**

    The `otaplugins.txt` file identifies the classes that implement `AgentPlugin`, so that the _SpecialAgent_ knows to load them during startup.

    The `otaplugins.txt` file for this example will be:

    ```java
    io.opentracing.contrib.example.TargetAgentPlugin
    ```

    Multiple `AgentPlugin` implementations can be specified in the `otaplugins.txt` file, each of which will be loaded by _SpecialAgent_ during startup.

    Put the file in `src/main/resources` for it to be found by _SpecialAgent_.

5. **Implement a JUnit test that uses `AgentRunner`**

    Please refer to the [Test Usage](https://github.com/opentracing-contrib/java-specialagent/#test-usage) section in the SpecialAgent.


## `AgentRunner` Usage

The <ins>SpecialAgent</ins> uses the JUnit Runner API to implement a lightweight test methodology that can be easily applied to modules that implement instrumentation for 3rd-party plugins. This runner is named `AgentRunner`, and allows developers to implement tests using vanilla JUnit patterns, transparently providing the following behavior:

1. Launch the test in a process simulating the `-javaagent` vm argument that points to the <ins>SpecialAgent</ins> (in order to test auto-instrumentation functionality).
2. Elevate the test code to be executed from a custom class loader that is disconnected from the system class loader (in order to test bytecode injection into an isolated class loader that cannot resolve classes on the system classpath).
3. Initialize a `MockTracer` as `GlobalTracer`, and provide a reference to the `Tracer` instance in the test method for assertions with JUnit.

The `AgentRunner` is available in the test jar of the <ins>SpecialAgent</ins> module. It can be imported with the following dependency spec:

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

### Configuring `AgentRunner`

The `AgentRunner` can be configured via the `@AgentRunner.Config(...)` annotation. The annotation supports the following properties:

1. `log`<br>The Java Logging Level, which can be set to `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, or `FINEST`.<br>Default: `WARNING`.
2. `events`<br>The re/transformation events to log: `DISCOVERY`, `IGNORED`, `TRANSFORMATION`, `ERROR`, `COMPLETE`.<br>Default: `{}`.
3. `isolateClassLoader`<br>If set to `true`, tests will be run from a class loader that is isolated from the system class loader. If set to `false`, tests will be run from the system class loader.<br>Default: `true`.

#### Packaging

The <ins>SpecialAgent</ins> has specific requirements for packaging of instrumentation plugins:

1. If the library being instrumented is 3rd-party (i.e. it does not belong to the standard Java APIs), then the dependency artifacts for the library must be non-transitive (i.e. declared with `<scope>test</scope>`, or with `<scope>provided</scope>`).
    * The dependencies for the 3rd-party libraries are not necessary when the plugin is applied to a target application, as the application must already have these dependencies for the plugin to be used.
    * Declaring the 3rd-party libraries as non-transitive dependencies greatly reduces the size of the <ins>SpecialAgent</ins> package, as all of the instrumentation plugins as contained within it.
    * If 3rd-party libraries are _not_ declared as non-transitive, there is a risk that target applications may experience class loading exceptions due to inadvertant loading of incompatibile classes.
    * Many of the currently implemented instrumentation plugins _do not_ declare the 3rd-party libraries which they are instrumenting as non-transitive. In this case, an `<exclude>` tag must be specified for each 3rd-party artifact dependency when referring to the instrumentation plugin artifact. An example of this can be seen with the instrumentation plugin for the Mongo Driver [here](https://github.com/opentracing-contrib/java-specialagent/blob/master/plugins/opentracing-specialagent-mongo-driver/pom.xml#L37-L44).
2. The package must contain a `fingerprint.bin` file. This file provides the <ins>SpecialAgent</ins> with a fingerprint of the 3rd-party library that the plugin is instrumenting. This fingerprint allows the <ins>SpecialAgent</ins> to determine if the plugin is compatible with the relevant 3rd-party library in a target application.
    1. To generate the fingerprint, it is first necessary to identify which Maven artifacts are intended to be fingerprinted. To mark an artifact to be fingerprinted, you must add `<optional>true</optional>` to the dependency's spec. Please see the [pom.xml for OkHttp3](https://github.com/opentracing-contrib/java-specialagent/blob/master/plugins/opentracing-specialagent-okhttp/pom.xml) as an example.
    2. Next, include the following plugin in the project's POM:
        ```xml
        <plugin>
          <groupId>io.opentracing.contrib</groupId>
          <artifactId>specialagent-maven-plugin</artifactId>
          <version>0.9.0-SNAPSHOT</version>
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
3. The package must contain a `dependencies.tgf` file. This file allows the <ins>SpecialAgent</ins> to distinguish instrumentation plugin dependency JARs from test JARs and API JARs. To generate this file, include the following plugin in the project's POM:
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

The <ins>SpecialAgent</ins> provides a convenient methodology for testing of the auto-instrumentation of plugins via `AgentRunner`. Please refer to the section on [Test Usage](#test-usage) for instructions.

#### Including the Instrumentation Plugin in the <ins>SpecialAgent</ins>

Instrumentation plugins must be explicitly packaged into the main JAR of the <ins>SpecialAgent</ins>. Please refer to the `<id>deploy</id>` profile in the [`POM`](https://github.com/opentracing-contrib/java-specialagent/blob/master/opentracing-specialagent/pom.xml) for an example of the usage.

## Debugging

The `-Dspecialagent.log.level` system property can be used to set the logging level for <ins>SpecialAgent</ins>. Acceptable values are: `SEVERE`, `WARNING`, `INFO`, `CONFIG`, `FINE`, `FINER`, or `FINEST`, or any numerical log level value is accepted also. The default logging level is set to `WARNING`.

The `-Dspecialagent.log.events` system property can be used to set the re/transformation events to log: `DISCOVERY`, `IGNORED`, `TRANSFORMATION`, `ERROR`, `COMPLETE`. The property accepts a comma-delimited list of event names. By default, no events are logged.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

### License

This project is licensed under the Apache 2 License - see the [LICENSE.txt](LICENSE.txt) file for details.

[opentracing-contrib]: https://github.com/opentracing-contrib/