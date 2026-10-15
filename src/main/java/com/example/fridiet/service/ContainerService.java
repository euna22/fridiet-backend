package com.example.fridiet.service;

import com.example.fridiet.dto.ContainerDeductRequest;
import com.example.fridiet.dto.ContainerScanResponse;
import com.example.fridiet.dto.ContainerRegisterRequest;
import com.example.fridiet.dto.ContainerSplitRequest;
import com.example.fridiet.entity.*;
import com.example.fridiet.repository.*;
import com.example.fridiet.util.QrCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContainerService {

    private final UserRepository userRepository;
    private final ContainerRepository containerRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final FridgeRepository fridgeRepository;
    private final IngredientMasterRepository ingredientMasterRepository;
    private final InventoryActivityService inventoryActivityService;

    @Value("${backend.qr-base-url}")
    private String qrBaseUrl;

    /**
     * QR 코드 생성
     */
    public byte[] generateContainerQrImage(String customToken) throws Exception {

        String qrToken = (customToken != null && !customToken.isBlank()) ? customToken : UUID.randomUUID().toString();

        String scanUrl = qrBaseUrl + "/qr-scanner?token=" + qrToken;

        return QrCodeUtil.generateQrCodeImage(scanUrl, 300, 300);
    }

    /**
     * QR 스티커 등록 & 기존 재고 매핑 또는 신규 재고 생성
     */
    @Transactional
    public ContainerScanResponse registerOrUpdateContainer(Long userId, ContainerRegisterRequest request) {
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Fridge fridge = fridgeRepository.findById(request.getFridgeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다. ID: " + request.getFridgeId()));
        inventoryActivityService.requireAccess(userId, fridge);

        // 이미 이 QR 코드가 등록되어 있는지 확인, 없으면 새로 생성
        Container container = containerRepository.findByQrCodeToken(request.getQrCodeToken())
                .orElseGet(() -> Container.builder()
                        .fridge(fridge)
                        .qrCodeToken(request.getQrCodeToken())
                        .name(request.getContainerName() != null ? request.getContainerName() : "스마트 용기")
                        .build());

        inventoryActivityService.requireAccess(userId, container.getFridge());
        if (container.getFridge().getId() != fridge.getId()) {
            throw new IllegalArgumentException("다른 냉장고에 등록된 용기는 연결할 수 없습니다.");
        }
        if (container.getCurrentBatch() != null) {
            if (request.getExistingBatchId() != container.getCurrentBatch().getId())
                return ContainerScanResponse.from(container);
            throw QuantityPolicy.invalid("이미 재고가 연결된 용기입니다. 기존 연결을 확인해주세요.");
        }
        // 식재료 정보가 같이 들어온 경우 InventoryBatch 생성/연결
        if (request.getExistingBatchId() != null) {
            // 기존 재고 연결
            InventoryBatch existingBatch = inventoryBatchRepository.findById(request.getExistingBatchId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다. ID: " + request.getExistingBatchId()));
            if (!existingBatch.getFridge().getId().equals(fridge.getId())) {
                throw new IllegalArgumentException("같은 냉장고의 재고만 연결할 수 있습니다.");
            }
            if (!java.util.Set.of("ACTIVE", "EXPIRED").contains(existingBatch.getStatus()))
                throw QuantityPolicy.invalid("이미 소진되거나 폐기된 재고는 연결할 수 없습니다.");
            container.linkInventoryBatch(existingBatch);
        } else if (request.getIngredientName() != null && !request.getIngredientName().isBlank()) {
            // 신규 재고 생성 후 연결
            String trimmedName = request.getIngredientName().trim();
            IngredientMaster master = ingredientMasterRepository.findByCanonicalName(trimmedName).orElse(null);

            InventoryBatch newBatch = InventoryBatch.builder()
                    .fridge(fridge)
                    .ingredientMaster(master)
                    .ingredientName(trimmedName)
                    .unitType(request.getUnitType() != null ? request.getUnitType() : UnitType.QUANTITY)
                    .remainingQuantity(request.getRemainingQuantity())
                    .customQuantityText(request.getCustomQuantityText())
                    .unit(request.getUnit())
                    .consumeByDate(request.getConsumeByDate() != null ? request.getConsumeByDate() : LocalDate.now().plusDays(7))
                    .locationCategory(request.getLocationCategory() != null ? request.getLocationCategory() : "REFRIGERATED")
                    .status("ACTIVE")
                    .createdBy(userId)
                    .build();

            if(request.getUnitType() == UnitType.QUANTITY) {
                newBatch.normalizeQuantity(request);
            }
            InventoryBatch savedBatch = inventoryBatchRepository.save(newBatch);
            container.linkInventoryBatch(savedBatch);
        }

        Container savedContainer = containerRepository.save(container);
        return ContainerScanResponse.from(savedContainer);
    }
    /**
     * 기존 재고 컨테이너에 분할 저장
     */
    @Transactional
    public ContainerScanResponse registerWithSplit(Long userId, ContainerSplitRequest request) {

        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Fridge fridge = fridgeRepository.findById(request.getFridgeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다."));

        inventoryActivityService.requireAccess(userId, fridge);

        Container container = containerRepository.findForUpdate(request.getQrCodeToken())
                .orElseGet(() -> Container.builder()
                        .fridge(fridge)
                        .qrCodeToken(request.getQrCodeToken())
                        .name(request.getContainerName() != null ? request.getContainerName() : "스마트 용기")
                        .build());

        if(container.getFridge().getId() != fridge.getId()) {
            throw new IllegalArgumentException("해당 용기는 이 냉장고에 등록할 수 없습니다.");
        }

        if(container.getCurrentBatch() != null) {
            throw new IllegalArgumentException("이미 재고가 들어있는 용기입니다.");
        }

        InventoryBatch existingBatch = inventoryBatchRepository.findById(request.getSourceBatchId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다."));

        if(!existingBatch.getFridge().getId().equals(fridge.getId())) {
            throw new IllegalArgumentException("해당 냉장고에 존재하지 않는 재고입니다.");
        }

        if(!"ACTIVE".equals(existingBatch.getStatus())) {
            throw new IllegalArgumentException("사용 가능한 재고가 아닙니다.");
        }

        String unit = request.getSourceUnit();

        if(request.getContainerUnitType() == UnitType.QUANTITY || request.getContainerUnitType() == UnitType.PERCENT) {
            Double transferredQuantity = request.getTransferredQuantity();
            Double containerRemainingQuantity = request.getContainerRemainingQuantity();

            if(transferredQuantity > existingBatch.getRemainingQuantity()) {
                throw new IllegalArgumentException("기존 재고보다 많이 옮길 수 없습니다.");
            }
            if(transferredQuantity == 0.0) {
                throw new IllegalArgumentException("옮길 양을 입력해야 합니다.");
            }
            if(containerRemainingQuantity == 0) {
                throw new IllegalArgumentException("용기의 초기 잔량을 입력해야 등록할 수 있습니다.");
            }
            if(request.getContainerUnitType() == UnitType.PERCENT) unit = "%";
            existingBatch.consumeQuantity(transferredQuantity, null);
        } else if(request.getContainerUnitType() == UnitType.CUSTOM) {
            String containerRemainingText = request.getContainerRemainingText();

            if(containerRemainingText.isBlank() || "0".equals(containerRemainingText)) {
                throw new IllegalArgumentException("용기의 초기 잔량을 입력해야 등록할 수 있습니다.");
            }
            existingBatch.consumeQuantity(null, containerRemainingText);
        }

        InventoryBatch containerBatch = InventoryBatch.builder()
                .fridge(fridge)
                .ingredientMaster(existingBatch.getIngredientMaster())
                .ingredientName(existingBatch.getIngredientName())
                .unitType(request.getContainerUnitType())
                .unit(unit)
                .remainingQuantity(request.getContainerUnitType() == UnitType.CUSTOM ? null : request.getContainerRemainingQuantity())
                .customQuantityText(request.getContainerUnitType() == UnitType.CUSTOM ? request.getTransferredText() : null)
                .countBasis(existingBatch.getCountBasis())
                .perCountQuantity(existingBatch.getPerCountQuantity())
                .perCountUnit(existingBatch.getPerCountUnit())
                .consumeByDate(existingBatch.getConsumeByDate())
                .locationCategory(existingBatch.getLocationCategory())
                .status("ACTIVE")
                .createdBy(userId)
                .build();

        InventoryBatch savedBatch = inventoryBatchRepository.save(containerBatch);

        container.linkInventoryBatch(savedBatch);

        Container savedContainer = containerRepository.save(container);

        return ContainerScanResponse.from(savedContainer);
    }

    /**
     * QR 코드 스캔 시 용기 및 연결된 재고 정보 조회
     */
    @Transactional(readOnly = true)
    public ContainerScanResponse scanContainer(Long userId, String qrCodeToken) {

        if (qrCodeToken == null || qrCodeToken.isBlank()) {
            throw new IllegalArgumentException("QR 코드 토큰이 올바르지 않습니다.");
        }

        // 등록되지 않은 QR 코드라면 isEmpty = true 상태의 DTO 반환
        return containerRepository.findByQrCodeToken(qrCodeToken)
                .map(container -> {
                    inventoryActivityService.requireAccess(userId, container.getFridge());
                    return ContainerScanResponse.from(container);
                })
                .orElseGet(() -> ContainerScanResponse.builder()
                        .qrCodeToken(qrCodeToken)
                        .containerName("미등록 스마트 용기")
                        .isEmpty(true)
                        .build());
    }

    /**
     * QR 스캔 화면에서 재고 수량 바로 차감 (굴소스 5스푼, 두부 반모 등)
     */
    @Transactional
    public ContainerScanResponse deductQuantityByQr(Long userId, String qrCodeToken, ContainerDeductRequest request) {
        Container container = containerRepository.findByQrCodeToken(qrCodeToken)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 QR 코드입니다. Token: " + qrCodeToken));

        inventoryActivityService.requireAccess(userId, container.getFridge());
        InventoryBatch batch = container.getCurrentBatch();
        if(batch == null || "CUNSUMED".equals(batch.getStatus()) || "DISCARDED".equals(batch.getStatus())) {
            throw new IllegalStateException("해당 용기는 현재 비어있습니다.");
        }
        batch = inventoryBatchRepository.findForUpdate(batch.getId()).orElseThrow();

        if(!"ACTIVE".equals(batch.getStatus())) {
            throw new IllegalArgumentException("사용 가능한 재고가 아닙니다.");
        }

        // QUANTITY, PERCENT, CUSTOM 차감 로직 수행
        if(request.getUnitType() == UnitType.CUSTOM) {
            batch.consumeQuantity(null, request.getCustomText());
        } else {
            batch.consumeQuantity(request.getQuantityUsed(), null);
        }
        inventoryActivityService.record(userId, batch, "CONSUMED",
                request.getUnitType() == UnitType.CUSTOM ? null : request.getQuantityUsed(), request.getCustomText(),
                java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));

        // 재고가 모두 소진된 경우 용기 연결 해제
        if("CONSUMED".equals(batch.getStatus()) || "DISCARDED".equals(batch.getStatus())) {
            container.clearContainer();
        }

        return ContainerScanResponse.from(container);
    }

    /**
     * 비어있는 용기에 새로운 재고(InventoryBatch) 매핑/교체
     */
    @Transactional
    public void linkNewBatchToContainer(Long containerId, Long batchId) {
        Container container = containerRepository.findById(containerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 용기입니다. ID: " + containerId));

        InventoryBatch batch = inventoryBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다. ID: " + batchId));

        container.linkInventoryBatch(batch);
    }
}
