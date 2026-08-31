package com.finotech.jewellery.modules.finance.api.request;

import com.finotech.jewellery.modules.finance.domain.enums.AccountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceRequests {

    private FinanceRequests() {
    }

    public record AccountRequest(@NotBlank @Size(max = 20) String code,
                                 @NotBlank @Size(max = 150) String name,
                                 @NotNull AccountType accountType,
                                 UUID parentId,
                                 boolean postable,
                                 UUID companyId,
                                 @Size(min = 3, max = 3) String currency,
                                 @Size(max = 500) String description) {
    }

    /**
     * A hand-entered journal. Debits must equal credits, which is checked when
     * it is posted.
     */
    public record ManualJournalRequest(@NotNull LocalDate entryDate,
                                       @NotBlank @Size(max = 500) String description,
                                       UUID branchId,
                                       @NotEmpty @Valid List<Line> lines) {

        public record Line(@NotBlank @Size(max = 20) String accountCode,
                           @DecimalMin("0.0") BigDecimal debit,
                           @DecimalMin("0.0") BigDecimal credit,
                           @Size(max = 500) String description) {
        }
    }
}
