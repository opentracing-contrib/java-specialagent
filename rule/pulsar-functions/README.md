# SpecialAgent Rule for Apache Pulsar Functions

**Rule Name:** `pulsar:functions`

## Compatibility

```xml
<groupId>org.apache.pulsar</groupId>
<artifactId>pulsar-functions-instance</artifactId>
<version>[2.2.0,2.3.0),(2.4.2,LATEST]</version>
```

## Configuration

Starting the **[Pulsar Broker][broker]** with <ins>SpecialAgent</ins> produces many spans for internal processes.

Excess spans can slow down the broker, which can lead to timeout errors.

If you are not debugging **Pulsar Broker** itself, these spans may not be desired.

In order to reduce the load on the runtime, it is advised to disable all <ins>Integration Rules</ins> except for **Pulsar Broker** itself:

```bash
-Dsa.integration.*.disable
-Dsa.integration.pulsar:*.enable
```
<sup>_**Note:** Properties are interpreted in the order they are specified (i.e. the order of the declaration of properties matters)._</sup>