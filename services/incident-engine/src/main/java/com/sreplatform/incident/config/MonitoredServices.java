package com.sreplatform.incident.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MonitoredServices {

    private final List<ServiceTarget> targets;

    public MonitoredServices(@Value("${app.monitored-services}") String raw) {
        this.targets = raw == null || raw.isBlank()
                ? List.of()
                : java.util.Arrays.stream(raw.split(";"))
                        .map(String::trim)
                        .map(ServiceTarget::parse)
                        .toList();
    }

    public List<ServiceTarget> targets() {
        return targets;
    }

    public ServiceTarget byName(String name) {
        return targets.stream()
                .filter(t -> t.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + name));
    }
}
