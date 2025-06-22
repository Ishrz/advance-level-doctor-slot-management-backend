package com.doctor.slot.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.doctor.slot.dto.SlotManagementRequest;
import com.doctor.slot.model.*;
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

            LocalTime startTime = LocalTime.of(9, 0);
            LocalTime endTime = LocalTime.of(13, 0);

            while (startTime.plusMinutes(request.getSlotDuration()).isBefore(endTime.plusSeconds(1))) {
                Slot slot = Slot.builder()
                        .doctorId(request.getDoctorId())
                        .slotDate(date)
                        .startTime(startTime)
                        .endTime(startTime.plusMinutes(request.getSlotDuration()))
                        .slotType(request.getSlotType())
                        .slotStatus(SlotStatus.AVAILABLE)
                        .accessType(Optional.ofNullable(request.getAccessType()).orElse(SlotAccessType.NORMAL))
                        .location(request.getLocation())
                        .notes(request.getNotes())
                        .build();

                // Conflict Detection
                List<Slot> conflictingSlots = slotRepository.findAll().stream()
                        .filter(s -> s.getDoctorId().equals(slot.getDoctorId()))
                        .filter(s -> s.getSlotDate().isEqual(slot.getSlotDate()))
                        .filter(s -> slot.getStartTime().isBefore(s.getEndTime()) &&
                                     slot.getEndTime().isAfter(s.getStartTime()))
                        .toList();

                if (!conflictingSlots.isEmpty()) {
                    return ResponseEntity.status(409).body(Map.of(
                        "error", "Conflict detected",
                        "message", "Slot " + slot.getStartTime() + "–" + slot.getEndTime() + " on " + date + " conflicts with existing."
                    ));
                }

                slots.add(slot);
                startTime = startTime.plusMinutes(request.getSlotDuration());
            }
        }

        slotRepository.saveAll(slots);
        slots.forEach(s -> logAction(s.getId(), s.getDoctorId(), "CREATE_SLOT", "Created by system"));

        return ResponseEntity.ok(Map.of(
                "message", "Created " + slots.size() + " slots.",
                "slots", slots
        ));
    }

    @Override
    public ResponseEntity<?> blockDate(SlotManagementRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null || request.getDoctorId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        List<Slot> slots = slotRepository.findByDoctorIdAndSlotDateBetween(
                request.getDoctorId(), request.getStartDate(), request.getEndDate());

        slots.forEach(s -> s.setSlotStatus(SlotStatus.BLOCKED));
        slotRepository.saveAll(slots);

        return ResponseEntity.ok(Map.of(
                "message", "Blocked " + slots.size() + " slots",
                "count", slots.size()
        ));
    }

    @Override
    public ResponseEntity<?> deleteSlots(SlotManagementRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null || request.getDoctorId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        List<Slot> slotsToDelete = slotRepository.findByDoctorIdAndSlotDateBetween(
                request.getDoctorId(), request.getStartDate(), request.getEndDate());

        if (slotsToDelete.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No slots found to delete."));
        }

        slotsToDelete.forEach(s -> logAction(s.getId(), s.getDoctorId(), "DELETE_SLOT", "Removed by admin"));
        slotRepository.deleteAll(slotsToDelete);

        return ResponseEntity.ok(Map.of(
                "message", "Deleted " + slotsToDelete.size() + " slots.",
                "count", slotsToDelete.size()
        ));
    }

    @Override
    public ResponseEntity<?> bookSlot(SlotManagementRequest request) {
        if (request.getSlotId() == null || request.getDoctorId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing doctorId or slotId"));
        }

        Optional<Slot> optionalSlot = slotRepository.findById(request.getSlotId());
        if (optionalSlot.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot not found"));
        }

        Slot slot = optionalSlot.get();

        if (!slot.getDoctorId().equals(request.getDoctorId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot doesn't belong to this doctor"));
        }

        if (!slot.getSlotStatus().equals(SlotStatus.AVAILABLE)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot is not available"));
        }

        if (slot.getAccessType() == SlotAccessType.WALK_IN || slot.getAccessType() == SlotAccessType.EMERGENCY) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot reserved for walk-in/emergency only"));
        }

        long dailyBookings = slotRepository.findAll().stream()
                .filter(s -> s.getDoctorId().equals(request.getDoctorId()))
                .filter(s -> s.getSlotDate().equals(slot.getSlotDate()))
                .filter(s -> s.getSlotStatus() == SlotStatus.BOOKED)
                .count();

        if (dailyBookings >= 10) {
            return ResponseEntity.badRequest().body(Map.of("error", "Max 10 bookings allowed per day"));
        }

        boolean gapViolation = slotRepository.findAll().stream()
                .filter(s -> s.getDoctorId().equals(request.getDoctorId()))
                .filter(s -> s.getSlotDate().equals(slot.getSlotDate()))
                .anyMatch(s -> s.getSlotStatus() == SlotStatus.BOOKED &&
                        Math.abs(Duration.between(s.getStartTime(), slot.getStartTime()).toMinutes()) < 15);

        if (gapViolation) {
            return ResponseEntity.badRequest().body(Map.of("error", "Min 15-min gap required"));
        }

        if (slot.getSlotDate().isAfter(LocalDate.now().plusDays(7))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only 7-day advance bookings allowed"));
        }

        if (slot.getSlotDate().isEqual(LocalDate.now()) && LocalTime.now().isAfter(LocalTime.of(17, 0))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Same-day booking cutoff: 5 PM"));
        }

        slot.setSlotStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
        logAction(slot.getId(), slot.getDoctorId(), "BOOK_SLOT", "Slot booked");

        return ResponseEntity.ok(Map.of(
                "message", "Slot booked successfully",
                "slot", slot
        ));
    }

    @Override
    public ResponseEntity<?> lockSlot(SlotManagementRequest request) {
        if (request.getSlotId() == null || request.getDoctorId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing doctorId or slotId"));
        }

        Optional<Slot> optionalSlot = slotRepository.findById(request.getSlotId());
        if (optionalSlot.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot not found"));
        }

        Slot slot = optionalSlot.get();

        if (!slot.getDoctorId().equals(request.getDoctorId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Doctor mismatch"));
        }

        if (!slot.getSlotStatus().equals(SlotStatus.AVAILABLE)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot not available"));
        }

        slot.setSlotStatus(SlotStatus.PENDING);
        slot.setLockedAt(LocalDateTime.now());
        slotRepository.save(slot);

        return ResponseEntity.ok(Map.of(
                "message", "Slot locked (PENDING)",
                "slot", slot
        ));
    }

    @Override
    public ResponseEntity<?> markUnavailable(SlotManagementRequest request) {
        if (request.getDoctorId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
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
            return ResponseEntity.ok(Map.of("message", "No available slots to mark"));
        }

        slotsToBlock.forEach(slot -> {
            slot.setSlotStatus(SlotStatus.BLOCKED);
            logAction(slot.getId(), slot.getDoctorId(), "MARK_UNAVAILABLE", "Blocked for unavailability");
        });

        slotRepository.saveAll(slotsToBlock);

        return ResponseEntity.ok(Map.of(
                "message", "Marked " + slotsToBlock.size() + " slots as unavailable",
                "count", slotsToBlock.size()
        ));
    }

    @Override
    public ResponseEntity<?> recommendSlot(SlotManagementRequest request) {
        if (request.getDoctorId() == null || request.getStartDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing doctorId or preferred date"));
        }

        List<Slot> availableSlots = slotRepository.findAll().stream()
                .filter(slot -> slot.getDoctorId().equals(request.getDoctorId()))
                .filter(slot -> slot.getSlotDate().isEqual(request.getStartDate()))
                .filter(slot -> slot.getSlotStatus().equals(SlotStatus.AVAILABLE))
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();

        return ResponseEntity.ok(availableSlots); //  Frontend expects JSON array
    }

    @Override
    public ResponseEntity<?> bulkDelete(SlotManagementRequest request) {
        if (request.getDoctorId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing fields"));
        }

        List<Slot> slotsToDelete = slotRepository.findAll().stream()
                .filter(slot -> slot.getDoctorId().equals(request.getDoctorId()))
                .filter(slot -> !slot.getSlotDate().isBefore(request.getStartDate()) &&
                                !slot.getSlotDate().isAfter(request.getEndDate()))
                .toList();

        slotRepository.deleteAll(slotsToDelete);

        return ResponseEntity.ok(Map.of(
                "message", "Bulk deleted " + slotsToDelete.size() + " slots",
                "count", slotsToDelete.size()
        ));
    }

    private void logAction(Long slotId, Long doctorId, String action, String message) {
        SlotAuditLog log = SlotAuditLog.builder()
                .slotId(slotId)
                .doctorId(doctorId)
                .action(action)
                .message(message)
                .performedBy("system")
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(log);
    }
    
    @Override
    public ResponseEntity<?> getSlots(SlotManagementRequest request) {
        if (request.getDoctorId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return ResponseEntity.badRequest().body("Missing doctorId, startDate, or endDate.");
        }

        List<Slot> slots = slotRepository.findByDoctorIdAndSlotDateBetween(
            request.getDoctorId(),
            request.getStartDate(),
            request.getEndDate()
        );

        return ResponseEntity.ok(slots);
    }


    
}
