/*
   Copyright The Narayana Authors
   SPDX-License-Identifier: Apache-2.0
 */

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrator that starts the echo server, sends a payload, asserts the echo, and terminates.
 */
public class EchoOrchestrator {

    private static final String TEST_PAYLOAD = "hello-stm";
    private static final int TIMEOUT_SECONDS = 10;

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> errorMessage = new AtomicReference<>();

        // Deploy the echo server
        vertx.deployVerticle(new EchoServer()).onComplete(serverResult -> {
            if (serverResult.succeeded()) {
                System.out.println("EchoServer deployed successfully");

                // Give the server a moment to start listening
                vertx.setTimer(500, timerId -> {
                    // Create a client and connect
                    vertx.createNetClient().connect(1234, "localhost").onComplete(clientResult -> {
                        if (clientResult.succeeded()) {
                            var socket = clientResult.result();

                            // Set up handler to receive the echo
                            socket.handler(buffer -> {
                                String received = buffer.toString("UTF-8");
                                System.out.println("Received echo: " + received);
                                result.set(received);

                                // Close the socket
                                socket.close().onComplete(v -> {
                                    latch.countDown();
                                });
                            });

                            // Send the test payload
                            System.out.println("Sending payload: " + TEST_PAYLOAD);
                            socket.write(Buffer.buffer(TEST_PAYLOAD));

                        } else {
                            errorMessage.set("Failed to connect client: " + clientResult.cause());
                            latch.countDown();
                        }
                    });
                });
            } else {
                errorMessage.set("Failed to deploy server: " + serverResult.cause());
                latch.countDown();
            }
        });

        try {
            // Wait for completion or timeout
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Test timed out after " + TIMEOUT_SECONDS + " seconds");
                vertx.close().onComplete(v -> System.exit(2));
                return;
            }

            // Check for errors
            if (errorMessage.get() != null) {
                System.err.println(errorMessage.get());
                vertx.close().onComplete(v -> System.exit(3));
                return;
            }

            // Assert the echo matches
            String received = result.get();
            if (TEST_PAYLOAD.equals(received)) {
                System.out.println("SUCCESS: Echo assertion passed - received matches sent payload");
                vertx.close().onComplete(v -> System.exit(0));
            } else {
                System.err.println("FAILURE: Echo mismatch");
                System.err.println("  Expected: " + TEST_PAYLOAD);
                System.err.println("  Received: " + received);
                vertx.close().onComplete(v -> System.exit(1));
            }

        } catch (InterruptedException e) {
            System.err.println("Test interrupted: " + e.getMessage());
            vertx.close().onComplete(v -> System.exit(4));
        }
    }
}
