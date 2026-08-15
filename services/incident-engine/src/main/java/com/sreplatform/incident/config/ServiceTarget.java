package com.sreplatform.incident.config;

/**
 * A service the incident engine monitors. Populated from the
 * MONITORED_SERVICES environment variable: name=url,replicas,version
 */
public record ServiceTarget(String name, String url, int replicas, String version) {

    public static ServiceTarget parse(String raw) {
        String[] kv = raw.split("=", 2);
        if (kv.length != 2) {
            throw new IllegalArgumentException("Malformed service target: " + raw);
        }
        String name = kv[0].trim();
        String[] parts = kv[1].trim().split(",");
        String url = parts[0].trim();
        int replicas = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 6;
        String version = parts.length > 2 ? parts[2].trim() : "unknown";
        return new ServiceTarget(name, url, replicas, version);
    }
}
