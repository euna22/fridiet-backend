package com.example.fridiet.client;

import com.example.fridiet.dto.AiRecipeClientDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiRecipeClient {

    private final RestTemplate restTemplate;
    private final String recipeApiUrl;
    private final String aiServiceToken;

    public AiRecipeClient(
            @Value("${ai.base-url:http://localhost:8000}") String aiBaseUrl,
            @Value("${ai.service-token}") String aiServiceToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
        this.recipeApiUrl = aiBaseUrl + "/api/recipes/ai";
        this.aiServiceToken = aiServiceToken;
    }

    /**
     * AI에게 재고 리스트를 전달하여 생성된 레시피를 수신한다.
     */
    public AiRecipeClientDto.Response generateRecipe(AiRecipeClientDto.Request request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-AI-Service-Token", aiServiceToken);
        HttpEntity<AiRecipeClientDto.Request> entity = new HttpEntity<>(request, headers);

        AiRecipeClientDto.Response response;
        try {
            response = restTemplate.postForObject(
                recipeApiUrl,
                entity,
                AiRecipeClientDto.Response.class
        );
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "현재 조건에 맞는 추천이 없습니다.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 추천 처리에 실패했습니다.");
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        if (response == null) {
            throw new IllegalStateException("AI 레시피 응답이 비어 있습니다.");
        }
        return response;
    }
}
