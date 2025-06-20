package com.doctor.slot.dto;

import java.time.LocalDate;

import com.doctor.slot.model.SlotAccessType;
import com.doctor.slot.model.SlotAction;
import com.doctor.slot.model.SlotType;

import lombok.Data;

@Data
public class SlotManagementRequest {

    private SlotAction action;

    private Long doctorId;
    private String location;
    private String notes;

    // Date range for creating slots
    private LocalDate startDate;
    private LocalDate endDate;

    // Slot configuration
    private Integer slotDuration; // e.g. 30 mins
    private SlotType slotType;
    
    private Long slotId; 
    
    private SlotAccessType accessType;

}
