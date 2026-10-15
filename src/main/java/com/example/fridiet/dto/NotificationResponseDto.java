package com.example.fridiet.dto;

import com.example.fridiet.entity.FcmNotification;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Getter
@Builder
public class NotificationResponseDto {
    private Long id;
    private String type;
    private String title;
    private String message;
    private String time;
    private String dateGroup;

    @JsonProperty("isRead")
    private boolean isRead;

    private Long ingredientId;
    private String ingredientNames;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);
    public static NotificationResponseDto from(FcmNotification entity, LocalDate today, LocalDate yesterday) {

        LocalDate createdDate = entity.getCreatedAt().toLocalDate();

        String dateGroup;
        if(createdDate.equals(today)) {
            dateGroup = "오늘";
        } else if(createdDate.equals(yesterday)) {
            dateGroup = "어제";
        } else {
            dateGroup = createdDate.format(DATE_FORMATTER);
        }

        String formattedTime = entity.getCreatedAt().format(TIME_FORMATTER);


        return NotificationResponseDto.builder()
                .id(entity.getId())
                .type(entity.getType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .time(formattedTime)
                .dateGroup(dateGroup)
                .isRead(entity.isRead())
                .ingredientId(entity.getIngredientId())
                .ingredientNames(entity.getIngredientNameText())
                .build();
    }
}
