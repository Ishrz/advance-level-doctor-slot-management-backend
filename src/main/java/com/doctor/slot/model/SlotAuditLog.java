package com.doctor.slot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long slotId;
    private Long doctorId;

    private String action; // e.g. BOOK_SLOT, CREATE_SLOT, DELETE_SLOT
    private String message; // details like "deleted due to holiday"

    private String performedBy; // for now: "system"
    private LocalDateTime timestamp;
}
