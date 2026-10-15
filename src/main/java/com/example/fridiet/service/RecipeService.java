package com.example.fridiet.service;

import com.example.fridiet.client.AiRecipeClient;
import com.example.fridiet.dto.AiRecipeClientDto;
import com.example.fridiet.dto.AiRecipeGenerateRequest;
import com.example.fridiet.dto.RecipeCreateRequest;
import com.example.fridiet.dto.RecipeFeedbackRequest;
import com.example.fridiet.dto.RecipeResponse;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeRecommendationFeedbackRepository recipeFeedbackRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final UserRepository userRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final IngredientMasterRepository ingredientMasterRepository;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final FridgeRepository fridgeRepository;
    private final AiRecipeClient aiRecipeClient;
    private final ObjectMapper objectMapper;

     /**
     * 수동 레시피 생성 및 저장
     */
    @Transactional
    public RecipeResponse createManualRecipe(Long userId, RecipeCreateRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));

        // Recipe 엔티티 생성
        Recipe recipe = Recipe.builder()
                .owner(owner)
                .recipeName(request.getRecipeName())
                .cookTime(request.getCookTime())
                .isGenerated(false) // 사용자가 작성한 레시피이므로 false
                .description(request.getDescription())
                .memo(request.getMemo())
                .stepsJson(request.getStepsJson())
                .build();

        Recipe savedRecipe = recipeRepository.save(recipe);

        // RecipeIngredient 목록 저장
        List<RecipeIngredient> savedIngredients = new ArrayList<>();

        for (RecipeCreateRequest.RecipeIngredientDto dto : request.getIngredients()) {

            String trimmedName = dto.getNameText().trim();
            IngredientMaster ingredientMaster = ingredientMasterRepository.findByCanonicalName(trimmedName).orElse(null);

            RecipeIngredient ingredient = RecipeIngredient.builder()
                    .recipe(savedRecipe)
                    .ingredientMaster(ingredientMaster)
                    .nameText(dto.getNameText())
                    .quantity(dto.getQuantity())
                    .unit(dto.getUnit())
                    .optional(dto.getOptional() != null ? dto.getOptional() : false)
                    .build();

            savedIngredients.add(recipeIngredientRepository.save(ingredient));
        }

        return RecipeResponse.from(savedRecipe, savedIngredients);
    }

    /**
     * AI 기반 레시피 생성 및 저장
     */
    @Transactional
    public RecipeResponse generateAiRecipe(Long userId, AiRecipeGenerateRequest request) {
        User user = userRepository.findForFeedbackUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));
        AiRecipeClientDto.Response aiResponse = requestAiRecommendation(user, request);
        String stepJson = serializeSteps(aiResponse);
        RecipeResponse existing = existingSaved(userId, aiResponse.getRecipeKey());
        if (existing != null) return existing;

        // Recipe 엔티티 생성 및 저장
        Recipe recipe = Recipe.builder()
                .owner(user)
                .recipeName(aiResponse.getTitle())
                .recipeKey(aiResponse.getRecipeKey())
                .cookTime(aiResponse.getCookingTime())
                .isGenerated(true) // AI가 생성
                .description(aiResponse.getDescription())
                .stepsJson(stepJson)
                .build();

        Recipe savedRecipe = recipeRepository.save(recipe);

        // recipe_ingredients 엔티티 변환 및 DB 저장
        List<RecipeIngredient> savedIngredients = new ArrayList<>();

        if(aiResponse.getIngredients() != null) {
            for(AiRecipeClientDto.Response.IngredientDto ingDto : aiResponse.getIngredients()) {
                String trimmedName = ingDto.getNameText().trim();

                // 마스터 DB에 있으면 FK 매핑, 없으면 null
                IngredientMaster master = ingredientMasterRepository.findByCanonicalName(trimmedName)
                        .orElse(null);

                RecipeIngredient recipeIngredient = RecipeIngredient.builder()
                        .recipe(savedRecipe)
                        .ingredientMaster(master)
                        .nameText(trimmedName)
                        .quantity(ingDto.getQuantity())
                        .unit(ingDto.getUnit())
                        .optional(ingDto.getOptional() != null ? ingDto.getOptional() : false)
                        .build();

                savedIngredients.add(recipeIngredientRepository.save(recipeIngredient));
            }
        }

        recordFeedback(
                user,
                findFridge(request.getFridgeId()),
                aiResponse.getTitle(),
                aiResponse.getRecipeKey(),
                RecipeFeedbackEventType.SAVED
        );
        return RecipeResponse.from(savedRecipe, savedIngredients, aiResponse);
    }

    public List<RecipeResponse> getSavedAiRecipes(Long userId, Long fridgeId) {

        if(!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new RuntimeException("해당 냉장고에 접근 권한이 없습니다.");
        }

        List<InventoryBatch> batches = inventoryBatchRepository.findAllActiveByFridgeId(fridgeId);

        Set<Long> inventoryIngredientIds = new HashSet<>();
        Set<String> inventoryTextNames = new HashSet<>();

        for(InventoryBatch batch : batches) {
            if(batch.getIngredientMaster() != null) {
                inventoryIngredientIds.add(batch.getIngredientMaster().getId());
            } else if(batch.getIngredientName() != null && !batch.getIngredientName().isBlank()) {
                inventoryTextNames.add(batch.getIngredientName().trim());
            }
        }

        List<Recipe> recipes = recipeRepository.findAllByOwner_IdAndIsGeneratedTrueOrderByCreatedAtDesc(userId);

        return recipes.stream().map(recipe -> {
            List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findAllByRecipeId(recipe.getId());

            List<String> availableList = new ArrayList<>();
            List<String> missingList = new ArrayList<>();

            for(RecipeIngredient recipeIngredient : recipeIngredients) {
                boolean isMatched = false;
                String displayName = "";

                if(recipeIngredient.getIngredientMaster() != null) {
                    Long ingredientMasterId = recipeIngredient.getIngredientMaster().getId();
                    displayName = recipeIngredient.getIngredientMaster().getCanonicalName();

                    if(inventoryIngredientIds.contains(ingredientMasterId)) {
                        isMatched = true;
                    } else {
                        List<String> aliases = ingredientAliasRepository.findAllByIngredientMaster_Id(ingredientMasterId)
                                .stream().map(IngredientAlias::getAliasText).toList();
                        isMatched = aliases.stream().anyMatch(inventoryTextNames::contains);
                    }
                } else if(recipeIngredient.getNameText() != null && !recipeIngredient.getNameText().isBlank()) {
                    String nameText = recipeIngredient.getNameText().trim();
                    displayName = nameText;

                    if(inventoryTextNames.contains(nameText)) {
                        isMatched = true;
                    } else {
                        Optional<IngredientAlias> aliasOpt = ingredientAliasRepository.findByAliasName(nameText);
                        if(aliasOpt.isPresent() && aliasOpt.get().getIngredientMaster() != null) {
                            Long matchedMasterId = aliasOpt.get().getIngredientMaster().getId();
                            isMatched = inventoryIngredientIds.contains(matchedMasterId);
                        }
                    }
                }
                if(isMatched) {
                    if(!availableList.contains(displayName)) {
                        availableList.add(displayName);
                    }
                } else {
                    if(!missingList.contains(displayName)) {
                        missingList.add(displayName);
                    }
                }
            }
            return RecipeResponse.from(recipe, recipeIngredients, availableList, missingList);
        }).toList();
    }

    public RecipeResponse getRecipeDetail(Long userId, Long recipeId, Long fridgeId) {
        if(!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new RuntimeException("해당 냉장고에 접근 권한이 없습니다.");
        }

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 레시피입니다."));

        if(!recipe.getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("본인의 레시피만 조회할 수 있습니다.");
        }

        List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findAllByRecipeId(recipeId);

        List<String> availableList = new ArrayList<>();
        List<String> missingList = new ArrayList<>();

        List<InventoryBatch> batches = inventoryBatchRepository.findAllActiveByFridgeId(fridgeId);

        Set<Long> inventoryIngredientIds = new HashSet<>();
        Set<String> inventoryTextNames = new HashSet<>();

        for(InventoryBatch batch : batches) {
            if(batch.getIngredientMaster() != null) {
                inventoryIngredientIds.add(batch.getIngredientMaster().getId());
            } else if(batch.getIngredientName() != null && !batch.getIngredientName().isBlank()) {
                inventoryTextNames.add(batch.getIngredientName().trim());
            }
        }

        for(RecipeIngredient recipeIngredient : recipeIngredients) {
            boolean isMatched = false;
            String displayName = "";

            if(recipeIngredient.getIngredientMaster() != null) {
                Long ingredientMasterId = recipeIngredient.getIngredientMaster().getId();
                displayName = recipeIngredient.getIngredientMaster().getCanonicalName();

                if(inventoryIngredientIds.contains(ingredientMasterId)) {
                    isMatched = true;
                } else {
                    List<String> aliases = ingredientAliasRepository.findAllByIngredientMaster_Id(ingredientMasterId)
                            .stream().map(IngredientAlias::getAliasText).toList();
                    isMatched = aliases.stream().anyMatch(inventoryTextNames::contains);
                }
            } else if(recipeIngredient.getNameText() != null && !recipeIngredient.getNameText().isBlank()) {
                String nameText = recipeIngredient.getNameText().trim();
                displayName = nameText;

                if(inventoryTextNames.contains(nameText)) {
                    isMatched = true;
                } else {
                    Optional<IngredientAlias> aliasOpt = ingredientAliasRepository.findByAliasName(nameText);
                    if(aliasOpt.isPresent() && aliasOpt.get().getIngredientMaster() != null) {
                        Long matchedMasterId = aliasOpt.get().getIngredientMaster().getId();
                        isMatched = inventoryIngredientIds.contains(matchedMasterId);
                    }
                }
            }
            if(isMatched) {
                if(!availableList.contains(displayName)) {
                    availableList.add(displayName);
                }
            } else {
                if(!missingList.contains(displayName)) {
                    missingList.add(displayName);
                }
            }
        }

        return RecipeResponse.from(recipe, recipeIngredients, availableList, missingList);
    }

    @Transactional
    public RecipeResponse previewAiRecipe(Long userId, AiRecipeGenerateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));
        AiRecipeClientDto.Response aiResponse = requestAiRecommendation(user, request);
        recordFeedback(
                user,
                findFridge(request.getFridgeId()),
                aiResponse.getTitle(),
                aiResponse.getRecipeKey(),
                RecipeFeedbackEventType.SHOWN
        );
        return RecipeResponse.preview(userId, aiResponse, serializeSteps(aiResponse));
    }

    @Transactional
    public RecipeResponse saveAiRecipe(Long userId, RecipeCreateRequest request) {
        User user = userRepository.findForFeedbackUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));

        if (request.getFridgeId() != null) requireFridgeAccess(user, request.getFridgeId());
        requireKnownRecipeKey(userId, request.getRecipeKey());
        RecipeResponse existing = existingSaved(userId, request.getRecipeKey());
        if (existing != null) return existing;

        Recipe recipe = Recipe.builder()
                .owner(user)
                .recipeName(request.getRecipeName())
                .recipeKey(request.getRecipeKey())
                .cookTime(request.getCookTime())
                .isGenerated(true)
                .description(request.getDescription())
                .memo(request.getMemo())
                .stepsJson(request.getStepsJson())
                .build();
        Recipe savedRecipe = recipeRepository.save(recipe);
        List<RecipeIngredient> savedIngredients = new ArrayList<>();

        for (RecipeCreateRequest.RecipeIngredientDto dto : request.getIngredients()) {
            String trimmedName = dto.getNameText().trim();
            IngredientMaster master = ingredientMasterRepository.findByCanonicalName(trimmedName)
                    .orElse(null);
            RecipeIngredient ingredient = RecipeIngredient.builder()
                    .recipe(savedRecipe)
                    .ingredientMaster(master)
                    .nameText(trimmedName)
                    .quantity(dto.getQuantity())
                    .unit(dto.getUnit())
                    .optional(dto.getOptional() != null ? dto.getOptional() : false)
                    .build();
            savedIngredients.add(recipeIngredientRepository.save(ingredient));
        }

        Fridge fridge = request.getFridgeId() != null ? findFridge(request.getFridgeId()) : null;
        recordFeedback(user, fridge, savedRecipe.getRecipeName(), request.getRecipeKey(), RecipeFeedbackEventType.SAVED);
        return RecipeResponse.from(savedRecipe, savedIngredients);
    }

    @Transactional
    public void recordRecommendationFeedback(Long userId, RecipeFeedbackRequest request) {
        User user = userRepository.findForFeedbackUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + userId));
        if (request.getEventType() != RecipeFeedbackEventType.COOKED
                && request.getEventType() != RecipeFeedbackEventType.DISMISSED) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "피드백 API는 COOKED와 DISMISSED만 받습니다. 저장 기록은 저장 성공 시 생성됩니다.");
        }
        if (request.getFridgeId() != null) requireFridgeAccess(user, request.getFridgeId());
        requireKnownRecipeKey(userId, request.getRecipeKey());
        Fridge fridge = request.getFridgeId() != null ? findFridge(request.getFridgeId()) : null;
        recordFeedback(user, fridge, request.getRecipeName(), request.getRecipeKey(), request.getEventType());
    }

    @Transactional
    public void validateCookingRecipe(Long userId, Long fridgeId, String recipeKey) {
        User user = userRepository.findForFeedbackUpdate(userId).orElseThrow();
        requireFridgeAccess(user, fridgeId);
        requireKnownRecipeKey(userId, recipeKey);
    }

    /** 식사별로 연결하고, 추천/결산에서는 기존처럼 같은 레시피·날짜를 중복 제거한다. */
    public void recordMealFeedback(com.example.fridiet.entity.Calendar meal, String recipeKey) {
        var feedback = RecipeRecommendationFeedback.builder().user(meal.getOwner()).fridge(meal.getFridge())
                .recipeName(meal.getTitle()).recipeKey(recipeKey).eventKey(digest("meal:" + meal.getId()))
                .eventType(RecipeFeedbackEventType.COOKED).createdAt(meal.getEatenAt()).build();
        feedback.linkMeal(meal.getId());
        recipeFeedbackRepository.save(feedback);
    }

    public void synchronizeMealFeedback(com.example.fridiet.entity.Calendar meal, boolean deleted) {
        recipeFeedbackRepository.findByMealEventId(meal.getId()).ifPresent(feedback -> {
            if (deleted) recipeFeedbackRepository.delete(feedback);
            else feedback.updateMeal(meal.getTitle(), meal.getEatenAt());
        });
    }

    private AiRecipeClientDto.Response requestAiRecommendation(
            User user,
            AiRecipeGenerateRequest request
    ) {
        return requestAiRecommendation(user, request, false);
    }

    @Transactional(readOnly = true)
    public RecipeResponse shoppingRecommendation(Long userId, AiRecipeGenerateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자가 없습니다."));
        var response = requestAiRecommendation(user, request, true);
        return RecipeResponse.preview(userId, response, serializeSteps(response));
    }

    private AiRecipeClientDto.Response requestAiRecommendation(User user, AiRecipeGenerateRequest request, boolean shoppingMode) {
        requireFridgeAccess(user, request.getFridgeId());

        List<InventoryBatch> activeBatches =
                inventoryBatchRepository.findAllActiveByFridgeId(request.getFridgeId());
        List<String> activeInventoryNames = activeBatches.stream()
                .map(InventoryBatch::getIngredientName)
                .distinct()
                .toList();
        List<AiRecipeClientDto.InventoryDto> inventorySnapshots = activeBatches.stream()
                .sorted(java.util.Comparator.comparing(InventoryBatch::getConsumeByDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).thenComparing(InventoryBatch::getId))
                .map(batch -> AiRecipeClientDto.InventoryDto.builder()
                        .ingredientName(batch.getIngredientName())
                        .consumeByDate(batch.getConsumeByDate())
                        .remainingQuantity(batch.getRemainingQuantity())
                        .countBasis(batch.getCountBasis())
                        .perCountQuantity(batch.getPerCountQuantity())
                        .perCountUnit(batch.getPerCountUnit())
                        .quantityNeedsReview(batch.isQuantityNeedsReview())
                        .unitType(batch.getUnitType().name())
                        .unit(batch.getUnit())
                        .build())
                .toList();
        List<RecipeRecommendationFeedback> recent = recipeFeedbackRepository
                .findTop50ByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(
                        user.getId(),
                        LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(30)
                );
        List<RecipeRecommendationFeedback> positive = recipeFeedbackRepository
                .findByUser_IdAndEventTypeInAndCreatedAtAfterOrderByCreatedAtDesc(
                        user.getId(), List.of(RecipeFeedbackEventType.SAVED, RecipeFeedbackEventType.COOKED),
                        LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(30));
        List<AiRecipeClientDto.FeedbackDto> recentFeedback = java.util.stream.Stream.concat(recent.stream(), positive.stream())
                .distinct()
                .sorted(java.util.Comparator.comparing(RecipeRecommendationFeedback::getCreatedAt).reversed())
                .map(feedback -> AiRecipeClientDto.FeedbackDto.builder()
                        .recipeKey(feedback.getRecipeKey())
                        .recipeName(feedback.getRecipeName())
                        .eventType(feedback.getEventType().name())
                        .occurredAt(feedback.getCreatedAt())
                        .build())
                .toList();

        AiRecipeClientDto.Request aiRequest = AiRecipeClientDto.Request.builder()
                .shoppingMode(shoppingMode)
                .healthCondition(user.getHealthCondition())
                .healthGoal(user.getHealthGoal())
                .activeInventories(activeInventoryNames)
                .inventories(inventorySnapshots)
                .targetIngredients(request.getTargetIngredients())
                .additionalIngredients(request.getAdditionalIngredients())
                .excludeIngredients(request.getExcludeIngredients())
                .recentFeedback(recentFeedback)
                .build();
        return aiRecipeClient.generateRecipe(aiRequest);
    }

    private Fridge findFridge(Long fridgeId) {
        return fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 냉장고입니다. ID: " + fridgeId
                ));
    }

    private void recordFeedback(
            User user,
            Fridge fridge,
            String recipeName,
            String recipeKey,
            RecipeFeedbackEventType eventType
    ) {
        LocalDateTime occurredAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        String eventKey = null;
        if (eventType != RecipeFeedbackEventType.SHOWN) {
            String identity = recipeKey != null ? recipeKey : "legacy:" + recipeName.trim();
            String bucket = eventType == RecipeFeedbackEventType.SAVED ? "saved" : occurredAt.toLocalDate().toString();
            eventKey = digest(user.getId() + "|" + identity + "|" + eventType + "|" + bucket);
            if (recipeFeedbackRepository.existsByEventKey(eventKey)) return;
        }
        recipeFeedbackRepository.save(RecipeRecommendationFeedback.builder()
                .user(user)
                .fridge(fridge)
                .recipeName(recipeName.trim())
                .recipeKey(recipeKey)
                .eventKey(eventKey)
                .eventType(eventType)
                .createdAt(occurredAt)
                .build());
    }

    private static String digest(String input) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private RecipeResponse existingSaved(Long userId, String recipeKey) {
        if (recipeKey == null) return null;
        return recipeRepository.findFirstByOwner_IdAndRecipeKeyOrderByIdAsc(userId, recipeKey)
                .map(recipe -> RecipeResponse.from(recipe, recipeIngredientRepository.findAllByRecipeId(recipe.getId())))
                .orElse(null);
    }

    private void requireKnownRecipeKey(Long userId, String recipeKey) {
        if (recipeKey != null && (recipeKey.isBlank() || !recipeFeedbackRepository.existsByUser_IdAndRecipeKey(userId, recipeKey))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "이 사용자에게 추천된 레시피 고유 ID가 아닙니다.");
        }
    }

    private void requireFridgeAccess(User user, Long fridgeId) {
        Fridge fridge = findFridge(fridgeId);
        if (!fridge.getOwner().getId().equals(user.getId()) &&
                !fridgeMemberRepository.existsByFridgeIdAndUserIdAndStatus(fridgeId, user.getId(), "ACCEPTED")) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "참여 중인 냉장고만 사용할 수 있습니다.");
        }
    }

    private String serializeSteps(AiRecipeClientDto.Response aiResponse) {
        try {
            return objectMapper.writeValueAsString(aiResponse.getSteps());
        } catch (Exception ignored) {
            return "[]";
        }
    }
}
