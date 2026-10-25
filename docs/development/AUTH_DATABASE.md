# 인증 및 데이터베이스 개발 환경

## 구성

- React는 `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`만 사용해 Supabase Auth 세션을 관리한다.
- Spring Security는 Supabase JWT의 JWKS 서명, issuer, audience, 만료 시간을 검증한다.
- Spring Boot만 공개 업무 API이며, 데이터 접근 전에 JWT subject(UUID)와 리소스 소유자를 비교한다.
- PostgreSQL RLS는 `auth.uid()`를 기준으로 애플리케이션 검증을 보완한다.

서비스 역할 키, 데이터베이스 비밀번호 및 토큰에는 `VITE_` 접두사를 붙이지 않는다. `.env` 파일은 Git에서 제외되며 `.env.example`에는 이름만 기록한다.

## Supabase 준비

외부 프로젝트를 자동 생성하지 않는다. 사용할 프로젝트에서 다음 값을 확인해 로컬의 추적되지 않는 환경에 설정한다.

- 프로젝트 URL과 publishable/anon key
- JWT issuer, audience, JWKS URL
- PostgreSQL 연결 URL과 계정
- React 개발 서버와 배포 URL을 포함한 Auth redirect URL

JWKS 검증을 위해 비대칭 JWT signing key 사용을 전제로 한다. 실제 프로젝트의 signing key 방식과 issuer/audience는 연결 전에 확인해야 한다.

## Flyway 마이그레이션

Flyway는 Spring Boot 생명주기에 통합되고 적용 이력을 `flyway_schema_history`에 남기므로, 버전이 적용된 마이그레이션을 반복 실행하지 않는다. 기존 마이그레이션 파일은 적용 후 수정하지 않고 새 버전 파일을 추가한다.

가능하면 DDL 권한을 가진 전용 `FLYWAY_DATABASE_*` 계정과 최소 권한의 `DATABASE_*` 런타임 계정을 분리한다. 전용 값을 생략하면 로컬 개발 편의를 위해 런타임 설정으로 대체된다.

```powershell
cd backend
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/documind'
$env:DATABASE_USERNAME = 'documind_app'
$env:DATABASE_PASSWORD = '<local-secret>'
$env:FLYWAY_DATABASE_URL = 'jdbc:postgresql://localhost:5432/documind'
$env:FLYWAY_DATABASE_USERNAME = 'documind_migrator'
$env:FLYWAY_DATABASE_PASSWORD = '<local-secret>'
$env:SUPABASE_JWT_ISSUER = 'https://<project-ref>.supabase.co/auth/v1'
$env:SUPABASE_JWKS_URI = 'https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json'
$env:SUPABASE_JWT_AUDIENCE = 'authenticated'
.\gradlew.bat bootRun
```

애플리케이션 시작 전에 Flyway가 `V1__create_document_analysis_schema.sql`을 적용한다. 마이그레이션은 Supabase의 `auth.users`, `auth.uid()`와 `authenticated` 역할이 이미 존재하는 환경을 대상으로 한다.

## 테스트 환경

일반 보안 테스트는 외부 키 없이 로컬에서 실행된다.

```powershell
cd backend
.\gradlew.bat test
```

PostgreSQL 제약과 RLS 통합 테스트는 일반 단위 테스트와 별도의 `databaseIntegrationTest` 태스크로 실행한다. 이 태스크는 `TEST_DATABASE_URL`이 없으면 실패하며 조건부로 건너뛰지 않는다. 오작동 방지를 위해 데이터베이스 이름은 반드시 `_test`로 끝나야 한다. 운영 또는 개발 DB를 지정하지 않는다.

통합 테스트가 구성하는 `auth` 스키마, `auth.users`, `auth.uid()` 및 역할은 PostgreSQL에서 정책과 제약을 검사하기 위한 **Supabase 테스트 대역**이다. 이 결과는 실제 Supabase 프로젝트의 RLS 검증 완료를 의미하지 않는다.

```powershell
$env:TEST_DATABASE_URL = 'jdbc:postgresql://localhost:5432/documind_test'
$env:TEST_DATABASE_USERNAME = 'postgres'
$env:TEST_DATABASE_PASSWORD = '<test-only-secret>'
.\gradlew.bat databaseIntegrationTest
```

GitHub Actions는 일회성 PostgreSQL 서비스와 공개 테스트 전용 자격증명을 사용하며 운영 비밀값을 사용하지 않는다.

## 런타임 DB 역할과 RLS 권고안

권고안은 Spring Boot 런타임 연결에도 RLS를 적용하는 것이다. 마이그레이션 계정은 DDL 전용으로 유지하고 애플리케이션 풀 계정에는 `SUPERUSER`, `BYPASSRLS`, 테이블 소유권 및 직접 DDL 권한을 주지 않는다. 런타임 계정은 `authenticated` 역할을 `SET ROLE`할 수 있는 멤버십만 받고, 데이터 DML 권한은 `authenticated` 역할을 통해 얻는다.

각 업무 트랜잭션은 다음 순서를 하나의 트랜잭션 경계 안에서 수행해야 한다.

1. Spring Security가 검증한 JWT `sub`를 UUID로 변환한다.
2. `SET LOCAL ROLE authenticated`를 실행한다.
3. `select set_config('request.jwt.claim.sub', :userId, true)`로 트랜잭션 로컬 사용자 컨텍스트를 설정한다.
4. 모든 repository 쿼리에서 `owner_id = :userId` 조건을 별도로 적용한다.
5. commit 또는 rollback으로 트랜잭션을 끝낸다.

`SET LOCAL`과 `set_config(..., true)`는 트랜잭션 종료 시 자동 해제되므로 풀에 반환된 커넥션으로 사용자 컨텍스트가 이어지는 것을 막는다. auto-commit 상태에서 세션 범위 `SET ROLE` 또는 `set_config(..., false)`를 사용하지 않는다. 예외 경로에서도 반드시 rollback하고, 풀 반환 시 연결 상태 초기화 및 교차 사용자 연속 요청 테스트를 추가한다.

서버의 `owner_id` 조건은 주된 인가 통제이고 RLS는 심층 방어다. 둘 중 하나만으로 소유권을 판단하지 않는다. 현재 Phase 3A에는 업무 repository가 없으므로 위 컨텍스트 주입은 아직 구현되지 않았으며, 구현 전에는 런타임 계정을 운영 데이터베이스에 연결하지 않는다.

대안으로 RLS 우회 역할을 사용할 경우 권한 범위는 세 테이블의 필요한 DML로만 제한하고 DDL·Auth 스키마 수정 권한을 제거해야 한다. 그러나 쿼리 하나의 소유자 조건 누락이 곧 데이터 노출로 이어지므로 MVP에서는 이 방식을 권장하지 않는다. 불가피하다면 모든 repository의 사용자 A/B IDOR 통합 테스트, SQL 리뷰 및 별도 감사 로깅이 필수다.
