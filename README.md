# DocuMind AI

텍스트 기반 PDF를 업로드하고 로컬 LLM으로 요약, 핵심 포인트와 키워드를 생성하는 문서 분석 웹 애플리케이션입니다.

현재 단계는 **Phase 2 기본 애플리케이션 구축**입니다. 각 서비스는 health 또는 플레이스홀더 수준으로 독립 실행되며 실제 인증, 문서 관리, 데이터베이스 및 AI 분석 기능은 아직 구현하지 않았습니다.

## 구성요소

- **Frontend:** React + TypeScript 사용자 인터페이스
- **Backend:** Java + Spring Boot 공개 API, 인증/권한 및 업무 로직
- **AI Service:** Python + FastAPI, PyMuPDF 기반 PDF 추출 및 내부 AI 처리
- **Platform:** Supabase Auth, PostgreSQL 및 비공개 Storage
- **Local LLM:** 외부에 직접 공개하지 않는 Ollama 로컬 추론

## 계획된 라이프사이클

1. 요구사항과 아키텍처를 확정한다.
2. MVP를 구현하고 최소한의 자동화 테스트를 수행한다.
3. 완성된 웹 서비스를 짧은 기간 실제 환경에 배포한다.
4. E2E 검증과 증빙 수집을 완료한다.
5. 서비스와 보안 터널을 종료하고 비밀키 및 임시 데이터를 회수한다.

상시 운영과 수익화는 현재 프로젝트 범위에 포함되지 않습니다.

## 개발 요구사항

- Node.js 24 이상 및 npm 11 이상
- Java 17 LTS
- Python 3.13

의존성은 `frontend/package-lock.json`, Gradle Wrapper, `ai-service/requirements.txt`의 해시로 재현합니다.

## 로컬 실행

### Frontend

```powershell
cd frontend
npm ci --ignore-scripts
npm run dev
```

기본 URL은 `http://localhost:5173`입니다. 타입검사, 테스트 및 빌드는 각각 `npm run typecheck`, `npm run test:run`, `npm run build`로 실행합니다.

### Backend

```powershell
cd backend
.\gradlew.bat bootRun
```

기본 URL은 `http://localhost:8080`이며 Health Check는 `GET /actuator/health`입니다. 테스트는 `.\gradlew.bat test`로 실행합니다. 현재 DB 및 Supabase 연결은 없습니다.

### AI Service

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install --require-hashes -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Health Check는 `GET http://127.0.0.1:8000/health`입니다. 테스트는 `.\.venv\Scripts\python.exe -m pytest`로 실행합니다. FastAPI는 내부 서비스로 유지하며 Ollama와 PyMuPDF는 아직 연결하지 않았습니다.

macOS/Linux에서는 가상환경 실행 파일 경로로 `.venv/bin/python`을 사용하고 백엔드는 `./gradlew`로 실행합니다.

## 환경 설정

루트 `.env.example`은 필요한 설정 이름만 설명합니다. `VITE_` 접두사가 붙은 세 변수만 향후 React 번들에 공개할 수 있으며, 서비스 역할 키·DB 비밀번호·내부 인증 토큰은 절대 `VITE_` 변수로 만들지 않습니다. 현재 기본 실행과 CI에는 환경변수나 외부 API 키가 필요하지 않습니다.

## 문서

- [MVP 요구사항](docs/requirements/MVP.md)
- [아키텍처 개요](docs/architecture/OVERVIEW.md)
- [데이터 모델 및 ERD](docs/architecture/ERD.md)
- [인증 및 보안 설계](docs/architecture/SECURITY.md)
- [AI 분석 파이프라인](docs/architecture/AI_PIPELINE.md)
- [OpenAPI 3.1 명세](docs/api/openapi.yaml)
- [배포 검증 및 종료 체크리스트](docs/deployment/VERIFICATION_AND_SHUTDOWN.md)
- [저장소 작업 지침](AGENTS.md)
