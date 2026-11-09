package io.groo.ieum.department.domain;

import io.groo.ieum.common.domain.AuditingFieldsWithIp;
import io.groo.ieum.company.domain.Company;
import io.groo.core.type.YNType;
import io.groo.ieum.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // -> protected Order() {}와 동일한 의미 (롬복으로 생성자 막기)
@Table(name = "department")
public class Department extends AuditingFieldsWithIp {
    /**
     * 부서 행 아이디<br>
     * 테이블 관리용 seq
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    /**
     * 부서명<br>
     * 부서의 영문 이름
     */
    @Column(name = "department_name", nullable = false, length = 20)
    private String name;

    /**
     * 부서 국문명<br>
     * 부서의 한글 이름
     */
    @Column(name = "department_name_kr", nullable = false, length = 20)
    private String nameKR;

    /**
     * 상위 부서 행 아이디 (읽기 전용)<br>
     * 부모에 대한 수정은 parent 객체를 통해 진행할 것
     */
    @Column(name = "parent_row_id", insertable = false, updatable = false)
    private Long parentId;

    /**
     * 상위 부서 객체<br>
     * 테이블 컬럼은 parent_row_id<br>
     * 부서의 계층 구조를 표현하기 위한 자기 참조 관계
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_row_id")
    private Department parent;

    /**
     * 부서장 객체<br>
     * 부서를 관리하는 사용자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_user_row_id")
    private User headUser;

    /**
     * 부서 레벨<br>
     * 부서 계층 구조에서의 깊이 (tree 레벨)
     */
    @Column(name = "department_level")
    private Long level;

    /**
     * 부서 설명<br>
     * 부서에 대한 상세 설명
     */
    @Column(name = "department_desc", length = 1000)
    private String desc;

    /**
     * 색상 코드<br>
     * UI에서 부서 표시 시 사용할 색상 코드
     */
    @Column(name = "color_code", length = 10)
    private String color;

    /**
     * 회사 객체<br>
     * 테이블 컬럼은 company_row_id<br>
     * 부서가 속한 회사 정보
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_row_id")
    private Company company;

    /**
     * 하위 부서 목록<br>
     * 해당 부서 하위에 속한 부서 목록
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Department> children = new ArrayList<>();

    /**
     * 소속 유저 목록<br>
     * 해당 부서에 소속된 유저 매핑 목록
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<UserDepartment> userDepartments = new ArrayList<>();

    /**
     * 삭제 여부<br>
     * Soft delete를 위한 플래그
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    // company 추가 연관관계 편의 메소드
    public void addCompany(Company company) {
        this.company = company;
        company.getDepartments().add(this);
    }

    // parent 추가 연관관계 편의 메소드
    public void addParent(Department parent) {
        this.parent = parent;
        if  (parent != null) {
            parent.getChildren().add(this);
        }
    }

    /**
     * 부서 생성 함수<br>
     * Entity의 경우 Setter없이 Getter만 사용<br>
     * 해당 메소드를 통해 유저 생성할 것
     *
     * @return Department
     */
    public static Department createDepartment(String name, String nameKR, Department parent, User headUser, Long level, String desc, String color, Company company) {
        Department department = new Department();
        department.name = name;
        department.nameKR = nameKR;
        department.addParent(parent);
        department.headUser = headUser;
        department.level = level;
        department.desc = desc;
        department.color = color;
        department.addCompany(company);
        department.isDeleted = YNType.N;
        return department;
    }

    /**
     * 부서 수정 함수<br>
     * Entity의 경우 Setter없이 Getter만 사용<br>
     * 해당 메소드를 통해 유저 생성할 것
     */
    public void updateDepartment(String name, String nameKR, Department parent, User headUser, Long level, String desc, String color) {
        if (!Objects.isNull(name)) { this.name = name; }
        if (!Objects.isNull(nameKR)) { this.nameKR = nameKR; }
        if (!Objects.isNull(parent)) { this.changeParent(parent); }
        // 부서장의 경우 잘못 선택한 경우 null로 설정가능
        this.headUser = headUser;
        if (!Objects.isNull(level)) { this.level = level; }
        if (!Objects.isNull(desc)) { this.desc = desc; }
        // 색상 코드의 경우 잘못 선택한 경우 공란으로 설정가능
        this.color = color;
    }

    /**
     * 부모 부서 변경
     */
    public void changeParent(Department newParent) {
        // 기존 부모에서 제거
        if (this.parent != null) {
            this.parent.getChildren().remove(this);
        }

        // 새로운 부모
        this.addParent(newParent);
    }

    /**
     * 부서 레벨 변경<br>
     * 상위 부서가 바뀔 때 서비스가 새 상위 레벨 + 1 로 다시 센 값을 넣는다.
     */
    public void changeLevel(Long level) {
        this.level = level;
    }

    /**
     * 부서 삭제 함수<br>
     * Entity의 경우 Setter없이 Getter만 사용<br>
     * 해당 메소드를 통해 부서 삭제할 것
     */
    public void deleteDepartment() {
        this.isDeleted = YNType.Y;
    }
}
