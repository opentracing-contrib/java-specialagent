# SpecialAgent Plugin API

> API for auto-instrumentation of OpenTracing instrumentation plugins

## Overview

This project provides the API for instrumentation plugins to integrate into _SpecialAgent_'s auto-instrumentation hooks. The API is a light wrapper on top of [ByteBuddy](http://bytebuddy.net/), which enables a plugin developer to use the full breadth of ByteBuddy's @Advice intercept API.

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

    The `AgentPlugin` defines one method:

    ```java
    AgentBuilder buildAgent(String agentArgs) throws Exception;
    ```

    An example implementation for an instrumentation plugin that instruments the `com.example.TargetBuilder#build(String)` method in an example 3rd-party library:

    ```java
      public class TargetAgentPlugin implements AgentPlugin {
        public AgentBuilder buildAgent(final String agentArgs) throws Exception {
          return new AgentBuilder.Default()
            // .with(new DebugListener())                 // DebugListener to debug ByteBuddy's transformations.
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
              }});
          }

          // The @Advice method that defines the intercept callback. It is important this method does not require any
          // classes of the 3rd-party library to be loaded, because the classes may not be present in the class loader
          // from where the intercept rule is being defined. All of the OpenTracing instrumentation logic into the
          // 3rd-party library must be defined in the TargetAgentIntercept class (in this example).
          @Advice.OnMethodExit
          public static void exit(@Advice.Origin Method method, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) throws Exception {
            System.out.println(">>>>>> " + method);
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

    ```
    io.opentracing.contrib.example.TargetAgentPlugin
    ```

    Multiple `AgentPlugin` implementations can be specified in the `otaplugins.txt` file, each of which will be loaded by _SpecialAgent_ during startup.

5. **Implement a JUnit test that uses `AgentRunner`**

    Please refer to the [Test Usage](https://github.com/opentracing-contrib/java-specialagent/#test-usage) section in the SpecialAgent.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

### License

This project is licensed under the Apache 2 License - see the [LICENSE.txt](LICENSE.txt) file for details.