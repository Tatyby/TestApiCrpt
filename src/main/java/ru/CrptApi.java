package ru;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class CrptApi {
    private final Limit limit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be positive");
        }
        this.limit = new Limit(timeUnit.toMillis(1), requestLimit);
    }

    public void createDocument(Document document, String signature) {
        if (!limit.tryAcquire()) {
            System.out.println("Request limit exceeded. Please wait before making another request");
            return;
        }
        Converter converterJson = new Converter();
        String documentJson = converterJson.convertToJson(document);
        try {
            String response = sendPostRequest(signature, documentJson);
            System.out.println("Received response: " + response);
        } catch (Exception e) {
            System.err.println("Failed to send request:" + e.getMessage());
        } finally {
            limit.release();
        }
    }

    private String sendPostRequest(String url, String jsonPayload) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json");
        StringEntity entity = new StringEntity(jsonPayload);
        httpPost.setEntity(entity);
        CloseableHttpResponse response = httpClient.execute(httpPost);
        HttpEntity responseEntity = response.getEntity();
        BufferedReader reader = new BufferedReader(new InputStreamReader(responseEntity.getContent()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        response.close();
        httpClient.close();
        return result.toString();
    }


    public class Limit {
        private final long interval;
        private final int requestLimit;
        private final AtomicLong lastRequestTime;
        private final AtomicLong requestCount;

        public Limit(long interval, int requestLimit) {
            this.interval = interval;
            this.requestLimit = requestLimit;
            this.lastRequestTime = new AtomicLong(0);
            this.requestCount = new AtomicLong(0);
        }

        public boolean tryAcquire() {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - lastRequestTime.get();

            if (elapsedTime >= interval) {
                lastRequestTime.set(currentTime);
                requestCount.set(1);
                return true;
            }

            if (requestCount.get() < requestLimit) {
                requestCount.incrementAndGet();
                return true;
            }

            return false;
        }

        public void release() {
            requestCount.decrementAndGet();
        }
    }

    public static class Converter {
        private final ObjectMapper objectMapper = new ObjectMapper();

        public String convertToJson(Object o) {
            try {
                return objectMapper.writeValueAsString(o);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Description {
        private String participantInn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }
}