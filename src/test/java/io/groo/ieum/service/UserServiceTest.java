package io.groo.ieum.service;

import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.type.CountryCode;
import io.groo.core.type.YNType;
import io.groo.core.util.FileUtils;
import io.groo.ieum.department.repository.DepartmentRepository;
import io.groo.ieum.security.service.TokenRevocationService;
import io.groo.ieum.user.domain.User;
import io.groo.ieum.user.repository.UserRepository;
import io.groo.ieum.user.service.EmailService;
import io.groo.ieum.user.service.UserService;
import io.groo.ieum.user.service.UserServiceImpl;
import io.groo.ieum.user.service.dto.UserServiceDto;
import io.groo.ieum.user.type.StatusType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import io.groo.core.util.MessageResolver;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import io.groo.ieum.department.domain.Department;
import io.groo.ieum.department.domain.UserDepartment;
import io.groo.ieum.company.domain.Company;
import io.groo.ieum.permission.domain.Role;
import io.groo.ieum.permission.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mockStatic;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("유저 서비스 테스트")
class UserServiceTest {
    @Mock
    private MessageResolver messageResolver;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private EntityManager em;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private io.groo.ieum.client.sso.SsoApiClient ssoApiClient;
    @Mock
    private TokenRevocationService tokenRevocationService;

    @InjectMocks
    private UserServiceImpl userService;

