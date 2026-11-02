<p align="center">
  <img src="https://img.shields.io/badge/%EA%B7%B8%EB%A3%A8_%EC%9D%B4%EC%9D%8C-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="그루" />
</p>

<h1 align="center">그루 이음 Backend</h1>

<p align="center">
  <strong>사업장 근로자를 위한 일정관리 및 휴가관리 서비스</strong>
</p>

<p align="center">
  <a href="https://github.com/lshdainty/groo-ieum-back/actions/workflows/ci-main.yml">
    <img src="https://github.com/lshdainty/groo-ieum-back/actions/workflows/ci-main.yml/badge.svg" alt="CI/CD" />
  </a>
  <a href="https://codecov.io/gh/lshdainty/groo-ieum-back">
    <img src="https://codecov.io/gh/lshdainty/groo-ieum-back/branch/main/graph/badge.svg" alt="codecov" />
  </a>
  <img src="https://img.shields.io/badge/Java-25-007396?logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
</p>

---

## 소개

**그루 이음 Backend**는 [그루](https://github.com/lshdainty/groo) 서비스의 HR(인사관리) 백엔드입니다.

Golang 사용자가 Java Spring Boot를 공부하고자 시작했으며, 기존 사용하던 Legacy 프로그램을 **JPA**, **QueryDSL** 등 실무에서 많이 사용중인 라이브러리를 사용하여 개선하고자 합니다.

인증은 [porest-sso-back](https://github.com/lshdainty/groo-sso-back)에 위임하며, HR은 SSO 토큰(RS256)을 JWKS로 검증한 뒤 자체 JWT(HS256)로 교환하여 사용합니다. 회원가입 완료 등 사용자 이벤트는 Redis Pub/Sub 채널로 수신합니다.

---

## 기술 스택

| Category | Technology |
|----------|------------|
| **Language** | ![Java](https://img.shields.io/badge/Java_25-007396?style=flat-square&logo=openjdk&logoColor=white) |
| **Framework** | ![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.0.4-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white) |
| **ORM** | ![JPA](https://img.shields.io/badge/JPA-59666C?style=flat-square&logo=hibernate&logoColor=white) ![QueryDSL](https://img.shields.io/badge/QueryDSL_7.1-0769AD?style=flat-square) |
| **Database** | ![MariaDB](https://img.shields.io/badge/MariaDB_3.5.1-003545?style=flat-square&logo=mariadb&logoColor=white) ![H2](https://img.shields.io/badge/H2_(runtime)-004088?style=flat-square) |
| **Messaging** | ![Redis](https://img.shields.io/badge/Redis_Pub%2FSub-DC382D?style=flat-square&logo=redis&logoColor=white) SSO 사용자 이벤트 수신 |
| **Authentication** | ![JWT](https://img.shields.io/badge/JJWT_0.12.6-000000?style=flat-square&logo=jsonwebtokens&logoColor=white) ![Nimbus JOSE](https://img.shields.io/badge/Nimbus_JOSE_10.4-5A29E4?style=flat-square) SSO 토큰(RS256) JWKS 검증 |
| **Utilities** | ![Apache POI](https://img.shields.io/badge/Apache_POI_5.3.0-D22128?style=flat-square) ![Korean Lunar](https://img.shields.io/badge/KoreanLunarCalendar_0.3.1-0076D6?style=flat-square) ![Spring Mail](https://img.shields.io/badge/Spring_Mail-6DB33F?style=flat-square) |
| **Monitoring** | ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white) ![Loki](https://img.shields.io/badge/Loki_2.0.1-F46800?style=flat-square&logo=grafana&logoColor=white) ![P6Spy](https://img.shields.io/badge/P6Spy-4B32C3?style=flat-square) |
| **공통 라이브러리** | ![groo-core](https://img.shields.io/badge/groo--core_3.0.0-6DB33F?style=flat-square) |
| **API Documentation** | ![Swagger](https://img.shields.io/badge/SpringDoc_OpenAPI_3.0.0-85EA2D?style=flat-square&logo=swagger&logoColor=black) |
| **Testing** | ![JUnit5](https://img.shields.io/badge/JUnit5-25A162?style=flat-square&logo=junit5&logoColor=white) ![Mockito](https://img.shields.io/badge/Mockito-C5D9C8?style=flat-square) |
| **Build** | ![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white) |

---

## 프로젝트 아키텍처

### 계층 구조

도메인별 패키지 안에서 계층을 분리하는 구조입니다.

- **Controller**: REST API 엔드포인트, DTO 변환 (엔티티 직접 노출 금지)
- **Service**: 비즈니스 로직, 트랜잭션 경계 (`@Transactional`)
- **Repository**: 데이터 접근 (QueryDSL 기본 + JPQL 백업)

### 모듈 구조

```
porest-hr-back (단일 Gradle 모듈: porest-hr)
└── groo-core   # 공통 라이브러리 (GitHub Packages 의존성)
```

- **groo-core**: 예외 처리, API 응답 포맷(`ApiResponse`), 공통 타입, i18n 메시지 키 등 프로젝트 전반에서 사용되는 컴포넌트 제공
- **회사별 확장**: 회사/시스템 코드 테이블과 회사별 메시지 번들(`message/messages-{company}`)로 회사별 커스텀을 분리

---

## 도메인 모듈

```
src/main/java/io/groo/ieum/
├── user/                  # 사용자 관리
├── vacation/              # 휴가 관리 (핵심 도메인)
├── work/                  # 근무 이력 관리
├── schedule/              # 일정 관리
├── holiday/               # 공휴일 관리
├── department/            # 부서 관리
├── company/               # 회사 관리
├── permission/            # 권한/역할 관리
├── dues/                  # 회비 관리
├── notice/                # 공지사항
├── calendar/              # 캘린더 API
├── client/                # 외부 서비스(SSO) 연동 클라이언트
├── security/              # 인증/보안 (JWT 필터, 토큰 교환, IP 블랙리스트)
└── common/                # 공통 설정, 이벤트, 타입 조회 API
```

### 각 도메인 패키지 구조

```
{domain}/
├── domain/           # JPA 엔티티
├── controller/       # REST API 엔드포인트
├── service/          # 비즈니스 로직 (@Transactional)
├── repository/       # 데이터 접근 (QueryDSL + JPQL)
└── type/             # 도메인별 Enum 타입
```

> 핵심 도메인인 `vacation`은 추가로 휴가 정책 전략(`service/policy/` — ManualGrant/OnRequest/RepeatGrant)과 연차 자동 부여 스케줄러(`scheduler/`)를 갖습니다.

### Repository 패턴

각 도메인의 Repository는 3개 파일로 구성됩니다.

```java
// 1. Interface
public interface UserRepository { ... }

// 2. QueryDSL Implementation (@Primary)
@Repository @Primary
public class UserQueryDslRepository implements UserRepository { ... }

// 3. JPQL Fallback Implementation
@Repository("userJpaRepository")
public class UserJpaRepository implements UserRepository { ... }
```

---

## API 컨벤션

- **Base path**: `/api/v1`
- **응답 JSON은 snake_case**: 모든 컨트롤러 DTO에 `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` 적용
- **공통 응답 포맷**: groo-core의 `ApiResponse` 사용
- **API 문서**: `SWAGGER_ENABLED=true` 설정 시 `/swagger-ui.html` (SpringDoc OpenAPI)

---

## 시작하기

### 요구사항

- **Java**: 25 (toolchain 자동 관리)
- **Gradle**: Wrapper 포함 (9.2.1)
- **MariaDB**: 운영 DB (`ddl-auto: none`)
- **Redis**: SSO 사용자 이벤트 수신 (Pub/Sub 채널 `porest:sso:user-events`)
- **GitHub Packages 접근**: `GITHUB_ACTOR`, `GITHUB_TOKEN` 환경변수 필요 (groo-core 의존성)

### 환경 설정

프로필별 설정은 단일 `application.yml` + `.env.{profile}` 파일 조합으로 관리합니다.

```
.env.local    # 로컬 (기본 프로필)
.env.dev      # 개발
.env.prod     # 운영
.env.example  # 환경변수 목록 참고용
```

### 빌드 및 실행

```bash
# 빌드 (테스트 제외)
./gradlew clean build -x test

# 로컬 실행
./gradlew bootRun -Dspring.profiles.active=local
```

> 참고: 현재 `build.gradle`에서 `test` 태스크가 `enabled = false`로 설정되어 있으며, CI에서도 테스트를 임시 스킵합니다. (`src/test`에 Repository/Service 테스트 70여 개 보유)

---

## 국제화 (i18n)

```
src/main/resources/message/
├── messages.properties      # 기본 (영어)
├── messages_en.properties   # 영어
└── messages_ko.properties   # 한국어
```

- Bean Validation 메시지는 `ValidationMessages{,_en,_ko}.properties`로 별도 관리
- 메시지 basename에 groo-core 제공 `core-messages`와 회사별 `messages-{company}` 번들 포함

---

## 관련 저장소

| Repository | Description |
|------------|-------------|
| [그루](https://github.com/lshdainty/groo) | 통합 레포지토리 (서비스 소개) |
| [porest-hr-front](https://github.com/lshdainty/groo-ieum-front) | HR 프론트엔드 |
| [groo-core](https://github.com/lshdainty/groo-core) | 공통 라이브러리 |
| [porest-sso-back](https://github.com/lshdainty/groo-sso-back) | SSO 백엔드 |
| [porest-sso-front](https://github.com/lshdainty/groo-sso-front) | SSO 프론트엔드 |

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/lshdainty">lshdainty</a>
</p>
