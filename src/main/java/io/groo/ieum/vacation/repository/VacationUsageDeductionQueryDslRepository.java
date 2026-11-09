package io.groo.ieum.vacation.repository;

import io.groo.core.type.YNType;
import io.groo.ieum.vacation.domain.VacationUsageDeduction;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

import static io.groo.ieum.vacation.domain.QVacationUsageDeduction.vacationUsageDeduction;

@Repository
@Primary
@RequiredArgsConstructor
public class VacationUsageDeductionQueryDslRepository implements VacationUsageDeductionRepository {
    private final EntityManager em;
    private final JPAQueryFactory query;

    @Override
    public void saveAll(List<VacationUsageDeduction> deductions) {
        for (VacationUsageDeduction deduction : deductions) {
            em.persist(deduction);
        }
    }

    @Override
    public List<VacationUsageDeduction> findByUsageId(Long usageId) {
        return query
                .selectFrom(vacationUsageDeduction)
                .join(vacationUsageDeduction.usage).fetchJoin()
                .join(vacationUsageDeduction.grant).fetchJoin()
                .where(vacationUsageDeduction.usage.rowId.eq(usageId))
                .fetch();
    }

    @Override
    public List<VacationUsageDeduction> findByGrantIds(List<Long> grantIds) {
        if (grantIds == null || grantIds.isEmpty()) {
            return List.of();
        }
        return query
                .selectFrom(vacationUsageDeduction)
                .join(vacationUsageDeduction.usage).fetchJoin()
                .join(vacationUsageDeduction.grant).fetchJoin()
                .where(vacationUsageDeduction.grant.rowId.in(grantIds)
                        .and(vacationUsageDeduction.usage.isDeleted.eq(YNType.N)))
                .fetch();
    }

    @Override
    public List<VacationUsageDeduction> findByUsageIds(List<Long> usageIds) {
        if (usageIds == null || usageIds.isEmpty()) {
            return List.of();
        }
        return query
                .selectFrom(vacationUsageDeduction)
                .join(vacationUsageDeduction.usage).fetchJoin()
                .join(vacationUsageDeduction.grant).fetchJoin()
                .where(vacationUsageDeduction.usage.rowId.in(usageIds))
                .orderBy(vacationUsageDeduction.rowId.asc())
                .fetch();
    }
}