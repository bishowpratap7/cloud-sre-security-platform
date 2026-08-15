package com.sreplatform.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrdersApplicationTests {

    @Autowired
    TestRestTemplate rest;

    @Test
    void contextLoadsAndServesOrders() {
        ResponseEntity<String> orders = rest.getForEntity("/orders", String.class);
        assertThat(orders.getStatusCode().value()).isEqualTo(200);
        assertThat(orders.getBody()).contains("ord_");

        ResponseEntity<String> missing = rest.getForEntity("/orders/does-not-exist", String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<String> readiness = rest.getForEntity("/actuator/health/readiness", String.class);
        assertThat(readiness.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(readiness.getBody()).contains("\"UP\"");
    }
}
