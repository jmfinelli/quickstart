# Vert.x Echo Example

This example demonstrates a server verticle that echoes back whatever client verticles send to it, using STM to ensure safe concurrent message handling.

## Running the Example

Build and run the example with:

```bash
mvn clean install
```

The example automatically executes during the `integration-test` phase. No manual Vert.x installation is required.
