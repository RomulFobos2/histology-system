package ru.mai.histology.service.general;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
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

    @Value("${autoencoder.service.url:http://127.0.0.1:8000}")
    private String autoencoderServiceUrl;

    public AutoencoderClientService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.trainingRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isServiceAvailable() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(autoencoderServiceUrl + "/health", Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.error("Python-сервис автоэнкодера недоступен: {}", e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getModels() {
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(autoencoderServiceUrl + "/models", List.class);
            return response.getBody() == null ? List.of() : response.getBody();
        } catch (RestClientException e) {
            log.error("Не удалось получить список моделей автоэнкодера: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public Map<String, Object> getMetrics() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(autoencoderServiceUrl + "/metrics", Map.class);
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException e) {
            log.error("Не удалось получить метрики автоэнкодера: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    public Map<String, Object> getTrainingStatus() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(autoencoderServiceUrl + "/training/status", Map.class);
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException e) {
            log.error("Не удалось получить статус обучения автоэнкодера: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    public List<Map<String, Object>> getTrainingHistory() {
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(autoencoderServiceUrl + "/training/history", List.class);
            return response.getBody() == null ? List.of() : response.getBody();
        } catch (RestClientException e) {
            log.error("Не удалось получить историю обучения автоэнкодера: {}", e.getMessage(), e);
            return List.of();
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
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
