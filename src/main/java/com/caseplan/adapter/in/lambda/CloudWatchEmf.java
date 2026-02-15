package com.caseplan.adapter.in.lambda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates CloudWatch Embedded Metric Format (EMF) JSON and prints to stdout.
 * Lambda stdout is captured by CloudWatch Logs, which auto-extracts EMF as metrics.
 * No AWS SDK dependency required.
 */
public final class CloudWatchEmf {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NAMESPACE = "CasePlan/Lambda";

    private CloudWatchEmf() {
    }

    /**
     * Fluent builder for EMF metric records.
     */
    public static class MetricRecord {
        private final Map<String, String> dimensions = new LinkedHashMap<>();
        private final List<MetricEntry> metrics = new ArrayList<>();
        private final Map<String, Object> properties = new LinkedHashMap<>();

        /**
         * Add a dimension (key-value pair used for grouping metrics).
         */
        public MetricRecord dimension(String name, String value) {
            dimensions.put(name, value);
            return this;
        }

        /**
         * Add a metric with Count unit.
         */
        public MetricRecord count(String name, double value) {
            metrics.add(new MetricEntry(name, value, "Count"));
            return this;
        }

        /**
         * Add a metric with Milliseconds unit.
         */
        public MetricRecord millis(String name, double value) {
            metrics.add(new MetricEntry(name, value, "Milliseconds"));
            return this;
        }

        /**
         * Add a custom property (not a metric, but included in log event).
         */
        public MetricRecord property(String name, Object value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Build EMF JSON and print to stdout.
         */
        public void emit() {
            try {
                ObjectNode root = MAPPER.createObjectNode();

                // _aws metadata
                ObjectNode aws = root.putObject("_aws");
                aws.put("Timestamp", System.currentTimeMillis());

                ArrayNode cwMetrics = aws.putArray("CloudWatchMetrics");
                ObjectNode metricDirective = cwMetrics.addObject();
                metricDirective.put("Namespace", NAMESPACE);

                // Dimensions array (array of arrays)
                ArrayNode dimArray = metricDirective.putArray("Dimensions");
                ArrayNode dimNames = dimArray.addArray();
                for (String dimName : dimensions.keySet()) {
                    dimNames.add(dimName);
                }

                // Metrics definitions
                ArrayNode metricsArray = metricDirective.putArray("Metrics");
                for (MetricEntry entry : metrics) {
                    ObjectNode m = metricsArray.addObject();
                    m.put("Name", entry.name);
                    m.put("Unit", entry.unit);
                }

                // Dimension values as top-level fields
                for (Map.Entry<String, String> dim : dimensions.entrySet()) {
                    root.put(dim.getKey(), dim.getValue());
                }

                // Metric values as top-level fields
                for (MetricEntry entry : metrics) {
                    root.put(entry.name, entry.value);
                }

                // Additional properties
                for (Map.Entry<String, Object> prop : properties.entrySet()) {
                    Object v = prop.getValue();
                    if (v instanceof String) {
                        root.put(prop.getKey(), (String) v);
                    } else if (v instanceof Number) {
                        root.put(prop.getKey(), ((Number) v).doubleValue());
                    } else if (v instanceof Boolean) {
                        root.put(prop.getKey(), (Boolean) v);
                    } else if (v != null) {
                        root.put(prop.getKey(), v.toString());
                    }
                }

                System.out.println(MAPPER.writeValueAsString(root));
            } catch (Exception e) {
                // EMF emission must never break the handler
                System.err.println("EMF emit failed: " + e.getMessage());
            }
        }
    }

    /**
     * Start building a new EMF metric record.
     */
    public static MetricRecord record() {
        return new MetricRecord();
    }

    private static class MetricEntry {
        final String name;
        final double value;
        final String unit;

        MetricEntry(String name, double value, String unit) {
            this.name = name;
            this.value = value;
            this.unit = unit;
        }
    }
}
