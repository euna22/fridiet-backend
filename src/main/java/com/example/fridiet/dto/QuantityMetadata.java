package com.example.fridiet.dto;

@lombok.Getter
@lombok.Setter
public class QuantityMetadata {
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    private boolean metadataUpdated;
    public boolean hasMetadataUpdates() { return metadataUpdated; }
    public void setCountBasis(String value) { countBasis = value; metadataUpdated = true; }
    public void setPerCountQuantity(Double value) { perCountQuantity = value; metadataUpdated = true; }
    public void setPerCountUnit(String value) { perCountUnit = value; metadataUpdated = true; }
    @jakarta.validation.constraints.Size(max=100)
    private String countBasis;
    private Double perCountQuantity;
    private String perCountUnit;
    // 명시적인 포장 정보가 있을 때만 총내용량을 계산한다.
    private Double packageCount;
    private Double packageQuantity;
    private String packageUnit;
}
