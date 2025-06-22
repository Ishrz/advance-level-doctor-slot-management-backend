package com.doctor.slot.service;

import com.doctor.slot.dto.SlotManagementRequest;
import org.springframework.http.ResponseEntity;

public interface SlotService {

    ResponseEntity<?> createSlots(SlotManagementRequest request);

    ResponseEntity<?> blockDate(SlotManagementRequest request);

    ResponseEntity<?> deleteSlots(SlotManagementRequest request);
    
    ResponseEntity<?> bookSlot(SlotManagementRequest request);
    
    ResponseEntity<?> lockSlot(SlotManagementRequest request);
    
    ResponseEntity<?> markUnavailable(SlotManagementRequest request);
    
    ResponseEntity<?> recommendSlot(SlotManagementRequest request);
    
    ResponseEntity<?> bulkDelete(SlotManagementRequest request);
    
    ResponseEntity<?> getSlots(SlotManagementRequest request);







}
