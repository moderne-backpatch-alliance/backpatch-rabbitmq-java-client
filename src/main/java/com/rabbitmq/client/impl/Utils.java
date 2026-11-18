// Copyright (c) 2019-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MalformedFrameException;

import static java.lang.String.format;

final class Utils {

  private Utils() {}

  // A frameMax of 0 means "no limit" in connection.tune, not a literal limit of 0,
  // so it must not win the Math.min and discard the configured body cap.
  static int inboundFrameMax(int maxInboundMessageBodySize, int frameMax) {
    int effectiveFrameMax = frameMax == 0 ? Integer.MAX_VALUE : frameMax;
    return Math.min(maxInboundMessageBodySize, effectiveFrameMax);
  }

  static int framePayloadLimit(int frameMax) {
    if (frameMax <= 0) {
      return Integer.MAX_VALUE;
    } else if (frameMax < AMQP.FRAME_MIN_SIZE) {
      return AMQP.FRAME_MIN_SIZE - AMQCommand.EMPTY_FRAME_SIZE;
    } else {
      return frameMax - AMQCommand.EMPTY_FRAME_SIZE;
    }
  }

  static void enforceFrameMax(int framePayloadSize, int framePayloadLimit) throws MalformedFrameException {
    if (framePayloadSize < 0 || framePayloadSize > framePayloadLimit) {
      throw new MalformedFrameException(
          format(
              "Frame size is invalid (%d), maximum configured size is %d. "
                  + "See ConnectionFactory#setMaxInboundMessageBodySize "
                  + "if you need to increase the limit.",
              frameSizeFromPayloadSize(framePayloadSize), frameSizeFromPayloadSize(framePayloadLimit)));
    }
  }

  private static int frameSizeFromPayloadSize(int limit) {
    return limit + AMQCommand.EMPTY_FRAME_SIZE;
  }
}
