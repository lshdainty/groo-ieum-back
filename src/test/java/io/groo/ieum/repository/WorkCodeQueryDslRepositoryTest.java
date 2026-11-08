package io.groo.ieum.repository;

import io.groo.ieum.work.domain.WorkCode;
import io.groo.ieum.work.repository.WorkCodeQueryDslRepository;
import io.groo.ieum.work.type.CodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({WorkCodeQueryDslRepository.class, TestQuerydslConfig.class})
@Transactional
@DisplayName("QueryDSL 업무코드 레포지토리 테스트")
class WorkCodeQueryDslRepositoryTest {
    @Autowired
    private WorkCodeQueryDslRepository workCodeRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("업무코드 저장 및 코드로 조회")
    void save() {
        // given
        WorkCode workCode = WorkCode.createWorkCode("GRP001", "개발팀", CodeType.LABEL, null, 1);

        // when
        workCodeRepository.save(workCode);
        em.flush();
        em.clear();

        // then
        Optional<WorkCode> findCode = workCodeRepository.findByCode("GRP001");
        assertThat(findCode.isPresent()).isTrue();
        assertThat(findCode.get().getName()).isEqualTo("개발팀");
        assertThat(findCode.get().getType()).isEqualTo(CodeType.LABEL);
    }

    @Test
    @DisplayName("코드로 조회 시 업무코드가 없으면 빈 Optional 반환")
    void findByCodeEmpty() {
        // when
        Optional<WorkCode> findCode = workCodeRepository.findByCode("NONEXISTENT");

        // then
        assertThat(findCode.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("삭제된 업무코드는 코드로 조회 시 제외")
    void findByCodeExcludesDeleted() {
        // given
        WorkCode workCode = WorkCode.createWorkCode("DEL001", "삭제코드", CodeType.LABEL, null, 1);
        workCodeRepository.save(workCode);
        workCode.deleteWorkCode();
        em.flush();
        em.clear();

        // when
        Optional<WorkCode> findCode = workCodeRepository.findByCode("DEL001");

        // then
        assertThat(findCode.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Id로 업무코드 조회")
    void findById() {
        // given
        WorkCode workCode = WorkCode.createWorkCode("GRP001", "개발팀", CodeType.LABEL, null, 1);
        workCodeRepository.save(workCode);
        em.flush();
        em.clear();

        // when
        Optional<WorkCode> findCode = workCodeRepository.findByRowId(workCode.getRowId());

        // then
        assertThat(findCode.isPresent()).isTrue();
        assertThat(findCode.get().getCode()).isEqualTo("GRP001");
    }

    @Test
    @DisplayName("Id로 조회 시 업무코드가 없으면 빈 Optional 반환")
    void findByIdEmpty() {
        // when
        Optional<WorkCode> findCode = workCodeRepository.findByRowId(999L);

        // then
        assertThat(findCode.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("최상위 코드 조회 (parentIsNull = true)")
    void findAllByConditionsParentIsNull() {
        // given
        workCodeRepository.save(WorkCode.createWorkCode("GRP001", "개발팀", CodeType.LABEL, null, 1));
        workCodeRepository.save(WorkCode.createWorkCode("GRP002", "운영팀", CodeType.LABEL, null, 2));

        WorkCode parent = WorkCode.createWorkCode("GRP003", "부모코드", CodeType.LABEL, null, 3);
        workCodeRepository.save(parent);
        workCodeRepository.save(WorkCode.createWorkCode("CHILD001", "자식코드", CodeType.OPTION, parent, 1));
        em.flush();
        em.clear();

        // when
        List<WorkCode> codes = workCodeRepository.findAllByConditions(null, null, true, null);

        // then
        assertThat(codes).hasSize(3);
        assertThat(codes).extracting("code").containsExactly("GRP001", "GRP002", "GRP003");
    }

    @Test
    @DisplayName("부모 코드로 하위 코드 조회")
    void findAllByConditionsWithParentCode() {
        // given
        WorkCode parent = WorkCode.createWorkCode("GRP001", "개발팀", CodeType.LABEL, null, 1);
        workCodeRepository.save(parent);
        workCodeRepository.save(WorkCode.createWorkCode("CHILD001", "백엔드", CodeType.OPTION, parent, 1));
        workCodeRepository.save(WorkCode.createWorkCode("CHILD002", "프론트엔드", CodeType.OPTION, parent, 2));
        em.flush();
        em.clear();

        // when
        List<WorkCode> codes = workCodeRepository.findAllByConditions("GRP001", null, null, null);

        // then
        assertThat(codes).hasSize(2);
        assertThat(codes).extracting("code").containsExactly("CHILD001", "CHILD002");
    }

    @Test
    @DisplayName("부모 Id로 하위 코드 조회")
    void findAllByConditionsWithParentId() {
        // given
        WorkCode parent = WorkCode.createWorkCode("GRP001", "개발팀", CodeType.LABEL, null, 1);
        workCodeRepository.save(parent);
        workCodeRepository.save(WorkCode.createWorkCode("CHILD001", "백엔드", CodeType.OPTION, parent, 1));
        em.flush();
        em.clear();

        // when
        List<WorkCode> codes = workCodeRepository.findAllByConditions(null, parent.getRowId(), null, null);

        // then
        assertThat(codes).hasSize(1);
        assertThat(codes.get(0).getCode()).isEqualTo("CHILD001");
    }

    @Test
    @DisplayName("코드 타입으로 필터링")
    void findAllByConditionsWithType() {
        // given
        workCodeRepository.save(WorkCode.createWorkCode("LABEL001", "라벨", CodeType.LABEL, null, 1));
        workCodeRepository.save(WorkCode.createWorkCode("OPTION001", "옵션", CodeType.OPTION, null, 2));
        em.flush();
        em.clear();

        // when
        List<WorkCode> codes = workCodeRepository.findAllByConditions(null, null, true, CodeType.LABEL);

        // then
        assertThat(codes).hasSize(1);
        assertThat(codes.get(0).getCode()).isEqualTo("LABEL001");
    }

    @Test
    @DisplayName("존재하지 않는 부모 코드로 조회 시 예외 발생")
    void findAllByConditionsWithNonExistentParentCode() {
        // when & then
        assertThatThrownBy(() -> workCodeRepository.findAllByConditions("NONEXISTENT", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("부모 코드를 찾을 수 없습니다")
                .hasMessageNotContaining("NONEXISTENT");
    }

    @Test
    @DisplayName("존재하지 않는 부모 Id로 조회 시 예외 발생")
    void findAllByConditionsWithNonExistentParentId() {
        // when & then
        assertThatThrownBy(() -> workCodeRepository.findAllByConditions(null, 999L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("부모 코드를 찾을 수 없습니다")
                .hasMessageNotContaining("999");
    }

    @Test
    @DisplayName("업무코드 수정")
    void updateWorkCode() {
        // given
        WorkCode workCode = WorkCode.createWorkCode("GRP001", "원래이름", CodeType.LABEL, null, 1);
        workCodeRepository.save(workCode);
        em.flush();
        em.clear();

        // when
        WorkCode foundCode = workCodeRepository.findByCode("GRP001").orElseThrow();
        foundCode.updateWorkCode("GRP002", "수정이름", null, 2);
        em.flush();
        em.clear();

        // then
        Optional<WorkCode> updatedCode = workCodeRepository.findByCode("GRP002");
        assertThat(updatedCode.isPresent()).isTrue();
        assertThat(updatedCode.get().getName()).isEqualTo("수정이름");
        assertThat(updatedCode.get().getOrderSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("업무코드 삭제 (소프트 딜리트)")
    void deleteWorkCode() {
        // given
        WorkCode workCode = WorkCode.createWorkCode("GRP001", "삭제코드", CodeType.LABEL, null, 1);
        workCodeRepository.save(workCode);
        em.flush();
        em.clear();

        // when
        WorkCode foundCode = workCodeRepository.findByCode("GRP001").orElseThrow();
        foundCode.deleteWorkCode();
        em.flush();
        em.clear();

        // then
        Optional<WorkCode> deletedCode = workCodeRepository.findByCode("GRP001");
        assertThat(deletedCode.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("정렬 순서대로 조회")
    void findAllByConditionsOrderedByOrderSeq() {
        // given
        workCodeRepository.save(WorkCode.createWorkCode("GRP003", "세번째", CodeType.LABEL, null, 3));
        workCodeRepository.save(WorkCode.createWorkCode("GRP001", "첫번째", CodeType.LABEL, null, 1));
        workCodeRepository.save(WorkCode.createWorkCode("GRP002", "두번째", CodeType.LABEL, null, 2));
        em.flush();
        em.clear();

        // when
        List<WorkCode> codes = workCodeRepository.findAllByConditions(null, null, true, null);

        // then
        assertThat(codes).hasSize(3);
        assertThat(codes).extracting("code").containsExactly("GRP001", "GRP002", "GRP003");
    }
}
