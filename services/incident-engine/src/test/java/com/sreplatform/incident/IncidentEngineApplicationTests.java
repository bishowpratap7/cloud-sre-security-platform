package com.sreplatform.incident;

import com.sreplatform.incident.config.ServiceTarget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.monitored-services=payments-api=http://localhost:1,6,1.8.3;orders-api=http://localhost:2,3,2.4.1")
class IncidentEngineApplicationTests {

    @Autowired
    TestRestTemplate rest;

    @Test
    void contextLoads() {
        ResponseEntity<String> incidents = rest.getForEntity("/incidents", String.class);
        assertThat(incidents.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> services = rest.getForEntity("/services", String.class);
        assertThat(services.getBody()).contains("payments-api").contains("orders-api");
    }

    @Test
    void parsesServiceTargetConfig() {
        ServiceTarget target = ServiceTarget.parse("payments-api=http://localhost:8081,6,1.8.3");
        assertThat(target.name()).isEqualTo("payments-api");
        assertThat(target.url()).isEqualTo("http://localhost:8081");
        assertThat(target.replicas()).isEqualTo(6);
        assertThat(target.version()).isEqualTo("1.8.3");
    }

    @Test
    void alertWebhookOpensIncident() {
        String payload = "{\"status\":\"firing\",\"alerts\":[{"
                + "\"labels\":{\"alertname\":\"HighErrorRate\",\"service\":\"payments-api\",\"severity\":\"critical\"},"
                + "\"annotations\":{\"summary\":\"payments-api error rate above 20%\",\"description\":\"rollback now\"}}]}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/incidents",
                new HttpEntity<>(payload, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> active = rest.getForEntity("/incidents/active", String.class);
        assertThat(active.getBody()).contains("payments-api").contains("SEV_1");
    }
}
