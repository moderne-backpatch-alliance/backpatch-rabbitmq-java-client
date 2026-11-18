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

package com.rabbitmq.client.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConnectionFactoryConfigurator;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class ConnectionFactoryConfiguratorUriErrorTest {

    private static final String PASSWORD = "s3cret";

    private static Map<String, String> uri(String uri) {
        return Collections.singletonMap("uri", uri);
    }

    @Test
    public void invalidUriDoesNotLeakPassword() {
        // the space makes java.net.URI refuse it
        Map<String, String> properties = uri("amqp://user:" + PASSWORD + " x@host:5672/vhost");
        assertThatThrownBy(() -> ConnectionFactoryConfigurator.load(new ConnectionFactory(), properties, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining(PASSWORD)
            .hasNoCause();
    }

    @Test
    public void noSuchAlgorithmDoesNotLeakPassword() {
        checkRedacted(new NoSuchAlgorithmException("no algorithm"));
    }

    @Test
    public void keyManagementErrorDoesNotLeakPassword() {
        checkRedacted(new KeyManagementException("no key management"));
    }

    private void checkRedacted(Exception failure) {
        ConnectionFactory cf = new ConnectionFactory() {
            @Override
            public void setUri(String uriString) throws NoSuchAlgorithmException, KeyManagementException {
                if (failure instanceof NoSuchAlgorithmException) {
                    throw (NoSuchAlgorithmException) failure;
                }
                throw (KeyManagementException) failure;
            }
        };
        Map<String, String> properties = uri("amqps://user:" + PASSWORD + "@host:5671/vhost");
        assertThatThrownBy(() -> ConnectionFactoryConfigurator.load(cf, properties, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining(PASSWORD)
            .hasMessageContaining("amqps://host:5671/vhost")
            .hasCause(failure);
    }

    @Test
    public void userInfoWithSeveralColonsDoesNotLeakPassword() {
        Map<String, String> properties = uri("amqp://user:" + PASSWORD + ":x@host:5672/vhost");
        assertThatThrownBy(() -> ConnectionFactoryConfigurator.load(new ConnectionFactory(), properties, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining(PASSWORD);
    }

    @Test
    public void validUriStillLoads() {
        ConnectionFactory cf = new ConnectionFactory();
        ConnectionFactoryConfigurator.load(cf, uri("amqp://user:" + PASSWORD + "@host:5672/vhost"), "");
        assertThat(cf.getPassword()).isEqualTo(PASSWORD);
        assertThat(cf.getHost()).isEqualTo("host");
    }
}
