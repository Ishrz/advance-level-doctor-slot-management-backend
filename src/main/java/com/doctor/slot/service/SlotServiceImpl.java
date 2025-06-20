package com.doctor.slot.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.doctor.slot.dto.SlotManagementRequest;
import com.doctor.slot.model.Slot;
import com.doctor.slot.model.SlotAccessType;
import com.doctor.slot.model.SlotAuditLog;
import com.doctor.slot.model.SlotStatus;
import com.doctor.slot.repository.SlotAuditLogRepository;
import com.doctor.slot.repository.SlotRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {
	
	@Autowired
	private SlotAuditLogRepository auditLogRepository;


    private final SlotRepository slotRepository;

    @Override
    public ResponseEntity<?> createSlots(SlotManagementRequest request) {
        List<Slot> slots = new ArrayList<>();

        for (LocalDate date = request.getStartDate();
             !date.isAfter(request.getEndDate());
             date = date.plusDays(1)) {

            LocalTime startTime = LocalTime.of(9, 0);  // 9:00 AM
            LocalTime endTime = LocalTime.of(13, 0);   // 1:00 PM

            while (startTime.plusMinutes(request.getSlotDuration()).isBefore(endTime.plusSeconds(1))) {

                Slot slot = Slot.builder()
                        .doctorId(request.getDoctorId())
                        .slotDate(date)
                        .startTime(startTime)
                        .endTime(startTime.plusMinutes(request.getSlotDuration()))
                        .slotType(request.getSlotType())
                        .slotStatus(SlotStatus.AVAILABLE)
                        .accessType(request.getAccessType() != null ? request.getAccessType() : SlotAccessType.NORMAL)
                        .location(request.getLocation())
                        .notes(request.getNotes())
                        .build();

                // CONFLICT DETECTION START
                List<Slot> conflictingSlots = slotRepository.findAll().stream()
                        .filter(s -> s.getDoctorId().equals(slot.getDoctorId()))
                        .filter(s -> s.getSlotDate().isEqual(slot.getSlotDate()))
                        .filter(s -> 
                            slot.getStartTime().isBefore(s.getEndTime()) &&
                            slot.getEndTime().isAfter(s.getStartTime())
                        )
                        .toList();

                if (!conflictingSlots.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(" Conflict: Slot for " + slot.getSlotDate() +
                                  " from " + slot.getStartTime() + " to " + slot.getEndTime() +
                                  " overlaps with existing slot(s).");
                }
                // CONFLICT DETECTION END

                slots.add(slot);
                startTime = startTime.plusMinutes(request.getSlotDuration());
            }
        }

        slotRepository.saveAll(slots);
        for (Slot s : slots) {
            logAction(s.getId(), s.getDoctorId(), "CREATE_SLOT", "Slot created.");
        }

        return ResponseEntity.ok(" Created " + slots.size() + " slots.");
    }


    @Override
    public ResponseEntity<?> blockDate(SlotManagementRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null || request.getDoctorId() == null) {
            return ResponseEntity.badRequest().body("Missing required fields.");
        }

        List<Slot> slots = slotRepository.findByDoctorIdAndSlotDateBetween(
                request.getDoctorId(),
                request.getStartDate(),
                request.getEndDate()
        );

        for (Slot slot : slots) {
            slot.setSlotStatus(SlotStatus.BLOCKED);
        }

        slotRepository.saveAll(slots);

        return ResponseEntity.ok("Blocked " + slots.size() + " slots between " +
                request.getStartDate() + " and " + request.getEndDate());
    }


    @Override
    public ResponseEntity<?> deleteSlots(SlotManagementRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null || request.getDoctorId() == null) {
            return ResponseEntity.badRequest().body("Missing required fields.");
        }

        List<Slot> slotsToDelete = slotRepository.findByDoctorIdAndSlotDateBetween(
                request.getDoctorId(),
                request.getStartDate(),
                request.getEndDate()
        );

        if (slotsToDelete.isEmpty()) {
            return ResponseEntity.ok("No slots found to delete.");
        }
        
        for (Slot s : slotsToDelete) {
            logAction(
                s.getId(),
                s.getDoctorId(),
                "DELETE_SLOT",
                "Slot deleted due to leave or admin removal."
            );
        }

        slotRepository.deleteAll(slotsToDelete);

        return ResponseEntity.ok("Deleted " + slotsToDelete.size() + " slots between " +
                request.getStartDate() + " and " + request.getEndDate());
    }
    
    
    @Override
    public ResponseEntity<?> bookSlot(SlotManagementRequest request) {
        if (request.getSlotId() == null || request.getDoctorId() == null) {
            return ResponseEntity.badRequest().body("Missing slotId or doctorId.");
        }

        Optional<Slot> optionalSlot = slotRepository.findById(request.getSlotId());

        if (optionalSlot.isEmpty()) {
            return ResponseEntity.badRequest().body("Slot not found.");
        }

        Slot slot = optionalSlot.get();

        // Check if slot belongs to the given doctor and is available
        if (!slot.getDoctorId().equals(request.getDoctorId())) {
            return ResponseEntity.badRequest().body("Slot does not belong to this doctor.");
        }

        if (!slot.getSlotStatus().equals(SlotStatus.AVAILABLE)) {
            return ResponseEntity.badRequest().body("Slot is not available.");
        }

        // Rule: Block Walk-In or Emergency slots
        if (slot.getAccessType() == SlotAccessType.WALK_IN || slot.getAccessType() == SlotAccessType.EMERGENCY) {
            return ResponseEntity.badRequest().body(" This slot is reserved for walk-ins or emergencies only.");
        }

        // Rule 1: Max 10 bookings per doctor per day
        long dailyBookings = slotRepository.findAll().stream()
                .filter(s -> s.getDoctorId().equals(request.getDoctorId()))
                .filter(s -> s.getSlotDate().equals(slot.getSlotDate()))
                .filter(s -> s.getSlotStatus() == SlotStatus.BOOKED)
                .count();

        if (dailyBookings >= 10) {
            return ResponseEntity.badRequest().body(" Doctor already has 10 bookings for " + slot.getSlotDate());
        }

        // Rule 2: Minimum 15-minute gap between appointments
        boolean gapViolation = slotRepository.findAll().stream()
                .filter(s -> s.getDoctorId().equals(request.getDoctorId()))
                .filter(s -> s.getSlotDate().equals(slot.getSlotDate()))
                .anyMatch(s ->
                        s.getSlotStatus() == SlotStatus.BOOKED &&
                        Math.abs(Duration.between(s.getStartTime(), slot.getStartTime()).toMinutes()) < 15
                );

        if (gapViolation) {
            return ResponseEntity.badRequest().body(" Cannot book: Less than 15 minutes gap from another booking.");
        }

        // Rule 3: Max 7 days advance booking
        if (slot.getSlotDate().isAfter(LocalDate.now().plusDays(7))) {
            return ResponseEntity.badRequest().body(" You can only book slots up to 7 days in advance.");
        }

        // Rule 4: Same-day booking cutoff before 5:00 PM
        if (slot.getSlotDate().isEqual(LocalDate.now()) && LocalTime.now().isAfter(LocalTime.of(17, 0))) {
            return ResponseEntity.badRequest().body(" Same-day bookings must be made before 5:00 PM.");
        }

        //  Mark slot as booked
        slot.setSlotStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
        
        logAction(slot.getId(), slot.getDoctorId(), "BOOK_SLOT", "Slot booked successfully.");


        return ResponseEntity.ok(" Slot " + slot.getId() + " has been booked.");
    }

    
    @Override
    public ResponseEntity<?> lockSlot(SlotManagementRequest request) {
        if (request.getSlotId() == null || request.getDoctorId() == null) {
            return ResponseEntity.badRequest().body("Missing slotId or doctorId.");
        }

        Optional<Slot> optionalSlot = slotRepository.findById(request.getSlotId());

        if (optionalSlot.isEmpty()) {
            return ResponseEntity.badRequest().body("Slot not found.");
        }

        Slot slot = optionalSlot.get();

        if (!slot.getDoctorId().equals(request.getDoctorId())) {
            return ResponseEntity.badRequest().body("Slot does not belong to this doctor.");
        }

        if (!slot.getSlotStatus().equals(SlotStatus.AVAILABLE)) {
            return ResponseEntity.badRequest().body("Slot is not available for locking.");
        }

        slot.setSlotStatus(SlotStatus.PENDING);
        slot.setLockedAt(LocalDateTime.now());
        slotRepository.save(slot);

        return ResponseEntity.ok("Slot " + slot.getId() + " is now locked (PENDING) for booking.");
    }
    
    
    @Override
    public ResponseEntity<?> markUnavailable(SlotManagementRequest request) {
        if (request.getDoctorId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().body("Missing doctorId, startDate, or endDate.");
        }

        List<Slot> slotsToBlock = slotRepository.findAll().stream()
            .filter(slot -> slot.getDoctorId().equals(request.getDoctorId()))
            .filter(slot -> !slot.getSlotStatus().equals(SlotStatus.BOOKED))
            .filter(slot -> !slot.getSlotStatus().equals(SlotStatus.BLOCKED))
            .filter(slot -> !slot.getSlotStatus().equals(SlotStatus.PENDING))
            .filter(slot -> !slot.getSlotDate().isBefore(request.getStartDate())
                         && !slot.getSlotDate().isAfter(request.getEndDate()))
            .toList();

        if (slotsToBlock.isEmpty()) {
            return ResponseEntity.ok("No slots were marked as unavailable.");
        }

        for (Slot slot : slotsToBlock) {
            slot.setSlotStatus(SlotStatus.BLOCKED);

            //  Log each blocked slot
            logAction(
                slot.getId(),
                slot.getDoctorId(),
                "MARK_UNAVAILABLE",
                "Slot marked as UNAVAILABLE by system"
            );
        }

        slotRepository.saveAll(slotsToBlock);

        return ResponseEntity.ok("Marked " + slotsToBlock.size() + " slots as UNAVAILABLE (BLOCKED).");
    }

    
    @Override
    public ResponseEntity<?> recommendSlot(SlotManagementRequest request) {
        if (request.getDoctorId() == null || request.getStartDate() == null) {
            return ResponseEntity.badRequest().body("Missing doctorId or preferredDate.");
        }

        List<Slot> availableSlots = slotRepository.findAll().stream()
                .filter(slot -> slot.getDoctorId().equals(request.getDoctorId()))
                .filter(slot -> slot.getSlotDate().isEqual(request.getStartDate()))
                .filter(slot -> slot.getSlotStatus().equals(SlotStatus.AVAILABLE))
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();

        if (availableSlots.isEmpty()) {
            return ResponseEntity.ok(" No available slots found for this doctor on given date.");
        }

        Slot recommended = availableSlots.get(0);
        String response = " Recommended slot: " + recommended.getSlotDate() + " | " +
                recommended.getStartTime() + " to " + recommended.getEndTime() + " at " + recommended.getLocation();

        return ResponseEntity.ok(response);
    }
    
    @Override
    public ResponseEntity<?> bulkDelete(SlotManagementRequest request) {
        if (request.getDoctorId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().body(" Missing required fields: doctorId, startDate, endDate.");
        }

        List<Slot> slotsToDelete = slotRepository.findAll().stream()
            .filter(slot -> slot.getDoctorId().equals(request.getDoctorId()))
            .filter(slot -> !slot.getSlotDate().isBefore(request.getStartDate()) && !slot.getSlotDate().isAfter(request.getEndDate()))
            .toList();

        slotRepository.deleteAll(slotsToDelete);

        return ResponseEntity.ok(" Deleted " + slotsToDelete.size() + " slots for Doctor ID " +
                request.getDoctorId() + " between " + request.getStartDate() + " and " + request.getEndDate() + ".");
    }
    
    private void logAction(Long slotId, Long doctorId, String action, String message) {
        SlotAuditLog log = SlotAuditLog.builder()
                .slotId(slotId)
                .doctorId(doctorId)
                .action(action)
                .message(message)
                .performedBy("system") // in future this can be replaced with username from session/token
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(log);
    }








}