    // 테스트용 User 생성 헬퍼 메소드
    private User createTestUser(String id, String name, String email) {
        return User.createUser(
                null, id, name, email,
                LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                LocalDate.now(), YNType.N, null, null, CountryCode.KR
        );
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "fileRootPath", "/var/www/media");
        ReflectionTestUtils.setField(userService, "webUrlPrefix", "/media");
        ReflectionTestUtils.setField(userService, "tempPath", "/var/www/media/temp/profile");
        ReflectionTestUtils.setField(userService, "originPath", "/var/www/media/origin/profile");
    }

    @Nested
    @DisplayName("초대 정보 수정")
    class EditInvitation {
        private User invitedUser() {
            User user = createTestUser("invited1", "초대받은이", "invited@test.com");
            ReflectionTestUtils.setField(user, "ssoUserRowId", 99L);
            return user;
        }

        private io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse status(String value) {
            io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse resp =
                    new io.groo.ieum.client.sso.dto.SsoInvitationStatusResponse();
            ReflectionTestUtils.setField(resp, "invitationStatus", value);
            return resp;
        }

        @Test
        @DisplayName("성공 - 대기 중인 초대는 수정된다")
        void editInvitationSuccessPending() {
            User user = invitedUser();
            given(userRepository.findById("invited1")).willReturn(Optional.of(user));
            given(ssoApiClient.getInvitationStatus(List.of(99L))).willReturn(List.of(status("PENDING")));

            UserServiceDto result = userService.editInvitation("invited1",
                    UserServiceDto.builder().name("바뀐이름").email("new@test.com").build());

            assertThat(result.getName()).isEqualTo("바뀐이름");
        }

        @Test
        @DisplayName("실패 - 이미 가입을 마친 사용자의 초대 수정은 거절한다")
        void editInvitationFailNotPending() {
            User user = invitedUser();
            given(userRepository.findById("invited1")).willReturn(Optional.of(user));
            given(ssoApiClient.getInvitationStatus(List.of(99L))).willReturn(List.of(status("ACTIVE")));

            assertThatThrownBy(() -> userService.editInvitation("invited1",
                    UserServiceDto.builder().name("바뀐이름").email("new@test.com").build()))
                    .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
            assertThat(user.getName()).isEqualTo("초대받은이");
        }

        @Test
        @DisplayName("실패 - 초대 수정으로 소속을 내부 표식(SYSTEM)으로 바꾸지 못한다")
        void editInvitationFailSystemCompany() {
            User user = invitedUser();
            given(userRepository.findById("invited1")).willReturn(Optional.of(user));
            given(ssoApiClient.getInvitationStatus(List.of(99L))).willReturn(List.of(status("PENDING")));

            assertThatThrownBy(() -> userService.editInvitation("invited1",
                    UserServiceDto.builder().company("SYSTEM").build()))
                    .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
            assertThat(user.getCompany()).isEqualTo("NONE");
        }
    }

    @Nested
    @DisplayName("보안 점검 IEU-3 - 소속 내부 표식(SYSTEM) 설정 차단")
    class SystemCompanyGuard {
        @Test
        @DisplayName("실패 - 가입 경로로 SYSTEM 소속 사용자를 만들지 못한다")
        void joinUserFailSystemCompany() {
            UserServiceDto data = UserServiceDto.builder()
                    .ssoUserRowId(1L).id("user1").name("테스트유저").email("test@test.com")
                    .company("SYSTEM").build();

            assertThatThrownBy(() -> userService.joinUser(data))
                    .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("실패 - 초대 경로로 SYSTEM 소속을 넣으면 SSO 에 초대를 보내기 전에 거절한다")
        void inviteUserFailSystemCompany() {
            UserServiceDto data = UserServiceDto.builder()
                    .id("user1").name("테스트유저").email("test@test.com").company("system ").build();

            assertThatThrownBy(() -> userService.inviteUser(data))
                    .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
            then(ssoApiClient).shouldHaveNoInteractions();
            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("판별 - 대소문자 · 앞뒤 공백이 달라도 같은 내부 표식으로 본다(DB 콜레이션과 같은 기준)")
        void isSystemCompanyMatchesCollation() {
            assertThat(User.isSystemCompany("SYSTEM")).isTrue();
            assertThat(User.isSystemCompany("system")).isTrue();
            assertThat(User.isSystemCompany(" System ")).isTrue();
            assertThat(User.isSystemCompany("NONE")).isFalse();
            assertThat(User.isSystemCompany("SYSTEMS")).isFalse();
            assertThat(User.isSystemCompany(null)).isFalse();
        }

        @Test
        @DisplayName("판별 - 보안 점검 IEU-3 잔여: unicode_ci 가 같다고 보는 악센트 · 전각 · 폭 없는 글자 변형도 SYSTEM 으로 본다")
        void isSystemCompanyMatchesUnicodeVariants() {
            assertThat(User.isSystemCompany("SYSTÉM")).isTrue();                // 악센트(합성형 É)
            assertThat(User.isSystemCompany("SYSTE\u0301M")).isTrue();          // 악센트(결합 문자)
            assertThat(User.isSystemCompany("ＳＹＳＴＥＭ")).isTrue();            // 전각
            assertThat(User.isSystemCompany("S\u200BYSTEM")).isTrue();          // 폭 없는 공백
            assertThat(User.isSystemCompany("system\u3000")).isTrue();          // 전각 공백
            assertThat(User.isSystemCompany("\u017Fystem")).isTrue();           // ſ(긴 s) → s
            assertThat(User.isSystemCompany("ｓｙｓｔｅｍ")).isTrue();            // 전각 소문자
            // unicode_ci 에서도 다른 글자 — 막지 않는다(키릴 Ѕ 는 라틴 S 와 다른 글자다)
            assertThat(User.isSystemCompany("\u0405YSTEM")).isFalse();
            assertThat(User.isSystemCompany("SYSTEMS")).isFalse();
            assertThat(User.isSystemCompany("NONE")).isFalse();
        }
    }

    @Nested
    @DisplayName("보안 점검 IEU-1 - 이음 사용자만 대상으로 삼는다")
    class IeumUserOnly {
        private io.groo.ieum.client.sso.dto.SsoInviteResponse existingSsoAccount() {
            io.groo.ieum.client.sso.dto.SsoInviteResponse resp = new io.groo.ieum.client.sso.dto.SsoInviteResponse();
            ReflectionTestUtils.setField(resp, "alreadyExists", true);
            ReflectionTestUtils.setField(resp, "userNo", 500L);
            ReflectionTestUtils.setField(resp, "userId", "damda_only_kim");
            ReflectionTestUtils.setField(resp, "name", "김담다");
            ReflectionTestUtils.setField(resp, "email", "kim@other.com");
            return resp;
        }

        @Test
        @DisplayName("실패 - 이음에 없는 계정(담다 전용 · SSO 관리자)의 비밀번호는 초기화하지 않고 SSO 도 부르지 않는다")
        void resetPasswordRejectsNonIeumUser() {
            given(userRepository.findById("sso_admin")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.resetPassword("sso_admin"))
                    .isInstanceOf(EntityNotFoundException.class);
            then(ssoApiClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실패 - 삭제(퇴사)된 이음 사용자의 비밀번호도 초기화하지 않는다")
        void resetPasswordRejectsDeletedUser() {
            User user = createTestUser("left1", "퇴사자", "left1@test.com");
            ReflectionTestUtils.setField(user, "ssoUserRowId", 3L);
            user.deleteUser();
            given(userRepository.findById("left1")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.resetPassword("left1"))
                    .isInstanceOf(EntityNotFoundException.class);
            then(ssoApiClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실패 - 내부 계정(SYSTEM 소속)의 비밀번호는 초기화하지 않는다")
        void resetPasswordRejectsSystemAccount() {
            User user = User.createUser(9L, "system", "시스템", "system@test.com",
                    LocalDate.of(1990, 1, 1), "SYSTEM", "9 ~ 18", LocalDate.now(), YNType.N, null, null, CountryCode.KR);
            given(userRepository.findById("system")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.resetPassword("system"))
                    .isInstanceOf(EntityNotFoundException.class);
            then(ssoApiClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("성공 - SSO 와 연결된 이음 사용자면 SSO 에 초기화를 맡긴다")
        void resetPasswordForIeumUser() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            ReflectionTestUtils.setField(user, "ssoUserRowId", 7L);
            given(userRepository.findById("user1")).willReturn(Optional.of(user));

            userService.resetPassword("user1");

            then(ssoApiClient).should().resetPassword("user1");
        }

        @Test
        @DisplayName("성공 - 남의 이메일로 초대해 기존 SSO 계정과 연결되면 응답에 그 계정의 로그인 아이디 · 이름을 싣지 않는다")
        void inviteExistingSsoAccountHidesLoginId() {
            given(ssoApiClient.inviteUser(any())).willReturn(existingSsoAccount());
            given(userRepository.findBySsoUserRowId(500L)).willReturn(Optional.empty());

            UserServiceDto.InviteResult result = userService.inviteUser(UserServiceDto.builder()
                    .id("new_kim").name("김신입").email("kim@other.com").company("NONE").build());

            assertThat(result.isAlreadyExists()).isTrue();
            assertThat(result.getUserId()).isNull();
            assertThat(result.getName()).isNull();
            assertThat(result.getEmail()).isEqualTo("kim@other.com");
        }
    }

    @Nested
    @DisplayName("보안 점검 IEU-8 - 최상위 부서장 판별")
    class IsTopDepartmentHead {
        private UserDepartment mainDept(String headId, Long parentId) {
            Department dept = mock(Department.class);
            User head = headId == null ? null : createTestUser(headId, "부서장", headId + "@test.com");
            lenient().when(dept.getHeadUser()).thenReturn(head);
            lenient().when(dept.getParentId()).thenReturn(parentId);
            UserDepartment ud = mock(UserDepartment.class);
            given(ud.getDepartment()).willReturn(dept);
            return ud;
        }

        @Test
        @DisplayName("최상위 부서의 부서장 본인이면 true")
        void topHead() {
            UserDepartment top = mainDept("ceo", null);
            given(departmentRepository.findMainDepartmentByUserId("ceo")).willReturn(Optional.of(top));
            assertThat(userService.isTopDepartmentHead("ceo")).isTrue();
        }

        @Test
        @DisplayName("최상위 부서의 일반 구성원 · 하위 부서의 부서장 · 부서 없음 · 부서장 빈 부서는 false")
        void notTopHead() {
            UserDepartment top = mainDept("ceo", null);
            UserDepartment team = mainDept("teamlead", 1L);
            UserDepartment headless = mainDept(null, null);
            given(departmentRepository.findMainDepartmentByUserId("member")).willReturn(Optional.of(top));
            given(departmentRepository.findMainDepartmentByUserId("teamlead")).willReturn(Optional.of(team));
            given(departmentRepository.findMainDepartmentByUserId("nodept")).willReturn(Optional.empty());
            given(departmentRepository.findMainDepartmentByUserId("headless")).willReturn(Optional.of(headless));

            assertThat(userService.isTopDepartmentHead("member")).isFalse();
            assertThat(userService.isTopDepartmentHead("teamlead")).isFalse();
            assertThat(userService.isTopDepartmentHead("nodept")).isFalse();
            assertThat(userService.isTopDepartmentHead("headless")).isFalse();
        }
    }

    @Nested
    @DisplayName("회원 가입")
    class JoinUser {
        @Test
        @DisplayName("성공 - 유저가 정상적으로 저장된다")
        void joinUserSuccess() {
            // given
            UserServiceDto data = UserServiceDto.builder()
                    .ssoUserRowId(1L)
                    .id("user1")
                    .name("테스트유저")
                    .email("test@test.com")
                    .build();
            willDoNothing().given(userRepository).save(any(User.class));

            // when
            String result = userService.joinUser(data);

            // then
            then(userRepository).should().save(any(User.class));
            assertThat(result).isEqualTo("user1");
        }

        @Test
        @DisplayName("성공 - 프로필 이미지와 함께 저장된다")
        void joinUserWithProfileSuccess() {
            // given
            UserServiceDto data = UserServiceDto.builder()
                    .ssoUserRowId(1L)
                    .id("user1")
                    .name("테스트유저")
                    .profileUrl("/media/temp/profile/test.jpg_some-uuid")
                    .profileUUID("some-uuid")
                    .build();
            willDoNothing().given(userRepository).save(any(User.class));

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                mocked.when(() -> FileUtils.copy(anyString(), anyString(), any(MessageResolver.class)))
                        .thenReturn(true);
                mocked.when(() -> FileUtils.extractOriginalFilename(anyString(), isNull()))
                        .thenReturn("test.jpg");

                // when
                String result = userService.joinUser(data);

                // then
                then(userRepository).should().save(any(User.class));
                assertThat(result).isEqualTo("user1");
            }
        }

        @Test
        @DisplayName("성공 - profileUUID만 있으면 프로필 복사 없이 저장된다")
        void joinUserWithOnlyProfileUUID() {
            // given
            UserServiceDto data = UserServiceDto.builder()
                    .ssoUserRowId(1L)
                    .id("user1")
                    .name("테스트유저")
                    .profileUUID("some-uuid")
                    .build();
            willDoNothing().given(userRepository).save(any(User.class));

            // when
            String result = userService.joinUser(data);

            // then
            then(userRepository).should().save(any(User.class));
            assertThat(result).isEqualTo("user1");
        }

        @Test
        @DisplayName("성공 - profileUrl만 있으면 프로필 복사 없이 저장된다")
        void joinUserWithOnlyProfileUrl() {
            // given
            UserServiceDto data = UserServiceDto.builder()
                    .ssoUserRowId(1L)
                    .id("user1")
                    .name("테스트유저")
                    .profileUrl("/media/temp/profile/test.jpg")
                    .build();
            willDoNothing().given(userRepository).save(any(User.class));

            // when
            String result = userService.joinUser(data);

            // then
            then(userRepository).should().save(any(User.class));
            assertThat(result).isEqualTo("user1");
        }
    }

    @Nested
    @DisplayName("유저 조회")
    class SearchUser {
        @Test
        @DisplayName("성공 - 존재하는 유저를 반환한다")
        void searchUserSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "test@test.com");
            given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.of(user));

            // when
            UserServiceDto result = userService.searchUser(userId);

            // then
            then(userRepository).should().findByIdWithRolesAndPermissions(userId);
            assertThat(result.getName()).isEqualTo("이서준");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저면 예외가 발생한다")
        void searchUserFailNotFound() {
            // given
            String userId = "nonexistent";
            given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.searchUser(userId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 삭제된 유저면 예외가 발생한다")
        void searchUserFailDeleted() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "test@test.com");
            user.deleteUser();
            given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> userService.searchUser(userId))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("성공 - 프로필이 있는 유저를 반환한다")
        void searchUserWithProfileSuccess() {
            // given
            String userId = "user1";
            User user = User.createUser(
                    null, userId, "이서준", "test@test.com",
                    LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                    LocalDate.now(), YNType.N, "test.jpg", "some-uuid", CountryCode.KR
            );
            given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.of(user));

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                mocked.when(() -> FileUtils.generatePhysicalFilename("test.jpg", "some-uuid"))
                        .thenReturn("test.jpg_some-uuid");

                // when
                UserServiceDto result = userService.searchUser(userId);

                // then
                assertThat(result.getName()).isEqualTo("이서준");
                assertThat(result.getProfileName()).isEqualTo("test.jpg");
                assertThat(result.getProfileUrl()).contains("test.jpg_some-uuid");
            }
        }

        @Test
        @DisplayName("성공 - profileName만 있으면 profileUrl은 null이다")
        void searchUserWithOnlyProfileName() {
            // given
            String userId = "user1";
            User user = User.createUser(
                    null, userId, "이서준", "test@test.com",
                    LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                    LocalDate.now(), YNType.N, "test.jpg", null, CountryCode.KR
            );
            given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.of(user));

            // when
            UserServiceDto result = userService.searchUser(userId);

            // then
            assertThat(result.getProfileName()).isEqualTo("test.jpg");
            assertThat(result.getProfileUrl()).isNull();
        }

        @Test
        @DisplayName("성공 - 메인 부서가 있는 유저를 반환한다")
        void searchUserWithMainDepartment() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "test@test.com");

            Company company = Company.createCompany("회사", "Company", "desc");
            Department dept = Department.createDepartment("부서", "개발팀", null, null, 1L, "desc", "#000", company);
            UserDepartment userDept = UserDepartment.createUserDepartment(user, dept, YNType.Y);

            given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.of(user));

            // when
            UserServiceDto result = userService.searchUser(userId);

            // then
            assertThat(result.getName()).isEqualTo("이서준");
            assertThat(result.getMainDepartmentNameKR()).isEqualTo("개발팀");
        }

        @Test
        @DisplayName("성공 - 삭제된 메인 부서는 무시한다")
        void searchUserWithDeletedMainDepartment() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "test@test.com");

            Company company = Company.createCompany("회사", "Company", "desc");
            Department dept = Department.createDepartment("부서", "개발팀", null, null, 1L, "desc", "#000", company);
            UserDepartment userDept = UserDepartment.createUserDepartment(user, dept, YNType.Y);
            userDept.deleteUserDepartment();

            given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.of(user));

            // when
            UserServiceDto result = userService.searchUser(userId);

            // then
            assertThat(result.getName()).isEqualTo("이서준");
            assertThat(result.getMainDepartmentNameKR()).isNull();
        }

        @Test
        @DisplayName("성공 - 메인이 아닌 부서는 무시한다")
        void searchUserWithNonMainDepartment() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "test@test.com");

            Company company = Company.createCompany("회사", "Company", "desc");
            Department dept = Department.createDepartment("부서", "개발팀", null, null, 1L, "desc", "#000", company);
            UserDepartment userDept = UserDepartment.createUserDepartment(user, dept, YNType.N);

            given(userRepository.findByIdWithRolesAndPermissions(userId)).willReturn(Optional.of(user));

            // when
            UserServiceDto result = userService.searchUser(userId);

            // then
            assertThat(result.getName()).isEqualTo("이서준");
            assertThat(result.getMainDepartmentNameKR()).isNull();
        }
    }

    @Nested
    @DisplayName("전체 유저 조회")
    class SearchUsers {
        @Test
        @DisplayName("성공 - 유저 목록을 반환한다")
        void searchUsersSuccess() {
            // given
            given(userRepository.findUsersWithRolesAndPermissions()).willReturn(List.of(
                    createTestUser("user1", "이서준", "user1@test.com"),
                    createTestUser("user2", "김서연", "user2@test.com")
            ));

            // when
            List<UserServiceDto> result = userService.searchUsers();

            // then
            then(userRepository).should().findUsersWithRolesAndPermissions();
            assertThat(result).hasSize(2);
            assertThat(result).extracting("name").containsExactly("이서준", "김서연");
        }

        @Test
        @DisplayName("성공 - 유저가 없으면 빈 리스트를 반환한다")
        void searchUsersEmptyList() {
            // given
            given(userRepository.findUsersWithRolesAndPermissions()).willReturn(List.of());

            // when
            List<UserServiceDto> result = userService.searchUsers();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("전체 유저 엔티티 조회")
    class FindAllUsers {
        @Test
        @DisplayName("성공 - 유저 엔티티 목록을 반환한다")
        void findAllUsersSuccess() {
            // given
            List<User> users = List.of(
                    createTestUser("user1", "이서준", "user1@test.com"),
                    createTestUser("user2", "김서연", "user2@test.com")
            );
            given(userRepository.findUsersWithRolesAndPermissions()).willReturn(users);

            // when
            List<User> result = userService.findAllUsers();

            // then
            then(userRepository).should().findUsersWithRolesAndPermissions();
            assertThat(result).hasSize(2);
            assertThat(result).extracting("name").containsExactly("이서준", "김서연");
        }

        @Test
        @DisplayName("성공 - 유저가 없으면 빈 리스트를 반환한다")
        void findAllUsersEmptyList() {
            // given
            given(userRepository.findUsersWithRolesAndPermissions()).willReturn(List.of());

            // when
            List<User> result = userService.findAllUsers();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("유저 수정")
    class EditUser {
        @Test
        @DisplayName("성공 - 유저 정보가 수정된다")
        void editUserSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserServiceDto data = UserServiceDto.builder()
                    .id(userId)
                    .name("이하은")
                    .workTime("10 ~ 19")
                    .build();

            // when
            userService.editUser(data, "user1", false, false);

            // then
            then(userRepository).should().findById(userId);
            assertThat(user.getName()).isEqualTo("이하은");
            assertThat(user.getWorkTime()).isEqualTo("10 ~ 19");
        }

        @Test
        @DisplayName("성공 - 사진 지우기(Y)가 오면 사진 이름 · UUID 가 지워지고 다른 칸은 그대로 반영된다")
        void editUserRemoveProfile() {
            User user = User.createUser(null, "user1", "이서준", "user1@test.com",
                    LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                    LocalDate.now(), YNType.N, "me.png", "uuid-1", CountryCode.KR);
            given(userRepository.findById("user1")).willReturn(Optional.of(user));

            userService.editUser(UserServiceDto.builder().id("user1").name("이하은")
                    .profileDeleteYN(YNType.Y).build(), "user1", false, false);

            assertThat(user.getProfileName()).isNull();
            assertThat(user.getProfileUUID()).isNull();
            assertThat(user.getName()).isEqualTo("이하은");
        }

        @Test
        @DisplayName("성공 - 사진 지우기와 새 사진이 같이 오면 지우기가 이긴다(임시 사진을 옮기지 않는다)")
        void editUserRemoveProfileWinsOverNewPhoto() {
            User user = User.createUser(null, "user1", "이서준", "user1@test.com",
                    LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                    LocalDate.now(), YNType.N, "me.png", "uuid-1", CountryCode.KR);
            given(userRepository.findById("user1")).willReturn(Optional.of(user));

            userService.editUser(UserServiceDto.builder().id("user1")
                    .profileUUID("uuid-2").profileUrl("/upload/temp/profile/x.png")
                    .profileDeleteYN(YNType.Y).build(), "user1", false, false);

            assertThat(user.getProfileName()).isNull();
            assertThat(user.getProfileUUID()).isNull();
        }

        @Test
        @DisplayName("성공 - 사진 지우기가 없거나 N 이면 지금 사진을 그대로 둔다")
        void editUserKeepsProfileWithoutDeleteFlag() {
            User user = User.createUser(null, "user1", "이서준", "user1@test.com",
                    LocalDate.of(1990, 1, 1), "NONE", "9 ~ 18",
                    LocalDate.now(), YNType.N, "me.png", "uuid-1", CountryCode.KR);
            given(userRepository.findById("user1")).willReturn(Optional.of(user));

            userService.editUser(UserServiceDto.builder().id("user1").name("이하은").build(), "user1", false, false);
            userService.editUser(UserServiceDto.builder().id("user1").profileDeleteYN(YNType.N).build(), "user1", false, false);

            assertThat(user.getProfileName()).isEqualTo("me.png");
            assertThat(user.getProfileUUID()).isEqualTo("uuid-1");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저를 수정하려 하면 예외가 발생한다")
        void editUserFailNotFound() {
            // given
            String userId = "nonexistent";
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            UserServiceDto data = UserServiceDto.builder().id(userId).build();

            // when & then
            assertThatThrownBy(() -> userService.editUser(data, "user1", false, false))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        private void assertEditDenied(UserServiceDto data, String loginUserId, boolean canManage) {
            assertEditDenied(data, loginUserId, canManage, canManage);
        }

        private void assertEditDenied(UserServiceDto data, String loginUserId, boolean canManage, boolean canManageRoles) {
            assertThatThrownBy(() -> userService.editUser(data, loginUserId, canManage, canManageRoles))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((ForbiddenException) e).getErrorCode().getCode())
                    .isEqualTo("PERMISSION_001");
        }

        @Test
        @DisplayName("실패 - 남의 정보를 관리 권한 없이 고치면 403 이고 바뀌지 않는다")
        void editUserFailOtherWithoutManage() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            assertEditDenied(UserServiceDto.builder().id("user1").name("바꿈").build(), "user2", false);
            assertThat(user.getName()).isEqualTo("이서준");
        }

        @Test
        @DisplayName("실패 - 본인이라도 역할을 바꾸는 요청은 관리 권한 없이는 403 (ADMIN 승격 차단)")
        void editUserFailSelfRoleEscalation() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            assertEditDenied(UserServiceDto.builder().id("user1").name("바꿈").roleNames(List.of("ADMIN")).build(), "user1", false);
            assertThat(user.getName()).isEqualTo("이서준");
            assertThat(user.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("실패 - 본인이라도 소속을 내부 표식(SYSTEM)으로 바꾸면 403 이고 바뀌지 않는다 (IEU-3)")
        void editUserFailSelfCompanyToSystem() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            assertEditDenied(UserServiceDto.builder().id("user1").company("SYSTEM").build(), "user1", false);
            assertThat(user.getCompany()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("실패 - 본인이 다른 소속으로 바꾸는 것도 관리 권한 없이는 403")
        void editUserFailSelfCompanyChange() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            assertEditDenied(UserServiceDto.builder().id("user1").name("이하은").company("OTHER").build(), "user1", false);
            assertThat(user.getCompany()).isEqualTo("NONE");
            assertThat(user.getName()).isEqualTo("이서준");
        }

        @Test
        @DisplayName("성공 - 본인이 같은 소속 값을 다시 보내는 프로필 저장은 그대로 된다")
        void editUserSuccessSelfSameCompany() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            userService.editUser(UserServiceDto.builder().id("user1").name("이하은").company("NONE").build(), "user1", false, false);
            assertThat(user.getName()).isEqualTo("이하은");
            assertThat(user.getCompany()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("실패 - 관리 권한이 있어도 소속을 내부 표식(SYSTEM)으로는 바꾸지 못한다")
        void editUserFailManagerCompanyToSystem() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            assertThatThrownBy(() -> userService.editUser(
                    UserServiceDto.builder().id("user1").company("System").build(), "admin", true, true))
                    .isInstanceOf(io.groo.core.exception.InvalidValueException.class);
            assertThat(user.getCompany()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("성공 - 관리 권한이 있으면 다른 소속으로 바꾼다")
        void editUserSuccessManagerCompanyChange() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            userService.editUser(UserServiceDto.builder().id("user1").company("OTHER").build(), "admin", true, true);
            assertThat(user.getCompany()).isEqualTo("OTHER");
        }

        @Test
        @DisplayName("성공 - 이미 SYSTEM 인 내부 계정은 같은 값으로 저장해도 거절하지 않는다")
        void editUserSuccessSystemAccountKeepsCompany() {
            User user = User.createUser(null, "system", "시스템", "system@test.com",
                    LocalDate.of(1990, 1, 1), "SYSTEM", "9 ~ 18",
                    LocalDate.now(), YNType.N, null, null, CountryCode.KR);
            given(userRepository.findById("system")).willReturn(Optional.of(user));
            userService.editUser(UserServiceDto.builder().id("system").name("시스템2").company("SYSTEM").build(), "system", false, false);
            assertThat(user.getName()).isEqualTo("시스템2");
        }

        @Test
        @DisplayName("성공 - 본인이 역할 변화 없이(보내지 않거나 같은 값) 프로필을 고치면 관리 권한 없이도 된다")
        void editUserSuccessSelfSameRoles() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            userService.editUser(UserServiceDto.builder().id("user1").name("이하은").roleNames(List.of()).build(), "user1", false, false);
            assertThat(user.getName()).isEqualTo("이하은");
        }

        @Test
        @DisplayName("실패 - 사용자 관리 권한만으로는 자기에게 관리자 역할을 붙이지 못한다 (IEU-7)")
        void editUserFailUserManagerSelfAdminRole() {
            User user = createTestUser("hr1", "인사담당", "hr1@test.com");
            given(userRepository.findById("hr1")).willReturn(Optional.of(user));
            assertEditDenied(UserServiceDto.builder().id("hr1").roleNames(List.of("ADMIN")).build(), "hr1", true, false);
            assertThat(user.getRoles()).isEmpty();
            then(roleRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실패 - 사용자 관리 권한만으로는 남의 역할도 바꾸지 못한다 (IEU-7)")
        void editUserFailUserManagerOtherRole() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            assertEditDenied(UserServiceDto.builder().id("user1").name("바꿈").roleNames(List.of("ADMIN")).build(), "hr1", true, false);
            assertThat(user.getName()).isEqualTo("이서준");
            assertThat(user.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("성공 - 사용자 관리 권한만 있어도 역할을 건드리지 않으면(안 보내거나 같은 목록) 남의 정보를 고친다")
        void editUserSuccessUserManagerWithoutRoleChange() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            userService.editUser(UserServiceDto.builder().id("user1").name("이하은").build(), "hr1", true, false);
            userService.editUser(UserServiceDto.builder().id("user1").workTime("10 ~ 19").roleNames(List.of()).build(), "hr1", true, false);
            assertThat(user.getName()).isEqualTo("이하은");
            assertThat(user.getWorkTime()).isEqualTo("10 ~ 19");
        }

        @Test
        @DisplayName("보안 점검 IEU-2 - 역할이 실제로 바뀌면 그 사용자의 토큰을 전부 끊는다")
        void editUserRolesChangedRevokesTokens() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(role));

            userService.editUser(UserServiceDto.builder().id("user1").roleNames(List.of("ADMIN")).build(), "admin", true, true);

            then(tokenRevocationService).should().revokeAllForUser("user1");
        }

        @Test
        @DisplayName("보안 점검 IEU-2 - 역할을 안 보내거나 같은 목록이면 토큰을 끊지 않는다")
        void editUserRolesUnchangedKeepsTokens() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));

            userService.editUser(UserServiceDto.builder().id("user1").name("이하은").build(), "admin", true, true);
            userService.editUser(UserServiceDto.builder().id("user1").roleNames(List.of()).build(), "admin", true, true);

            then(tokenRevocationService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("성공 - 관리 권한이 있으면 남의 정보와 역할도 고친다")
        void editUserSuccessOtherByManager() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(role));
            userService.editUser(UserServiceDto.builder().id("user1").name("이하은").roleNames(List.of("ADMIN")).build(), "admin", true, true);
            assertThat(user.getName()).isEqualTo("이하은");
            assertThat(user.getRoles()).contains(role);
        }

        @Test
        @DisplayName("성공 - 대시보드 데이터가 수정된다")
        void editUserDashboardSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserServiceDto data = UserServiceDto.builder()
                    .id(userId)
                    .dashboard("{\"layout\": []}")
                    .build();

            // when
            userService.editUser(data, "user1", false, false);

            // then
            then(userRepository).should().findById(userId);
            assertThat(user.getDashboard()).isEqualTo("{\"layout\": []}");
        }

        @Test
        @DisplayName("성공 - 유저 권한이 수정된다")
        void editUserRolesSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(role));

            UserServiceDto data = UserServiceDto.builder()
                    .id(userId)
                    .roleNames(List.of("ADMIN"))
                    .build();

            // when
            userService.editUser(data, "admin", true, true);

            // then
            then(userRepository).should().findById(userId);
            then(roleRepository).should().findByCode("ADMIN");
            assertThat(user.getRoles()).contains(role);
        }

        @Test
        @DisplayName("실패 - 없는 역할 코드는 코드 값을 담지 않은 역할 없음 오류로 거절한다")
        void editUserUnknownRoleDoesNotEchoCode() {
            User user = createTestUser("user1", "이서준", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));
            given(roleRepository.findByCode("SECRET_ROLE_X")).willReturn(Optional.empty());

            UserServiceDto data = UserServiceDto.builder().id("user1").roleNames(List.of("SECRET_ROLE_X")).build();

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> userService.editUser(data, "admin", true, true))
                    .isInstanceOf(io.groo.core.exception.EntityNotFoundException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET_ROLE_X"));
        }
    }

    @Nested
    @DisplayName("유저 삭제")
    class DeleteUser {
        @Test
        @DisplayName("성공 - 유저가 삭제된다")
        void deleteUserSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "이서준", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            userService.deleteUser(userId);

            // then — 보안 점검 IEU-2: 삭제된 사용자의 토큰을 전부 끊는다
            then(tokenRevocationService).should().revokeAllForUser(userId);

            // then
            then(userRepository).should().findById(userId);
            assertThat(user.getIsDeleted()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저를 삭제하려 하면 예외가 발생한다")
        void deleteUserFailNotFound() {
            // given
            String userId = "nonexistent";
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.deleteUser(userId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("프로필 이미지 임시 저장")
    class SaveProfileImgInTempFolder {
        private static final byte[] JPEG_BYTES =
                {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};

        @Test
        @DisplayName("실패 - 확장자가 jpg 여도 내용이 이미지가 아니면 저장하지 않는다")
        void saveProfileImgRejectsNonImageContent() {
            MockMultipartFile fake = new MockMultipartFile("file", "test.jpg", "image/jpeg", "<html>".getBytes());

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                mocked.when(() -> FileUtils.getExtension(anyString())).thenReturn("jpg");

                org.junit.jupiter.api.Assertions.assertThrows(
                        io.groo.core.exception.InvalidValueException.class,
                        () -> userService.saveProfileImgInTempFolder(fake));
                mocked.verify(() -> FileUtils.save(any(), any(), any(), any()), never());
            }
        }

        @Test
        @DisplayName("성공 - 프로필 이미지가 임시 폴더에 저장된다")
        void saveProfileImgSuccess() {
            // given
            MockMultipartFile mockFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", JPEG_BYTES);
            String physicalFilename = "test.jpg_some-uuid";

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                // FileUploadValidator 가 내부에서 확장자를 뽑아 쓴다 — static mock 이 걸린 상태라
                // stub 하지 않으면 null 이 흘러 NPE 가 난다.
                mocked.when(() -> FileUtils.getExtension(anyString())).thenReturn("jpg");
                mocked.when(() -> FileUtils.generatePhysicalFilename(eq("test.jpg"), anyString()))
                        .thenReturn(physicalFilename);
                mocked.when(() -> FileUtils.save(eq(mockFile), anyString(), eq(physicalFilename), eq(messageResolver)))
                        .thenReturn(true);

                // when
                UserServiceDto result = userService.saveProfileImgInTempFolder(mockFile);

                // then
                assertThat(result.getProfileUrl()).contains(physicalFilename);
                assertThat(result.getProfileUUID()).isNotNull();
            }
        }

        @Test
        @DisplayName("실패 - 저장이 거절되면(false) 200 대신 오류를 던진다")
        void saveProfileImgRejected() {
            MockMultipartFile mockFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", JPEG_BYTES);
            String physicalFilename = "test.jpg_some-uuid";

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                mocked.when(() -> FileUtils.getExtension(anyString())).thenReturn("jpg");
                mocked.when(() -> FileUtils.generatePhysicalFilename(eq("test.jpg"), anyString()))
                        .thenReturn(physicalFilename);
                mocked.when(() -> FileUtils.save(eq(mockFile), anyString(), eq(physicalFilename), eq(messageResolver)))
                        .thenReturn(false);

                org.junit.jupiter.api.Assertions.assertThrows(
                        io.groo.core.exception.InvalidValueException.class,
                        () -> userService.saveProfileImgInTempFolder(mockFile));
            }
        }
    }

    // SSO 분리로 인해 유저 초대/초대 재전송/초대된 유저 수정 테스트는 porest-sso로 이동됨

    @Nested
    @DisplayName("아이디 중복 확인")
    class CheckUserIdDuplicate {
        @Test
        @DisplayName("성공 - 중복이면 true를 반환한다")
        void checkUserIdDuplicateTrue() {
            // given
            User user = createTestUser("user1", "유저", "user1@test.com");
            given(userRepository.findById("user1")).willReturn(Optional.of(user));

            // when
            boolean result = userService.checkUserIdDuplicate("user1");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("성공 - 중복이 아니면 false를 반환한다")
        void checkUserIdDuplicateFalse() {
            // given
            given(userRepository.findById("newuser")).willReturn(Optional.empty());

            // when
            boolean result = userService.checkUserIdDuplicate("newuser");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("메인 부서 존재 확인")
    class CheckUserHasMainDepartment {
        @Test
        @DisplayName("성공 - 메인 부서가 있으면 Y를 반환한다")
        void checkUserHasMainDepartmentY() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(departmentRepository.hasMainDepartment(userId)).willReturn(true);

            // when
            YNType result = userService.checkUserHasMainDepartment(userId);

            // then
            assertThat(result).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("성공 - 메인 부서가 없으면 N을 반환한다")
        void checkUserHasMainDepartmentN() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(departmentRepository.hasMainDepartment(userId)).willReturn(false);

            // when
            YNType result = userService.checkUserHasMainDepartment(userId);

            // then
            assertThat(result).isEqualTo(YNType.N);
        }
    }

    @Nested
    @DisplayName("유저 존재 확인")
    class CheckUserExist {
        @Test
        @DisplayName("성공 - 존재하는 유저를 반환한다")
        void checkUserExistSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            User result = userService.checkUserExist(userId);

            // then
            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저면 예외가 발생한다")
        void checkUserExistFailNotFound() {
            // given
            given(userRepository.findById("nonexistent")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.checkUserExist("nonexistent"))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 삭제된 유저면 예외가 발생한다")
        void checkUserExistFailDeleted() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            user.deleteUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> userService.checkUserExist(userId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("프로필 URL에서 물리적 파일명 추출")
    class ExtractPhysicalFileNameFromUrl {
        @Test
        @DisplayName("성공 - 물리적 파일명을 추출한다")
        void extractPhysicalFileNameFromUrlSuccess() {
            // given
            String profileUrl = "/media/temp/profile/test.jpg_some-uuid";

            // when
            String result = userService.extractPhysicalFileNameFromUrl(profileUrl);

            // then
            assertThat(result).isEqualTo("test.jpg_some-uuid");
        }

        @Test
        @DisplayName("성공 - profileUrl이 null이면 null을 반환한다")
        void extractPhysicalFileNameFromUrlNull() {
            // when
            String result = userService.extractPhysicalFileNameFromUrl(null);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("성공 - profileUrl이 빈 문자열이면 null을 반환한다")
        void extractPhysicalFileNameFromUrlEmpty() {
            // when
            String result = userService.extractPhysicalFileNameFromUrl("");

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("프로필 URL 생성")
    class GenerateProfileUrl {
        @Test
        @DisplayName("성공 - 프로필 URL을 생성한다")
        void generateProfileUrlSuccess() {
            // given
            String originalFilename = "test.jpg";
            String uuid = "some-uuid";

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                mocked.when(() -> FileUtils.generatePhysicalFilename(originalFilename, uuid))
                        .thenReturn("test.jpg_some-uuid");

                // when
                String result = userService.generateProfileUrl(originalFilename, uuid);

                // then
                assertThat(result).contains("test.jpg_some-uuid");
            }
        }

        @Test
        @DisplayName("성공 - physicalFilename이 null이면 null을 반환한다")
        void generateProfileUrlNull() {
            // given
            String originalFilename = null;
            String uuid = "some-uuid";

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                mocked.when(() -> FileUtils.generatePhysicalFilename(originalFilename, uuid))
                        .thenReturn(null);

                // when
                String result = userService.generateProfileUrl(originalFilename, uuid);

                // then
                assertThat(result).isNull();
            }
        }
    }

    @Nested
    @DisplayName("임시 프로필 이미지를 원본 폴더로 복사")
    class CopyTempProfileToOrigin {
        @Test
        @DisplayName("성공 - 프로필 이미지가 복사된다")
        void copyTempProfileToOriginSuccess() {
            // given
            UserServiceDto data = UserServiceDto.builder()
                    .profileUrl("/media/temp/profile/test.jpg_some-uuid")
                    .profileUUID("some-uuid")
                    .build();

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                mocked.when(() -> FileUtils.copy(anyString(), anyString(), eq(messageResolver)))
                        .thenReturn(true);
                mocked.when(() -> FileUtils.extractOriginalFilename(anyString(), isNull()))
                        .thenReturn("test.jpg");

                // when
                UserServiceDto result = userService.copyTempProfileToOrigin(data);

                // then
                assertThat(result.getProfileName()).isEqualTo("test.jpg");
                assertThat(result.getProfileUUID()).isEqualTo("some-uuid");
            }
        }

        @Test
        @DisplayName("성공 - profileUrl이 null이면 빈 결과를 반환한다")
        void copyTempProfileToOriginNullUrl() {
            // given
            UserServiceDto data = UserServiceDto.builder()
                    .profileUrl(null)
                    .build();

            // when
            UserServiceDto result = userService.copyTempProfileToOrigin(data);

            // then
            assertThat(result.getProfileName()).isNull();
            assertThat(result.getProfileUUID()).isNull();
        }

        @Test
        @DisplayName("성공 - 파일 복사 실패 시 빈 결과를 반환한다")
        void copyTempProfileToOriginCopyFail() {
            // given
            UserServiceDto data = UserServiceDto.builder()
                    .profileUrl("/media/temp/profile/test.jpg_some-uuid")
                    .profileUUID("some-uuid")
                    .build();

            try (MockedStatic<FileUtils> mocked = mockStatic(FileUtils.class)) {
                mocked.when(() -> FileUtils.copy(anyString(), anyString(), eq(messageResolver)))
                        .thenReturn(false);

                // when
                UserServiceDto result = userService.copyTempProfileToOrigin(data);

                // then
                assertThat(result.getProfileName()).isNull();
                assertThat(result.getProfileUUID()).isNull();
            }
        }
    }

    @Nested
    @DisplayName("대시보드 수정")
    class UpdateDashboard {
        @Test
        @DisplayName("성공 - 대시보드 데이터가 수정된다")
        void updateDashboardSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            String dashboardData = "{\"widgets\": [{\"type\": \"chart\", \"position\": 1}]}";

            // when
            UserServiceDto result = userService.updateDashboard(userId, userId, dashboardData);

            // then
            then(userRepository).should().findById(userId);
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getDashboard()).isEqualTo(dashboardData);
            assertThat(user.getDashboard()).isEqualTo(dashboardData);
        }

        @Test
        @DisplayName("성공 - null로 대시보드를 초기화한다")
        void updateDashboardWithNull() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            user.updateDashboard("{\"old\": \"data\"}");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            UserServiceDto result = userService.updateDashboard(userId, userId, null);

            // then
            assertThat(result.getDashboard()).isNull();
            assertThat(user.getDashboard()).isNull();
        }

        @Test
        @DisplayName("실패 - 남의 대시보드면 403 이고 저장소를 건드리지 않는다")
        void updateDashboardFailNotOwner() {
            // given

            // when & then
            assertThatThrownBy(() -> userService.updateDashboard("user1", "other", "{\"new\": true}"))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((ForbiddenException) e).getErrorCode().getCode())
                    .isEqualTo("PERMISSION_001");
            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실패 - 관리자(ADMIN 권한 묶음)도 남의 대시보드면 403 (권한과 무관, 서비스는 권한을 받지 않는다)")
        void updateDashboardFailAdminNotOwner() {
            // given

            // when & then - 서비스는 권한을 받지 않으므로 관리자도 로그인 id 가 다르면 같은 경로로 막힌다
            assertThatThrownBy(() -> userService.updateDashboard("admin", "other", "{\"new\": true}"))
                    .isInstanceOf(ForbiddenException.class);
            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저면 예외가 발생한다")
        void updateDashboardFailNotFound() {
            // given
            String userId = "nonexistent";
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.updateDashboard(userId, userId, "{\"test\": true}"))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("실패 - 삭제된 유저면 예외가 발생한다")
        void updateDashboardFailDeleted() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            user.deleteUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> userService.updateDashboard(userId, userId, "{\"test\": true}"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("승인권자 목록 조회")
    class GetUserApprovers {
        @Test
        @DisplayName("성공 - 승인권자 목록을 반환한다")
        void getUserApproversSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            User approver = createTestUser("head1", "부서장", "head@test.com");

            Company company = Company.createCompany("회사", "Company", "desc");
            Department dept = Department.createDepartment("부서", "부서KR", null, approver, 1L, "desc", "#000", company);
            ReflectionTestUtils.setField(dept, "rowId", 1L);

            given(departmentRepository.findApproversByUserId(userId)).willReturn(List.of(dept));
            given(userRepository.findByIdWithRolesAndPermissions("head1")).willReturn(Optional.of(approver));

            // when
            List<UserServiceDto> result = userService.getUserApprovers(userId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("부서장");
            assertThat(result.get(0).getDepartmentName()).isEqualTo("부서");
        }

        @Test
        @DisplayName("성공 - 부서장이 없으면 해당 부서는 결과에서 제외된다")
        void getUserApproversWithNullApprover() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            Company company = Company.createCompany("회사", "Company", "desc");
            Department dept = Department.createDepartment("부서", "부서KR", null, null, 1L, "desc", "#000", company);
            ReflectionTestUtils.setField(dept, "rowId", 1L);

            given(departmentRepository.findApproversByUserId(userId)).willReturn(List.of(dept));

            // when
            List<UserServiceDto> result = userService.getUserApprovers(userId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공 - 삭제된 부서장은 결과에서 제외된다")
        void getUserApproversWithDeletedApprover() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            User deletedApprover = createTestUser("deleted", "삭제된부서장", "deleted@test.com");
            deletedApprover.deleteUser();

            Company company = Company.createCompany("회사", "Company", "desc");
            Department dept = Department.createDepartment("부서", "부서KR", null, deletedApprover, 1L, "desc", "#000", company);
            ReflectionTestUtils.setField(dept, "rowId", 1L);

            given(departmentRepository.findApproversByUserId(userId)).willReturn(List.of(dept));
            given(userRepository.findByIdWithRolesAndPermissions("deleted")).willReturn(Optional.of(deletedApprover));

            // when
            List<UserServiceDto> result = userService.getUserApprovers(userId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공 - 빈 목록을 반환한다")
        void getUserApproversEmpty() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "user1@test.com");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(departmentRepository.findApproversByUserId(userId)).willReturn(List.of());

            // when
            List<UserServiceDto> result = userService.getUserApprovers(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("ID로 Role 포함 유저 조회")
    class GetUserWithRolesById {
        @Test
        @DisplayName("성공 - Role과 Permission을 포함한 유저를 반환한다")
        @SuppressWarnings("unchecked")
        void getUserWithRolesByIdSuccess() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "test@test.com");

            TypedQuery<User> userQuery = mock(TypedQuery.class);
            given(em.createQuery(anyString(), eq(User.class))).willReturn(userQuery);
            given(userQuery.setParameter(eq("userId"), eq(userId))).willReturn(userQuery);
            given(userQuery.setParameter(eq("isDeleted"), eq(YNType.N))).willReturn(userQuery);
            given(userQuery.getResultList()).willReturn(List.of(user));

            // when
            Optional<User> result = userService.getUserWithRolesById(userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("성공 - 유저가 없으면 빈 Optional을 반환한다")
        @SuppressWarnings("unchecked")
        void getUserWithRolesByIdNotFound() {
            // given
            String userId = "nonexistent";

            TypedQuery<User> userQuery = mock(TypedQuery.class);
            given(em.createQuery(anyString(), eq(User.class))).willReturn(userQuery);
            given(userQuery.setParameter(eq("userId"), eq(userId))).willReturn(userQuery);
            given(userQuery.setParameter(eq("isDeleted"), eq(YNType.N))).willReturn(userQuery);
            given(userQuery.getResultList()).willReturn(List.of());

            // when
            Optional<User> result = userService.getUserWithRolesById(userId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공 - Role이 있는 유저의 Permission도 조회한다")
        @SuppressWarnings("unchecked")
        void getUserWithRolesByIdWithRoles() {
            // given
            String userId = "user1";
            User user = createTestUser(userId, "유저", "test@test.com");
            Role role = Role.createRole("ADMIN", "관리자", "관리자 역할");
            user.addRole(role);

            TypedQuery<User> userQuery = mock(TypedQuery.class);
            TypedQuery<Role> roleQuery = mock(TypedQuery.class);

            given(em.createQuery(contains("from User"), eq(User.class))).willReturn(userQuery);
            given(userQuery.setParameter(eq("userId"), eq(userId))).willReturn(userQuery);
            given(userQuery.setParameter(eq("isDeleted"), eq(YNType.N))).willReturn(userQuery);
            given(userQuery.getResultList()).willReturn(List.of(user));

            given(em.createQuery(contains("from Role"), eq(Role.class))).willReturn(roleQuery);
            given(roleQuery.setParameter(eq("roles"), anyList())).willReturn(roleQuery);
            given(roleQuery.getResultList()).willReturn(List.of(role));

            // when
            Optional<User> result = userService.getUserWithRolesById(userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getRoles()).contains(role);
        }
    }

    // SSO 분리로 인해 초대 토큰, 비밀번호 초기화/변경, 회원가입 관련 테스트는 porest-sso로 이동됨
}
