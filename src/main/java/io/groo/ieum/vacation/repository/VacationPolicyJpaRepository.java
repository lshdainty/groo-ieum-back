package io.groo.ieum.vacation.repository;

import io.groo.core.type.YNType;
import io.groo.ieum.vacation.domain.VacationPolicy;
import io.groo.ieum.vacation.type.VacationType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("vacationPolicyJpaRepository")
@RequiredArgsConstructor
public class VacationPolicyJpaRepository implements VacationPolicyRepository {
    private final EntityManager em;

    @Override
    public void save(VacationPolicy vacationPolicy) {
        em.persist(vacationPolicy);
    }

    @Override
    public Optional<VacationPolicy> findByRowId(Long rowId) {
        return Optional.ofNullable(em.find(VacationPolicy.class, rowId));
    }

    @Override
    public List<VacationPolicy> findVacationPolicies() {
        return em.createQuery(
                        "select vp from VacationPolicy vp where vp.isDeleted = :isDeleted", VacationPolicy.class)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
    }

    @Override
    public boolean existsByName(String name) {
        Long count = em.createQuery(
                        "select count(vp) from VacationPolicy vp where vp.name = :name and vp.isDeleted = :isDeleted", Long.class)
                .setParameter("name", name)
                .setParameter("isDeleted", YNType.N)
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    public boolean existsByNameAndRowIdNot(String name, Long rowId) {
        Long count = em.createQuery(
                        "select count(vp) from VacationPolicy vp where vp.name = :name and vp.rowId <> :rowId and vp.isDeleted = :isDeleted", Long.class)
                .setParameter("name", name)
                .setParameter("rowId", rowId)
                .setParameter("isDeleted", YNType.N)
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    public List<VacationPolicy> findByVacationType(VacationType vacationType) {
        return em.createQuery(
                        "select vp from VacationPolicy vp where vp.vacationType = :vacationType and vp.isDeleted = :isDeleted", VacationPolicy.class)
                .setParameter("vacationType", vacationType)
                .setParameter("isDeleted", YNType.N)
                .getResultList();
    }
}
