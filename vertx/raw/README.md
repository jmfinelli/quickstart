# TxOJ Locking Demonstration

This is not a Vert.x example but rather a demonstrator of the locking issues that can occur when using TxOJ (the API on which STM is built). It illustrates the low-level transaction semantics and potential concurrency problems.

## Running the Example

Build and run the example with:

```bash
mvn clean install
```

The example automatically executes during the `integration-test` phase, demonstrating TxOJ locking behavior.
