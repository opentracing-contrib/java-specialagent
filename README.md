# OpenTracing-UberJar

## Overview

**OpenTracing-UberJar** is a package that intends to allow automatic instrumentation of 3rd-party libraries used in java applications. This document contains the operational instructions for the use of opentracing-uberjar.

## Goals

1) The **OpenTracing-UberJar** must not involve any modification of application code for which it is intended to be used.
2) The **OpenTracing-UberJar** must allow any instrumentation plugin made available in opentracing-contrib to be automatically instrumentable in applications that utilize a 3rd-party library for which an instrumentation plugin exists.
3) The **OpenTracing-UberJar** must operate correctly regardless in which ClassLoader the 3rd-party library is loaded.
4) The **OpenTracing-UberJar** must not adversely affect the executional stability of the application on which it is intended to be used. This goal applies only to the code in **OpenTracing-UberJar**, and does not apply to the code of the instrumentation plugins made available in opentracing-contrib.

## Usage

The **OpenTracing-UberJar** uses Java’s Instrumentation interface to transform the behavior of a target application. The entrypoint into the target application is performed via Java’s Agent convention. **OpenTracing-UberJar** supports both static and dynamic attach.

### Static Attach

Statically attaching to a Java application involves the use of the -javaagent vm argument at the time of startup of the target Java application. The following command can be used as an example:

```bash
java -javaagent:opentracing-uberjar.jar -jar myapp.jar
```

This command statically attaches **OpenTracing-UberJar** into the application in myapp.jar.

### Dynamic Attach

Dynamically attaching to a Java application involves the use of a running application’s PID, after the application’s startup. The following commands can be used as an example:

```bash
jps # Call this to obtain the PID of the target application
java -jar opentracing-uberjar.jar <PID> # Replate <PID> with the PID from jps
```

## Operation

When **OpenTracing-UberJar** attaches to an application, either statically or dynamically, it will automatically load the OpenTracing instrumentation plugins explicitly specified as dependencies in its POM.

Any exception that occurs during the execution of the bootstrap process will not adversely affect the stability of the target application. It is, however, possible to that the instrumentation plugin code may result in exceptions that are not properly handled, and would destabilize the target application.