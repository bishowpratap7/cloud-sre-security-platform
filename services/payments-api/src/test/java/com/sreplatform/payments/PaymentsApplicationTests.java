package com.sreplatform.payments;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentsApplicationTests {

    @LocalServerPort
    int port;

    @Test
    void contextLoads() {
        assertThat(port).isPositive();
    }
}
