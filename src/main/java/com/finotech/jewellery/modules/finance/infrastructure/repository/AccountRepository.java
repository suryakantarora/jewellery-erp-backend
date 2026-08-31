package com.finotech.jewellery.modules.finance.infrastructure.repository;

import com.finotech.jewellery.modules.finance.domain.entity.Account;
import com.finotech.jewellery.modules.finance.domain.enums.AccountType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Account> findAllByOrderByCodeAsc();

    List<Account> findAllByAccountTypeOrderByCodeAsc(AccountType accountType);
}
