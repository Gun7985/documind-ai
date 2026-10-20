# DocuMind AI 아키텍처 개요

## 구성요소와 책임

- **React/TypeScript:** 클라우드에 배포되는 사용자 UI로 로그인, PDF 관리, 분석 요청과 결과 조회를 제공한다.
- **Spring Boot:** 클라우드의 유일한 공개 애플리케이션 API로 JWT 검증, 사용자 권한, 업무 로직 및 분석 작업 조정을 담당한다.
- **Supabase:** Auth, PostgreSQL 및 비공개 Storage를 제공한다.
- **FastAPI:** 사용자 PC에서 실행되는 내부 서비스로 PyMuPDF를 통한 텍스트 추출과 AI 분석 흐름을 담당한다.
- **Ollama:** 사용자 PC에서 로컬 LLM 추론을 수행하며 외부에 직접 공개하지 않는다.

## 요청 흐름

```text
브라우저
  -> 클라우드 React
  -> 클라우드 Spring Boot
      -> Supabase Auth / PostgreSQL / Private Storage
      -> 인증된 보안 터널
          -> 사용자 PC FastAPI
              -> PyMuPDF
              -> Ollama (local LLM)
```

1. 사용자는 React UI에서 Supabase Auth로 인증한다.
2. React는 액세스 토큰과 함께 Spring Boot 공개 API를 호출한다.
3. Spring Security는 Supabase JWT를 검증하고 Spring Boot는 요청 사용자와 데이터 소유권을 확인한다.
4. Spring Boot는 메타데이터를 PostgreSQL에, PDF를 비공개 Storage에 관리한다.
5. 분석 요청은 인증된 보안 터널을 통해서만 로컬 FastAPI에 전달한다.
6. FastAPI는 PyMuPDF로 텍스트를 추출하고 외부에 공개되지 않은 Ollama로 분석한다.
7. 상태와 결과는 Spring Boot의 권한 검사 아래 사용자에게 반환된다.

## 보안 경계

- 브라우저에는 공개용 anon/publishable key만 제공하며 서비스 역할 키를 제공하지 않는다.
- 비공개 PDF 접근과 데이터 소유권은 클라이언트 신뢰 없이 서버 측에서 검증한다.
- FastAPI와 Ollama는 공용 인터넷에 직접 노출하지 않는다.
- 클라우드와 로컬 FastAPI 사이는 상호 인증 또는 동등한 보호를 갖춘 보안 터널만 사용한다.

## 검증 필요

- **클라우드 호스팅:** React 및 Spring Boot 호스팅 제공자, 네트워크 제약과 임시 배포 비용은 검증 필요.
- **터널 인증:** 터널 제품, 인증 방식, 토큰 회전 및 접근 제한 방식은 검증 필요.
- **VRAM/성능:** 선택한 Ollama 모델의 VRAM 요구량, 10MB/30페이지 PDF 처리 시간 및 동시 처리 한계는 실제 사용자 PC에서 검증 필요.
