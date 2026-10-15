package com.example.fridiet.service;

import com.example.fridiet.client.AiRecipeClient;
import com.example.fridiet.dto.AiRecipeClientDto;
import com.example.fridiet.dto.AiRecipeGenerateRequest;
import com.example.fridiet.dto.RecipeResponse;
import com.example.fridiet.dto.RecipeFeedbackRequest;
import com.example.fridiet.dto.RecipeCreateRequest;
import com.example.fridiet.entity.Fridge;
import com.example.fridiet.entity.InventoryBatch;
import com.example.fridiet.entity.Recipe;
import com.example.fridiet.entity.RecipeFeedbackEventType;
import com.example.fridiet.entity.RecipeRecommendationFeedback;
import com.example.fridiet.entity.User;
import com.example.fridiet.repository.FridgeRepository;
import com.example.fridiet.repository.FridgeMemberRepository;
import com.example.fridiet.repository.IngredientMasterRepository;
import com.example.fridiet.repository.InventoryBatchRepository;
import com.example.fridiet.repository.RecipeIngredientRepository;
import com.example.fridiet.repository.RecipeRepository;
import com.example.fridiet.repository.RecipeRecommendationFeedbackRepository;
import com.example.fridiet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeRecommendationFeedbackRepository recipeFeedbackRepository;
    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IngredientMasterRepository ingredientMasterRepository;
    @Mock
    private InventoryBatchRepository inventoryBatchRepository;
    @Mock
    private FridgeRepository fridgeRepository;
    @Mock
    private FridgeMemberRepository fridgeMemberRepository;
    @Mock
    private AiRecipeClient aiRecipeClient;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RecipeService recipeService;

    @Test
    void mealFeedbackUsesMealDateAndStableMealIdentity() {
        var user = User.builder().id(1L).build();
        var meal = com.example.fridiet.entity.Calendar.builder().owner(user)
                .fridge(Fridge.builder().id(2L).owner(user).build()).title("당근볶음")
                .eatenAt(LocalDateTime.of(2026, 8, 20, 19, 0)).build();
        org.springframework.test.util.ReflectionTestUtils.setField(meal, "id", 31L);
        recipeService.recordMealFeedback(meal, "mfds:1");
        var capture = ArgumentCaptor.forClass(RecipeRecommendationFeedback.class);
        verify(recipeFeedbackRepository).save(capture.capture());
        assertEquals(31L, capture.getValue().getMealEventId());
        assertEquals(meal.getEatenAt(), capture.getValue().getCreatedAt());
        assertEquals("mfds:1", capture.getValue().getRecipeKey());
        assertEquals(RecipeFeedbackEventType.COOKED, capture.getValue().getEventType());
    }

    @Test
    void mealCorrectionUpdatesOnlyItsLinkedFeedback() {
        var meal = com.example.fridiet.entity.Calendar.builder().title("수정한 식사")
                .eatenAt(LocalDateTime.of(2026, 8, 21, 8, 0)).build();
        org.springframework.test.util.ReflectionTestUtils.setField(meal, "id", 31L);
        var feedback = RecipeRecommendationFeedback.builder().recipeKey("mfds:1").recipeName("원래 식사")
                .eventType(RecipeFeedbackEventType.COOKED).createdAt(LocalDateTime.of(2026, 8, 20, 19, 0)).build();
        feedback.linkMeal(31L);
        when(recipeFeedbackRepository.findByMealEventId(31L)).thenReturn(Optional.of(feedback));
        recipeService.synchronizeMealFeedback(meal, false);
        assertEquals(meal.getEatenAt(), feedback.getCreatedAt());
        assertEquals("수정한 식사", feedback.getRecipeName());
        assertEquals("mfds:1", feedback.getRecipeKey());
        recipeService.synchronizeMealFeedback(meal, true);
        verify(recipeFeedbackRepository).delete(feedback);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void previewAiRecipeUsesStoredProfileWithoutSaving(boolean shopping) throws Exception {
        User user = User.builder()
                .id(1L)
                .email("profile-test@example.com")
                .name("프로필 테스트")
                .healthCondition("당뇨")
                .healthGoal("혈당 관리")
                .isDeleted(false)
                .build();
        Fridge fridge = Fridge.builder()
                .id(1L)
                .name("테스트 냉장고")
                .owner(user)
                .build();
        InventoryBatch inventory = InventoryBatch.builder()
                .id(1L)
                .fridge(fridge)
                .ingredientName("두부")
                .unitType(com.example.fridiet.entity.UnitType.QUANTITY)
                .consumeByDate(LocalDate.now().plusDays(2))
                .remainingQuantity(300.0)
                .unit("g")
                .status("ACTIVE")
                .createdBy(1L)
                .build();
        AiRecipeGenerateRequest request = org.mockito.Mockito.mock(AiRecipeGenerateRequest.class);

        when(request.getFridgeId()).thenReturn(1L);
        when(request.getTargetIngredients()).thenReturn(List.of());
        when(request.getAdditionalIngredients()).thenReturn(List.of());
        when(request.getExcludeIngredients()).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fridgeRepository.findById(1L)).thenReturn(Optional.of(fridge));
        when(inventoryBatchRepository.findAllActiveByFridgeId(1L)).thenReturn(List.of(inventory));
        // 최근 50개 노출 목록 밖에 있는 저장 이력도 AI에 전달되어야 한다.
        when(recipeFeedbackRepository.findByUser_IdAndEventTypeInAndCreatedAtAfterOrderByCreatedAtDesc(eq(1L), any(), any()))
                .thenReturn(List.of(RecipeRecommendationFeedback.builder().user(user)
                        .recipeName("이전 이름").recipeKey("LOCAL:test-1")
                        .eventType(RecipeFeedbackEventType.SAVED).createdAt(LocalDateTime.now().minusDays(10)).build()));
        when(aiRecipeClient.generateRecipe(any())).thenReturn(
                AiRecipeClientDto.Response.builder()
                        .recipeKey("LOCAL:test-1")
                        .title("두부구이")
                        .description("두부를 활용한 레시피")
                        .cookingTime(15)
                        .category("반찬")
                        .score(88.5)
                        .scoreBreakdown(Map.of("expiry_urgency", 40.0))
                        .reasons(List.of("두부의 소비기한이 2일 남았습니다."))
                        .availableIngredients(List.of("두부"))
                        .missingIngredients(List.of())
                        .ingredients(List.of())
                        .steps(List.of())
                        .build()
        );
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        RecipeResponse result = shopping ? recipeService.shoppingRecommendation(1L, request)
                : recipeService.previewAiRecipe(1L, request);

        ArgumentCaptor<AiRecipeClientDto.Request> captor =
                ArgumentCaptor.forClass(AiRecipeClientDto.Request.class);
        verify(aiRecipeClient).generateRecipe(captor.capture());
        verify(request, never()).getHealthCondition();
        verify(request, never()).getHealthGoal();
        verify(recipeRepository, never()).save(any(Recipe.class));
        ArgumentCaptor<RecipeRecommendationFeedback> feedbackCaptor =
                ArgumentCaptor.forClass(RecipeRecommendationFeedback.class);
        if (shopping) {
            verify(recipeFeedbackRepository, never()).save(any());
        } else {
            verify(recipeFeedbackRepository).save(feedbackCaptor.capture());
            assertEquals(RecipeFeedbackEventType.SHOWN, feedbackCaptor.getValue().getEventType());
            assertEquals("두부구이", feedbackCaptor.getValue().getRecipeName());
            assertEquals("LOCAL:test-1", feedbackCaptor.getValue().getRecipeKey());
        }
        assertEquals(shopping, captor.getValue().isShoppingMode());
        assertEquals("LOCAL:test-1", result.getRecipeKey());
        assertEquals("LOCAL:test-1", captor.getValue().getRecentFeedback().get(0).getRecipeKey());
        assertEquals("당뇨", captor.getValue().getHealthCondition());
        assertEquals("혈당 관리", captor.getValue().getHealthGoal());
        assertEquals(88.5, result.getRecommendationScore());
        assertEquals(List.of("두부의 소비기한이 2일 남았습니다."), result.getRecommendationReasons());
    }

    @Test
    void cookedRetryIsIgnored() {
        User user = User.builder().id(1L).build();
        RecipeFeedbackRequest request = mock(RecipeFeedbackRequest.class);
        when(userRepository.findForFeedbackUpdate(1L)).thenReturn(Optional.of(user));
        when(request.getEventType()).thenReturn(RecipeFeedbackEventType.COOKED);
        when(request.getRecipeKey()).thenReturn("LOCAL:test-1");
        when(request.getRecipeName()).thenReturn("두부구이");
        when(request.getFridgeId()).thenReturn(null);
        when(recipeFeedbackRepository.existsByUser_IdAndRecipeKey(1L, "LOCAL:test-1")).thenReturn(true);
        java.util.Set<String> eventKeys = new java.util.HashSet<>();
        when(recipeFeedbackRepository.existsByEventKey(any())).thenAnswer(inv -> eventKeys.contains(inv.getArgument(0)));
        when(recipeFeedbackRepository.save(any())).thenAnswer(inv -> {
            RecipeRecommendationFeedback event = inv.getArgument(0);
            assertNotNull(event.getEventKey());
            eventKeys.add(event.getEventKey());
            return event;
        });
        recipeService.recordRecommendationFeedback(1L, request);
        recipeService.recordRecommendationFeedback(1L, request);
        verify(recipeFeedbackRepository).save(any());
    }

    @Test
    void savingSameRecipeReturnsExistingRecord() {
        User user = User.builder().id(1L).build();
        RecipeCreateRequest request = mock(RecipeCreateRequest.class);
        Recipe saved = Recipe.builder().owner(user).recipeName("두부구이").recipeKey("LOCAL:test-1").build();
        when(request.getFridgeId()).thenReturn(null);
        org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 42L);
        when(userRepository.findForFeedbackUpdate(1L)).thenReturn(Optional.of(user));
        when(request.getRecipeKey()).thenReturn("LOCAL:test-1");
        when(recipeFeedbackRepository.existsByUser_IdAndRecipeKey(1L, "LOCAL:test-1")).thenReturn(true);
        when(recipeRepository.findFirstByOwner_IdAndRecipeKeyOrderByIdAsc(1L, "LOCAL:test-1")).thenReturn(Optional.of(saved));
        assertEquals(42L, recipeService.saveAiRecipe(1L, request).getRecipeId());
        verify(recipeRepository, never()).save(any());
        verify(recipeFeedbackRepository, never()).save(any());
    }

    @Test
    void feedbackCannotFabricateSavedEvent() {
        when(userRepository.findForFeedbackUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        RecipeFeedbackRequest request = mock(RecipeFeedbackRequest.class);
        when(request.getEventType()).thenReturn(RecipeFeedbackEventType.SAVED);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.recordRecommendationFeedback(1L, request));
        verify(recipeFeedbackRepository, never()).save(any());
    }

    @Test
    void unknownRecipeKeyIsRejected() {
        when(userRepository.findForFeedbackUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        RecipeFeedbackRequest request = mock(RecipeFeedbackRequest.class);
        when(request.getEventType()).thenReturn(RecipeFeedbackEventType.COOKED);
        when(request.getRecipeKey()).thenReturn("UNKNOWN:1");
        when(request.getFridgeId()).thenReturn(null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> recipeService.recordRecommendationFeedback(1L, request));
        verify(recipeFeedbackRepository, never()).save(any());
    }
}
