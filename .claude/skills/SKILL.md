# Stay-Hub Development Skills

Spring Boot 기반 숙박 예약 시스템 개발을 위한 Claude 세션 규칙과 스킬셋입니다.

## 핵심 개발 원칙

### 1. Plan-First Development (계획 우선 개발)

작업 전 반드시 계획을 수립하고 승인을 받습니다.

**규칙:**

- 항상 작업 계획을 먼저 제시하고 사용자 승인을 받는다
- 파일 수정 전 반드시 사용자 확인을 받는다
- 사용자가 방향과 핵심 결정을 내린다
- 추측하지 않고 확인을 요청한다

### 2. Spring-First Approach (Spring 우선 접근)

커스텀 구현보다 Spring 생태계를 우선 활용합니다.

**규칙:**

- 커스텀 구현 전에 Spring 내장 솔루션을 먼저 확인한다
- Spring 유틸리티 활용 (ObjectUtils, Collections 등)
- 불필요한 복잡성을 피한다

**예시:**

```java
// Good
if (ObjectUtils.isEmpty(marginRate)) return;

// Avoid
if (marginRate == null || marginRate.equals("")) return;
```

### 3. No Guessing Policy (추측 금지)

확실하지 않으면 확인부터 합니다.

**규칙:**

- 기능 확신이 없으면 문서를 확인한다
- 불확실할 때는 "확인해보겠습니다"라고 말한다
- Claude Code 질문시 WebFetch 사용

### 4. Preserve Working Solutions (동작 코드 보존)

성능 문제가 없는 동작 코드는 함부로 변경하지 않습니다.

**규칙:**

- "왜 동작하는 것을 바꿔야 하는가?" 먼저 질문한다
- 최적화 전에 측정한다

## 코딩 표준

### Clean Code Principles

**핵심 원칙:**

- 최소한의 중간 변수로 깔끔한 코드 작성
- 적절한 곳에 함수형 프로그래밍 패턴 적용
- 처음부터 Spring 유틸리티와 모범 사례 활용
- 시간 = 돈 - 낭비적인 반복 피하기

### Developer-Friendly Code (개발자 친화적 코드)

도메인 지식 없는 개발자도 이해할 수 있는 코드를 작성합니다.

**원칙:**

- 비즈니스 로직에서 명시적 코드 > 숨겨진 로직
- 확장성과 유지보수성의 균형
- 일관된 판단 기준 유지
- 팀 기술 성숙도를 고려한 현실적 설계

## 아키텍처 가이드라인

### Architecture Decision Making

**의사결정 기준:**

- "도메인 지식 없이 오류 추적이 가능한가?"
- 프로덕션에서 디버깅과 문제 해결 용이성 우선시
- 코드 흐름 추적시 명시적 경로 제공
- 외부 벤더별 응답 패턴 분리 (Alpensia, Sono, Hanwha 등)

### AOP Usage Guidelines

**적극 사용:**

- @Transactional, @Cacheable, 로깅, 보안 (횡단 관심사)

**신중 사용:**

- 비즈니스 검증, 데이터 변환, 응답 처리 (도메인 로직)

**판단 기준:**

- "이 로직을 숨기면 문제 해결이 어려워질까?"

**현실 고려사항:**

- 문서는 업데이트되지 않고, 팀원 변경시 흐름 추적이 어려워짐

## Stay-Hub 도메인 지식

### 모듈 구조

- **common**: 공통 모델, 유틸리티, 설정
- **domain**: 엔티티, 리포지토리, 도메인 로직
- **pms**: Property Management System - 호텔 관리
- **supplier**: 공급업체 연동

### 외부 벤더 연동

- **Alpensia**: 알펜시아 리조트 연동
- **Sono**: 소노 호텔 연동
- **Hanwha**: 한화 리조트 연동

### 주요 엔티티

- **ProductRatePlanPrice**: 요금제 가격 정보
- **ProductRoomStock**: 객실 재고 정보
- **RateplanCalcMarginRate**: 요금제 마진율 계산

### 배치 처리 설정

```java
public static final int ROOM_STOCK_BATCH_SIZE = 1000;
public static final int RATE_PLAN_BATCH_SIZE = 500;
```

## 개발 패턴

### Repository 패턴

**인터페이스:**

```java
@Mapper
public interface LogRepository {
    Integer insertStockSyncLogProduct(List<ProductRoomStock> productRoomStocks);
}
```

**XML 매퍼:**

```xml
<!--suppress SqlNoDataSourceInspection -->
<mapper namespace="com.allmytour.stayhub.domain.repository.allmytour.LogRepository">
    <!-- 쿼리 정의 -->
</mapper>
```

**테스트:**

```java
@MybatisTest
@Rollback
void insertStockSyncLogProductTest() {
    // 테스트 로직
}
```

### Entity 패턴

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRoomStock {
    // 필드 정의
}
```

### Service 패턴

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AlpensiaSchedulerService {

    private final AlpensiaExternalService alpensiaService;

    // 비즈니스 로직
}
```

## 에러 처리 모범 사례

### 로깅 실패 처리

```java
try {
    logRepository.insertStockSyncLogProduct(productRoomStocks);
} catch (Exception ignored) {
    // Ignore log insertion failures
}
```

**원칙:**

- 비즈니스 로직 실패와 로깅 실패 분리
- 로깅 실패는 메인 비즈니스 로직에 영향 없음
- 명확한 주석으로 의도 표현

### 배치 처리 에러 핸들링

```java
try {
    ratePlanRepository.saveProductRatePlanPrice(batch);
} catch (Exception e) {
    log.error("Failed to save rate plan prices batch: {}", e.getMessage(), e);
    continue; // 다음 배치 계속 처리
}
```

## 코드 품질 표준

### SonarLint & IntelliJ 준수

- 모든 코드는 SonarLint 분석을 위반 없이 통과
- IntelliJ IDEA 경고나 오류 제거
- 작업 완료 전 품질 검사 실행

### 상수 관리

```java
@UtilityClass
public class AlpensiaConstant {
    public static final int ROOM_STOCK_BATCH_SIZE = 1000;
    public static final int RATE_PLAN_BATCH_SIZE = 500;
}
```

## 사용 방법

이 스킬을 활용하여:

1. **계획 수립**: 작업 전 TodoWrite로 계획 작성
2. **코드 분석**: 기존 유사 파일 패턴 먼저 확인
3. **Spring 우선**: 커스텀 구현 전 Spring 솔루션 검토
4. **품질 검증**: SonarLint 및 IntelliJ 경고 해결
5. **도메인 적용**: Stay-Hub 특화 패턴 및 상수 활용

Stay-Hub 프로젝트의 일관성 있고 고품질의 코드 개발을 위해 이 가이드라인을 따라주세요.