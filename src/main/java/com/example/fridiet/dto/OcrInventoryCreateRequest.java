package com.example.fridiet.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class OcrInventoryCreateRequest {

    private List<OcrIngredientItemRequest> items;
}
