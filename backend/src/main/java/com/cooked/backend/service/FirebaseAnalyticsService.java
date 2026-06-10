package com.cooked.backend.service;

import com.google.analytics.data.v1beta.*;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FirebaseAnalyticsService {

    @Value("${ga4.property.id}")
    private String propertyId;

    @Value("${ga4.credentials.base64:}")
    private String credentialsBase64;

    private BetaAnalyticsDataClient createClient() throws Exception {
        InputStream is = null;
        if (credentialsBase64 != null && !credentialsBase64.trim().isEmpty()) {
            // 1. Prod: Decode Base64 string from environment variable
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(credentialsBase64.trim());
            is = new java.io.ByteArrayInputStream(decodedBytes);
        } else {
            // 2. Local Dev: Use the ignored JSON file
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("cookedapp-493503-4afa7c77d6ee.json");
            is = resource.getInputStream();
        }

        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(is)
                    .createScoped("https://www.googleapis.com/auth/analytics.readonly");
            BetaAnalyticsDataSettings settings = BetaAnalyticsDataSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            return BetaAnalyticsDataClient.create(settings);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    public Map<String, Object> getTrafficData() {
        Map<String, Object> response = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Integer> activeUsers = new ArrayList<>();

        try (BetaAnalyticsDataClient client = createClient()) {
            RunReportRequest request = RunReportRequest.newBuilder()
                    .setProperty("properties/" + propertyId)
                    .addDimensions(Dimension.newBuilder().setName("date"))
                    .addMetrics(Metric.newBuilder().setName("activeUsers"))
                    .addDateRanges(DateRange.newBuilder().setStartDate("7daysAgo").setEndDate("today"))
                    .addOrderBys(OrderBy.newBuilder().setDimension(OrderBy.DimensionOrderBy.newBuilder().setDimensionName("date").setOrderType(OrderBy.DimensionOrderBy.OrderType.ALPHANUMERIC)))
                    .build();

            RunReportResponse reportResponse = client.runReport(request);

            for (Row row : reportResponse.getRowsList()) {
                String rawDate = row.getDimensionValues(0).getValue(); // Format: YYYYMMDD
                LocalDate date = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
                labels.add(date.format(DateTimeFormatter.ofPattern("MMM dd")));
                activeUsers.add(Integer.parseInt(row.getMetricValues(0).getValue()));
            }

            response.put("labels", labels);
            response.put("activeUsers", activeUsers);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", "Failed to fetch traffic data: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> getEventsData() {
        Map<String, Object> response = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Integer> data = new ArrayList<>();

        try (BetaAnalyticsDataClient client = createClient()) {
            RunReportRequest request = RunReportRequest.newBuilder()
                    .setProperty("properties/" + propertyId)
                    .addDimensions(Dimension.newBuilder().setName("eventName"))
                    .addMetrics(Metric.newBuilder().setName("eventCount"))
                    .addDateRanges(DateRange.newBuilder().setStartDate("30daysAgo").setEndDate("today"))
                    .addOrderBys(OrderBy.newBuilder().setMetric(OrderBy.MetricOrderBy.newBuilder().setMetricName("eventCount")).setDesc(true))
                    .setLimit(5) // Get top 5 events
                    .build();

            RunReportResponse reportResponse = client.runReport(request);

            for (Row row : reportResponse.getRowsList()) {
                labels.add(row.getDimensionValues(0).getValue());
                data.add(Integer.parseInt(row.getMetricValues(0).getValue()));
            }

            response.put("labels", labels);
            response.put("data", data);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", "Failed to fetch events data: " + e.getMessage());
        }

        return response;
    }
}
