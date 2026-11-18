// Copyright (c) 2017-2020 VMware, Inc. or its affiliates.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.RpcServer;
import java.io.IOException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class RpcServerMainloopTest {

  @Test
  public void failingRequestIsRejectedAndTheLoopKeepsRunning() throws Exception {
    Channel channel = mock(Channel.class);
    RpcServer server =
        new RpcServer(channel, "q") {
          @Override
          public byte[] handleCall(Delivery request, AMQP.BasicProperties replyProperties) {
            if ("boom".equals(new String(request.getBody()))) {
              throw new IllegalArgumentException("cannot handle this request");
            }
            terminateMainloop();
            return new byte[0];
          }
        };
    ArgumentCaptor<Consumer> consumer = ArgumentCaptor.forClass(Consumer.class);
    verify(channel).basicConsume(anyString(), consumer.capture());

    deliver(consumer.getValue(), 1, "boom");
    deliver(consumer.getValue(), 2, "ok");
    server.mainloop();

    verify(channel).basicReject(1L, false);
    verify(channel, never()).basicAck(1L, false);
    verify(channel).basicAck(2L, false);
    verify(channel).basicPublish(anyString(), anyString(), any(AMQP.BasicProperties.class), any(byte[].class));
  }

  private static void deliver(Consumer consumer, long tag, String body) throws IOException {
    AMQP.BasicProperties properties =
        new AMQP.BasicProperties.Builder().correlationId("c").replyTo("r").build();
    consumer.handleDelivery("ctag", new Envelope(tag, false, "", "q"), properties, body.getBytes());
  }
}
