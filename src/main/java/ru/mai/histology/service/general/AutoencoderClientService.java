package ru.mai.histology.service.general;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class AutoencoderClientService {

    private final RestTemplate restTemplate;
    private final RestTemplate trainingRestTemplate;
    private final RestTemplate enhanceRestTemplate;

    @Value("${autoencoder.service.url:http://127.0.0.1:8000}")
    private String autoencoderServiceUrl;

    public AutoencoderClientService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = buildHttp11RestTemplate(restTemplateBuilder, Duration.ofSeconds(3), Duration.ofSeconds(120));
        this.trainingRestTemplate = buildHttp11RestTemplate(restTemplateBuilder, Duration.ofSeconds(3), Duration.ofSeconds(10));
        // U-Net на CPU может обрабатывать изображение 30–120 секунд
        this.enhanceRestTemplate = buildHttp11RestTemplate(restTemplateBuilder, Duration.ofSeconds(3), Duration.ofSeconds(120));
        log.info("Autoencoder HTTP transport forced to {} to avoid h2c upgrade issues with Uvicorn",
                SimpleClientHttpRequestFactory.class.getSimpleName());
    }

    private RestTemplate buildHttp11RestTemplate(RestTemplateBuilder restTemplateBuilder,
                                                 Duration connectTimeout,
                                                 Duration readTimeout) {
        // JDK HttpClient tries h2c upgrade on plain HTTP, which Uvicorn rejects for multipart POST /enhance.
        return restTemplateBuilder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }

    public boolean isServiceAvailable() {
        long t0 = System.currentTimeMillis();
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(autoencoderServiceUrl + "/health", Map.class);
            log.debug("GET /health - {} мс", System.currentTimeMillis() - t0);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("GET /health ошибка - {} мс: {}", System.currentTimeMillis() - t0, e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getModels() {
        long t0 = System.currentTimeMillis();
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(autoencoderServiceUrl + "/models", List.class);
            log.debug("GET /models - {} мс", System.currentTimeMillis() - t0);
            return response.getBody() == null ? List.of() : response.getBody();
        } catch (RestClientException e) {
            log.warn("GET /models ошибка - {} мс: {}", System.currentTimeMillis() - t0, e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getMetrics() {
        long t0 = System.currentTimeMillis();
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(autoencoderServiceUrl + "/metrics", Map.class);
            log.debug("GET /metrics - {} мс", System.currentTimeMillis() - t0);
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException e) {
            log.warn("GET /metrics ошибка - {} мс: {}", System.currentTimeMillis() - t0, e.getMessage());
            return Map.of();
        }
    }

    public Map<String, Object> getTrainingStatus() {
        long t0 = System.currentTimeMillis();
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(autoencoderServiceUrl + "/training/status", Map.class);
            log.debug("GET /training/status - {} мс", System.currentTimeMillis() - t0);
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException e) {
            log.warn("GET /training/status ошибка - {} мс: {}", System.currentTimeMillis() - t0, e.getMessage());
            return Map.of();
        }
    }

    public Map<String, Object> resetTrainingStatus() {
        long t0 = System.currentTimeMillis();
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    autoencoderServiceUrl + "/training/reset-status", null, Map.class);
            log.debug("POST /training/reset-status - {} мс", System.currentTimeMillis() - t0);
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException e) {
            log.warn("POST /training/reset-status ошибка - {} мс: {}", System.currentTimeMillis() - t0, e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    public List<Map<String, Object>> getTrainingHistory() {
        long t0 = System.currentTimeMillis();
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(autoencoderServiceUrl + "/training/history", List.class);
            log.debug("GET /training/history - {} мс", System.currentTimeMillis() - t0);
            return response.getBody() == null ? List.of() : response.getBody();
        } catch (RestClientException e) {
            log.warn("GET /training/history ошибка - {} мс: {}", System.currentTimeMillis() - t0, e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> clearTrainingHistory() {
        long t0 = System.currentTimeMillis();
        try {
            restTemplate.delete(autoencoderServiceUrl + "/training/history");
            log.debug("DELETE /training/history - {} мс", System.currentTimeMillis() - t0);
            return Map.of("status", "ok", "message", "История Python-сервиса очищена");
        } catch (RestClientException e) {
            log.warn("DELETE /training/history ошибка - {} мс: {}", System.currentTimeMillis() - t0, e.getMessage());
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    public Map<String, Object> trainModel(int epochs, int batchSize, double learningRate, int imageSize) {
        try {
            String url = autoencoderServiceUrl + "/train?epochs=" + epochs
                    + "&batch_size=" + batchSize
                    + "&learning_rate=" + learningRate
                    + "&image_size=" + imageSize;
            ResponseEntity<Map> response = trainingRestTemplate.postForEntity(url, null, Map.class);
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException e) {
            log.error("Не удалось запустить обучение автоэнкодера: {}", e.getMessage(), e);
            return Map.of(
                    "status", "error",
                    "message", "Python-сервис недоступен или вернул ошибку при обучении"
            );
        }
    }

    public Optional<EnhancedImageResponse> enhanceImage(String filename, String contentType, byte[] data, String mode) {
        try {
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"));
            ByteArrayResource resource = new ByteArrayResource(data) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", filePart);
            body.add("mode", mode != null ? mode : "auto");

            // Не задаём Content-Type вручную: FormHttpMessageConverter сам определит
            // multipart по наличию ByteArrayResource в body и добавит правильный
            // Content-Type с boundary. Явная установка MULTIPART_FORM_DATA без boundary
            // в некоторых версиях Spring приводила к 422 на стороне FastAPI/uvicorn.
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body);
            ResponseEntity<byte[]> response = enhanceRestTemplate.exchange(
                    autoencoderServiceUrl + "/enhance",
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Python-сервис вернул некорректный ответ при улучшении изображения");
                return Optional.empty();
            }

            HttpHeaders responseHeaders = response.getHeaders();
            String responseContentType = responseHeaders.getContentType() != null
                    ? responseHeaders.getContentType().toString()
                    : "image/jpeg";
            String modelName = responseHeaders.getFirst("X-Model-Name");

            return Optional.of(new EnhancedImageResponse(response.getBody(), responseContentType, modelName));
        } catch (RestClientException e) {
            log.error("Ошибка вызова Python-сервиса при улучшении изображения: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public record EnhancedImageResponse(byte[] imageBytes, String contentType, String modelName) {
    }
}
