package io.groo.ieum.work.repository;

import io.groo.core.type.YNType;
import io.groo.ieum.work.domain.WorkCode;
import io.groo.ieum.work.type.CodeType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static io.groo.ieum.work.domain.QWorkCode.workCode;

@Repository
@Primary
@RequiredArgsConstructor
public class WorkCodeQueryDslRepository implements WorkCodeRepository {
    private final EntityManager em;
    private final JPAQueryFactory query;

    @Override
    public void save(WorkCode workCode) {
        em.persist(workCode);
    }

    @Override
    public Optional<WorkCode> findByCode(String code) {
        return Optional.ofNullable(
                query
                        .selectFrom(workCode)
                        .where(
                                workCode.code.eq(code),
                                workCode.isDeleted.eq(YNType.N)
                        )
                        .fetchFirst()
        );
    }

    @Override
    public Optional<WorkCode> findByRowId(Long rowId) {
        return Optional.ofNullable(
                query
                        .selectFrom(workCode)
                        .where(
                                workCode.rowId.eq(rowId),
                                workCode.isDeleted.eq(YNType.N)
                        )
                        .fetchFirst()
        );
    }

    @Override
    public List<WorkCode> findAllByConditions(String parentWorkCode, Long parentWorkCodeId, Boolean parentIsNull, CodeType type) {
        BooleanBuilder builder = new BooleanBuilder();

        // 삭제되지 않은 데이터만 조회
        builder.and(workCode.isDeleted.eq(YNType.N));

        // 부모 코드 조건
        if (parentIsNull != null && parentIsNull) {
            // 최상위 코드 조회 (parent가 null)
            builder.and(workCode.parent.isNull());
        } else if (parentWorkCodeId != null) {
            // rowId로 특정 부모 코드의 하위 코드 조회 (우선순위 높음)
            WorkCode parent = findByRowId(parentWorkCodeId)
                    .orElseThrow(() -> new IllegalArgumentException("부모 코드를 찾을 수 없습니다"));
            builder.and(workCode.parent.eq(parent));
        } else if (parentWorkCode != null && !parentWorkCode.isEmpty()) {
            // 코드 문자열로 특정 부모 코드의 하위 코드 조회
            WorkCode parent = findByCode(parentWorkCode)
                    .orElseThrow(() -> new IllegalArgumentException("부모 코드를 찾을 수 없습니다"));
            builder.and(workCode.parent.eq(parent));
        }

        // 코드 타입 조건
        if (type != null) {
            builder.and(workCode.type.eq(type));
        }

        return query
                .selectFrom(workCode)
                .where(builder)
                .orderBy(workCode.orderSeq.asc())
                .fetch();
    }
}
