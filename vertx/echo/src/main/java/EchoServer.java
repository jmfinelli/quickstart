/*
   Copyright The Narayana Authors
   SPDX-License-Identifier: Apache-2.0
 */

import io.vertx.core.Vertx;
import io.vertx.core.AbstractVerticle;

/*
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EchoServer extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    vertx.deployVerticle(new EchoServer());
  }

  @Override
  public void start() throws Exception {

    vertx.createNetServer().connectHandler(sock -> {

      // Pipe the socket to itself (echo)
      sock.pipeTo(sock);

    }).listen(1234);

    System.out.println("Echo server is now listening");

  }
}
