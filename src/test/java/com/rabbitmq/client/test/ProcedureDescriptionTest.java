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

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.tools.jsonrpc.ProcedureDescription;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class ProcedureDescriptionTest {

  private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

  static class ReturnType {
    static {
      INITIALIZED.set(true);
    }
  }

  @Test
  public void returnTypeIsLoadedWithoutBeingInitialized() {
    ProcedureDescription description = new ProcedureDescription();
    description.setJavaReturnType(ReturnType.class.getName());
    assertThat(description.getReturnType().getName()).isEqualTo(ReturnType.class.getName());
    assertThat(INITIALIZED.get()).isFalse();
  }
}
