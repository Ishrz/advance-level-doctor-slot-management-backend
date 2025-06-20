package com.doctor.slot.repository;

import com.doctor.slot.model.SlotAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotAuditLogRepository extends JpaRepository<SlotAuditLog, Long> {
}
