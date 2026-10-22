package com.referralportal.repository.jpa;

import com.referralportal.entity.CreditLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository backing {@link com.referralportal.repository.RequestBudgetRepository}. */
public interface CreditLedgerJpaRepository extends JpaRepository<CreditLedger, Long> {

    @Query("select coalesce(sum(c.delta), 0) from CreditLedger c where c.user.id = :userId")
    long sumDeltaByUserId(@Param("userId") long userId);
}
