# 토스 페이먼츠 결제 서비스 문서

> Version: v0.0.1-SNAPSHOT | Java 21 | Spring Boot 3.5.7

## 문서 구조

### 1. 프로젝트 정보

- [INFO.md](INFO.md) - 프로젝트 개요 및 팀 정보
- [PROJECT_SETUP.md](PROJECT_SETUP.md) - 개발 환경 설정 가이드

### 2. 아키텍처 문서

- [ARCHITECTURE.md](ARCHITECTURE.md) - 전체 시스템 아키텍처
- [architecture/](architecture/) - 상세 아키텍처 설계
	- [HEXAGONAL_ARCHITECTURE.md](architecture/HEXAGONAL_ARCHITECTURE.md) - 헥사고날 아키텍처 적용
	- [EVENT_DRIVEN_DESIGN.md](architecture/EVENT_DRIVEN_DESIGN.md) - 이벤트 기반 설계
	- [MSA_INTEGRATION.md](architecture/MSA_INTEGRATION.md) - MSA 통합 전략

### 3. ADR (Architecture Decision Records)

- [adr/](adr/) - 아키텍처 의사결정 기록
	- [001-java-21-adoption.md](adr/001-java-21-adoption.md) - Java 21 도입 ✅
	- [002-event-driven-payment-preparation.md](adr/002-event-driven-payment-preparation.md) - 이벤트 기반 결제 준비 ✅
	- [003-toss-payments-integration.md](adr/003-toss-payments-integration.md) - 토스 페이먼츠 연동 방식 ✅
	- [004-outbox-pattern.md](adr/004-outbox-pattern.md) - Outbox Pattern 도입 ✅
	- [005-refund-policy-design.md](adr/005-refund-policy-design.md) - 환불 정책 설계 ✅
	- [006-exception-handling-standardization.md](adr/006-exception-handling-standardization.md) - 예외 처리 표준화 ✅

### 4. 기능 명세

- [features/](features/) - 기능별 상세 명세
	- [PAYMENT_FLOW.md](features/PAYMENT_FLOW.md) - 결제 플로우 ✅
	- [REFUND_FLOW.md](features/REFUND_FLOW.md) - 환불 플로우 ✅
	- [EVENT_PUBLISHING.md](features/EVENT_PUBLISHING.md) - 이벤트 발행 ✅

### 5. API 명세

- [API_SPECIFICATION.md](API_SPECIFICATION.md) - REST API 전체 명세

### 6. 구현 가이드

- [implementation/](implementation/) - 구현 세부사항
	- [DOMAIN_MODEL.md](implementation/DOMAIN_MODEL.md) - 도메인 모델 설계 ✅
	- [EXCEPTION_HANDLING.md](implementation/EXCEPTION_HANDLING.md) - 예외 처리 표준화 ✅
	- [DESIGN_PATTERNS.md](implementation/DESIGN_PATTERNS.md) - 적용된 디자인 패턴
	- [TESTING_STRATEGY.md](implementation/TESTING_STRATEGY.md) - 테스트 전략

### 7. 요구사항

- [requirements/](requirements/) - 비즈니스 요구사항
	- [BUSINESS_REQUIREMENTS.md](requirements/BUSINESS_REQUIREMENTS.md) - 비즈니스 요구사항
	- [REFUND_POLICY.md](requirements/REFUND_POLICY.md) - 환불 정책 상세

---

## 빠른 시작

### 처음 읽으시는 분

1. [INFO.md](INFO.md) - 프로젝트 이해
2. [ARCHITECTURE.md](ARCHITECTURE.md) - 전체 구조 파악
3. [API_SPECIFICATION.md](API_SPECIFICATION.md) - API 사용법

### 개발자

1. [PROJECT_SETUP.md](PROJECT_SETUP.md) - 환경 설정
2. [implementation/DOMAIN_MODEL.md](implementation/DOMAIN_MODEL.md) - 도메인 모델
3. [implementation/DESIGN_PATTERNS.md](implementation/DESIGN_PATTERNS.md) - 패턴 적용

### 아키텍트

1. [adr/](adr/) - 아키텍처 결정 기록
2. [architecture/](architecture/) - 아키텍처 상세
3. [ARCHITECTURE.md](ARCHITECTURE.md) - 전체 구조

---

**최종 업데이트**: 2025-11-23
**작성자**: TeamBind
