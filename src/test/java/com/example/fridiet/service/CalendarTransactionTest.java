package com.example.fridiet.service;

import com.example.fridiet.dto.*;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 로컬 MySQL 및 외부 서비스에 연결하지 않는 실제 JPA/트랜잭션 회귀 테스트. */
@SpringJUnitConfig(CalendarTransactionTest.Config.class)
class CalendarTransactionTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = CalendarRepository.class)
    @Import({CalendarService.class, InventoryActivityService.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:calendar_merge;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.example.fridiet.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean RecipeService recipeService() { return mock(RecipeService.class); }
    }
    @Autowired CalendarService service;
    @Autowired UserRepository users;
    @Autowired FridgeRepository fridges;
    @Autowired FridgeMemberRepository members;
    @Autowired InventoryBatchRepository batches;
    @Autowired CalendarRepository calendars;
    @Autowired InventoryActivityRepository activities;
    @Autowired MealConsumptionRepository consumptions;
    @Autowired RecipeService recipes;
    @Autowired PlatformTransactionManager transactionManager;
    Long userId, fridgeId, batchId;
    LocalDateTime time;

    @BeforeEach void fixture() {
        reset(recipes);
        time = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusHours(1).withNano(0);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var user = users.save(User.builder().email(UUID.randomUUID() + "@example.test").name("격리 테스트").isDeleted(false).build());
            var fridge = fridges.save(Fridge.builder().name("격리 냉장고").owner(user).build());
            var batch = batches.save(InventoryBatch.builder().fridge(fridge).ingredientName("당근")
                    .consumeByDate(time.toLocalDate().plusDays(3)).locationCategory("REFRIGERATED")
                    .remainingQuantity(100.0).unitType(UnitType.QUANTITY).unit("g").status("ACTIVE").createdBy(user.getId()).build());
            userId = user.getId(); fridgeId = fridge.getId(); batchId = batch.getId();
        });
    }
    MealRecordRequest.MealItemDto item(double amount) {
        var item = new MealRecordRequest.MealItemDto();
        ReflectionTestUtils.setField(item, "batchId", batchId);
        ReflectionTestUtils.setField(item, "unitType", UnitType.QUANTITY);
        ReflectionTestUtils.setField(item, "unit", "g");
        ReflectionTestUtils.setField(item, "quantityUsed", amount);
        return item;
    }
    MealRecordRequest request() {
        var request = new MealRecordRequest();
        ReflectionTestUtils.setField(request, "requestId", UUID.randomUUID().toString());
        ReflectionTestUtils.setField(request, "fridgeId", fridgeId);
        ReflectionTestUtils.setField(request, "title", "당근볶음");
        ReflectionTestUtils.setField(request, "mealType", "LUNCH");
        ReflectionTestUtils.setField(request, "eatenAt", time);
        ReflectionTestUtils.setField(request, "usedIngredients", List.of(item(40)));
        return request;
    }

    @Test void actualJpaUpdateDeleteKeepsStockHistoryAndTombstoneConsistent() {
        var request = request();
        Long mealId = service.recordMealAndDeductStock(userId, request, "mfds:1").getMealEventId();
        assertEquals(60.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
        var update = new MealRecordUpdateRequest();
        ReflectionTestUtils.setField(update, "title", "당근볶음 수정");
        ReflectionTestUtils.setField(update, "mealType", "DINNER");
        ReflectionTestUtils.setField(update, "eatenAt", time.minusDays(1));
        ReflectionTestUtils.setField(update, "usedIngredients", List.of(item(80)));
        service.updateMeal(mealId, userId, update);
        assertEquals(20.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
        var active = activities.findByMealEventIdAndCancelledAtIsNull(mealId);
        assertEquals(1, active.size());
        assertEquals(80.0, active.getFirst().getQuantity());
        assertEquals(time.minusDays(1), active.getFirst().getOccurredAt());
        assertEquals(80.0, consumptions.findAllByCalendarId(mealId).getFirst().getQuantityUsed());
        service.deleteMeal(mealId, userId);
        service.deleteMeal(mealId, userId);
        assertEquals(100.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
        assertTrue(activities.findByMealEventIdAndCancelledAtIsNull(mealId).isEmpty());
        assertTrue(service.getMealHistory(userId).isEmpty());
        assertNotNull(calendars.findById(mealId).orElseThrow().getDeletedAt());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.recordMealAndDeductStock(userId, request, "mfds:1"));
        assertEquals(100.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
    }

    @Test void feedbackFailureRollsBackCalendarStockAndActivity() {
        doThrow(new IllegalStateException("forced feedback failure")).when(recipes).recordMealFeedback(any(), anyString());
        assertThrows(IllegalStateException.class, () -> service.recordMealAndDeductStock(userId, request(), "mfds:1"));
        assertEquals(100.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
        assertTrue(service.getMealHistory(userId).isEmpty());
        assertTrue(activities.findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                userId, time.minusDays(1), time.plusDays(1)).isEmpty());
    }

    @Test void concurrentResponseLossRetryDeductsOnlyOnce() throws Exception {
        var request = request();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Long> submit = () -> { start.await(); return service.recordMealAndDeductStock(userId, request).getMealEventId(); };
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(60.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
        assertEquals(1, service.getMealHistory(userId).size());
    }

    @Test void deleteFailureRollsBackRefundTombstoneAndCancelledHistory() {
        Long mealId = service.recordMealAndDeductStock(userId, request()).getMealEventId();
        doThrow(new IllegalStateException("forced sync failure")).when(recipes).synchronizeMealFeedback(any(), eq(true));
        assertThrows(IllegalStateException.class, () -> service.deleteMeal(mealId, userId));
        assertEquals(60.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
        assertNull(calendars.findById(mealId).orElseThrow().getDeletedAt());
        assertEquals(1, activities.findByMealEventIdAndCancelledAtIsNull(mealId).size());
    }

    @Test void updateFailureRollsBackReplacementConsumption() {
        Long mealId = service.recordMealAndDeductStock(userId, request()).getMealEventId();
        var update = new MealRecordUpdateRequest();
        ReflectionTestUtils.setField(update, "title", "롤백 대상");
        ReflectionTestUtils.setField(update, "mealType", "DINNER");
        ReflectionTestUtils.setField(update, "usedIngredients", List.of(item(80)));
        doThrow(new IllegalStateException("forced sync failure")).when(recipes).synchronizeMealFeedback(any(), eq(false));
        assertThrows(IllegalStateException.class, () -> service.updateMeal(mealId, userId, update));
        assertEquals(60.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
        assertEquals("당근볶음", calendars.findById(mealId).orElseThrow().getTitle());
        assertEquals(40.0, consumptions.findAllByCalendarId(mealId).getFirst().getQuantityUsed());
        assertEquals(40.0, activities.findByMealEventIdAndCancelledAtIsNull(mealId).getFirst().getQuantity());
    }

    @Test void differentUsersCannotOverspendSharedBatchConcurrently() throws Exception {
        Long otherUserId = new TransactionTemplate(transactionManager).execute(status -> {
            var other = users.save(User.builder().email(UUID.randomUUID() + "@example.test").isDeleted(false).build());
            members.save(FridgeMember.builder().fridge(fridges.findById(fridgeId).orElseThrow())
                    .user(other).role("MEMBER").status("ACCEPTED").build());
            return other.getId();
        });
        var firstRequest = request();
        var secondRequest = request();
        ReflectionTestUtils.setField(firstRequest, "usedIngredients", List.of(item(80)));
        ReflectionTestUtils.setField(secondRequest, "usedIngredients", List.of(item(80)));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> tryConcurrentMeal(start, userId, firstRequest));
            var second = executor.submit(() -> tryConcurrentMeal(start, otherUserId, secondRequest));
            start.countDown();
            assertEquals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(20.0, batches.findById(batchId).orElseThrow().getRemainingQuantity());
        assertEquals(1, service.getMealHistory(userId).size() + service.getMealHistory(otherUserId).size());
    }

    int tryConcurrentMeal(CountDownLatch start, Long actor, MealRecordRequest request) throws InterruptedException {
        start.await();
        try { service.recordMealAndDeductStock(actor, request); return 1; }
        catch (org.springframework.web.server.ResponseStatusException exception) {
            assertEquals(400, exception.getStatusCode().value());
            return 0;
        }
    }
}
