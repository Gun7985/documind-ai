# DocuMind AI

텍스트 기반 PDF를 업로드하고 로컬 LLM으로 요약, 핵심 포인트와 키워드를 생성하는 문서 분석 웹 애플리케이션입니다.

현재 단계는 **설계 및 초기 프로젝트 설정**입니다. 이 저장소에는 아직 실행 가능한 애플리케이션 코드나 배포 인프라가 없습니다.

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

## 문서

- [MVP 요구사항](docs/requirements/MVP.md)
- [아키텍처 개요](docs/architecture/OVERVIEW.md)
- [배포 검증 및 종료 체크리스트](docs/deployment/VERIFICATION_AND_SHUTDOWN.md)
- [저장소 작업 지침](AGENTS.md)
