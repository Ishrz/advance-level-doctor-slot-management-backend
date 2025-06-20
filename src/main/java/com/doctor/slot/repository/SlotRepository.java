package com.doctor.slot.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.doctor.slot.model.Slot;
import com.doctor.slot.model.SlotStatus;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {
    // custom queries here later if needed
	List<Slot> findByDoctorIdAndSlotDateBetween(Long doctorId, LocalDate startDate, LocalDate endDate);
	
	List<Slot> findBySlotStatusAndLockedAtBefore(SlotStatus status, LocalDateTime time);



}
