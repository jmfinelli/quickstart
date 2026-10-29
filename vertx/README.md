TransactionalVert.x
===================

The vertx sub-project is a port of Mark Little's TransactionalVert.x repo <sup>[1](#history)</sup>. It shows how to use the narayana STM implementation in a Vert.x 3 environment. Integrating STM into Vert.x enables the sharing of objects between verticles in the same Vert.x instance that are safe with respect to concurrent transactional updates.

Take a look at each the README.md files in each sub-directory to see how to use STM for Vert.x:

# docs

  Contains the STM guide

# module 

  STM packaged as a Vert.x module

# echo

  A server verticle which echos back whatever client verticles send.
  The example automatically runs during the `integration-test` phase when you run `mvn clean install`.

# shared

  An example that shows how to safely share data between verticles. 
  There is a variable called INSTANCE\_CNT in ClientVerticle.java that you can change to introduce
  greater concurrency in order to highlight lock contention.

# raw 

  This is not a Vert.x example, rather it is a demonstrator of the locking issues that can occur when using TxOJ (the api on which STM is built).



<a name="githistory">1</a>: To see a history of the commits Mark made to the original repo use the --follow option: git log --follow vertx
