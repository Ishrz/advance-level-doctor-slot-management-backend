package com.doctor.slot.service;

import com.doctor.slot.model.Slot;
import com.doctor.slot.model.SlotStatus;
import com.doctor.slot.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingSlotCleaner {

    private final SlotRepository slotRepository;

    //This runs every 60 seconds (1 min)
    @Scheduled(fixedRate = 60000)
    public void cleanExpiredPendingSlots() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(5);
        List<Slot> expiredSlots = slotRepository.findBySlotStatusAndLockedAtBefore(SlotStatus.PENDING, expiryTime);

        for (Slot slot : expiredSlots) {
            slot.setSlotStatus(SlotStatus.AVAILABLE);
            slot.setLockedAt(null);
        }

        if (!expiredSlots.isEmpty()) {
            slotRepository.saveAll(expiredSlots);
            log.info("🧹 Expired {} locked slots and marked them as AVAILABLE", expiredSlots.size());
        }
    }
}
