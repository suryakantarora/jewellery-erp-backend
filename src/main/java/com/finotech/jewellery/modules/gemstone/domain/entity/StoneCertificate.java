package com.finotech.jewellery.modules.gemstone.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A grading certificate issued by a lab (GIA, IGI, HRD…). The PDF or scan lives
 * in object storage; only the reference is kept here.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "stone_certificate", schema = "product")
public class StoneCertificate extends BaseEntity {

    @Column(name = "certificate_number", nullable = false, unique = true, length = 100)
    private String certificateNumber;

    @Column(name = "issuing_lab", nullable = false, length = 100)
    private String issuingLab;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "verification_url", length = 500)
    private String verificationUrl;

    @Column(name = "notes", length = 255)
    private String notes;
}
