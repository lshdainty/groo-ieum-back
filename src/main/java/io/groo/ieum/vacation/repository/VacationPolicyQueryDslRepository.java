package io.groo.ieum.vacation.repository;

import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.type.VacationType;
import io.groo.core.type.YNType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static io.groo.ieum.vacation.domain.QVacationPolicy.vacationPolicy;


@Repository
@Primary
@RequiredArgsConstructor
public class VacationPolicyQueryDslRepository implements VacationPolicyRepository {
    private final EntityManager em;

    private final JPAQueryFactory query;

    @Override
    public void save(VacationPolicy vacationPolicy) {
        em.persist(vacationPolicy);
    }

    @Override
    public Optional<VacationPolicy> findByRowId(Long rowId) {
        return Optional.ofNullable(query
                .selectFrom(vacationPolicy)
                .where(vacationPolicy.rowId.eq(rowId))
                .fetchOne()
        );
    }

    @Override
    public List<VacationPolicy> findVacationPolicies() {
        return query
                .selectFrom(vacationPolicy)
                .where(vacationPolicy.isDeleted.eq(YNType.N))
                .fetch();
    }

    @Override
    public boolean existsByName(String name) {
        Integer count = query
                .selectOne()
                .from(vacationPolicy)
                .where(vacationPolicy.name.eq(name)
                        .and(vacationPolicy.isDeleted.eq(YNType.N)))
                .fetchFirst();
        return count != null;
    }

    @Override
    public boolean existsByNameAndRowIdNot(String name, Long rowId) {
        Integer count = query
                .selectOne()
                .from(vacationPolicy)
                .where(vacationPolicy.name.eq(name)
                        .and(vacationPolicy.rowId.ne(rowId))
                        .and(vacationPolicy.isDeleted.eq(YNType.N)))
                .fetchFirst();
        return count != null;
    }

    @Override
    public List<VacationPolicy> findByVacationType(VacationType vacationType) {
        return query
                .selectFrom(vacationPolicy)
                .where(vacationPolicy.vacationType.eq(vacationType)
                        .and(vacationPolicy.isDeleted.eq(YNType.N)))
                .fetch();
    }
}
