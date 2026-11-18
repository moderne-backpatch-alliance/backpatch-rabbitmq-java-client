// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UtilsTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    // maxInboundMessageBodySize, frameMax, expected
    return Arrays.asList(
        new Object[][] {
          {65536, 0, 65536}, // negotiated frame_max of 0 means "no limit", body cap must still apply
          {65536, 16384, 16384}, // frame_max smaller than the body cap wins
          {16384, 65536, 16384}, // body cap smaller than frame_max wins
          {65536, 65536, 65536},
        });
  }

  private final int maxInboundMessageBodySize;
  private final int frameMax;
  private final int expected;

  public UtilsTest(int maxInboundMessageBodySize, int frameMax, int expected) {
    this.maxInboundMessageBodySize = maxInboundMessageBodySize;
    this.frameMax = frameMax;
    this.expected = expected;
  }

  @Test
  public void inboundFrameMaxHonoursFrameMaxZeroAsUnlimited() {
    assertThat(Utils.inboundFrameMax(maxInboundMessageBodySize, frameMax)).isEqualTo(expected);
  }
}
