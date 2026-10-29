# Vert.x Shared Data Example

This example demonstrates how to safely share data between verticles using STM (Software Transactional Memory). It shows how STM prevents race conditions and ensures data consistency when multiple verticles access shared objects concurrently.

The example includes a variable called `INSTANCE_CNT` in `ClientVerticle.java` that you can modify to introduce greater concurrency and observe lock contention behavior.

## Running the Example

Build and run the example with:

```bash
mvn clean install
```

The example automatically executes during the `integration-test` phase and demonstrates safe concurrent access to shared data.
