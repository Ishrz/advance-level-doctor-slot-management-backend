package com.doctor.slot.controller;

import com.doctor.slot.dto.SlotManagementRequest;
import com.doctor.slot.model.SlotAction;
import com.doctor.slot.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/slot-management")
@RequiredArgsConstructor
public class SlotManagementController {

    private final SlotService slotService;

    @PostMapping
    public ResponseEntity<?> handleSlotAction(@RequestBody SlotManagementRequest request) {
        if (request.getAction() == null) {
            return ResponseEntity.badRequest().body("Missing action type.");
        }

        switch (request.getAction()) {
            case CREATE_SLOTS:
                return slotService.createSlots(request);
            case BLOCK_DATE:
                return slotService.blockDate(request);
            case DELETE_SLOTS:
                return slotService.deleteSlots(request);
            case BOOK_SLOT:
                return slotService.bookSlot(request);
            case LOCK_SLOT:
                return slotService.lockSlot(request);
            case MARK_UNAVAILABLE:
                return slotService.markUnavailable(request);
            case RECOMMEND_SLOT:
                return slotService.recommendSlot(request);
            case BULK_DELETE:
                return slotService.bulkDelete(request);



            default:
                return ResponseEntity.badRequest().body("Unsupported action.");
        }
    }
}
