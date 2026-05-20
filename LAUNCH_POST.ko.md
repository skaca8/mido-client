# 외부 API 30곳 연동에 지쳐서, 결국 라이브러리를 만들었다 — `mido-client`

> Spring Boot 3.2+ 환경에서 **여러 외부 API 채널을 YAML만으로 정의·관리**하는 RestClient 래퍼.
> `@Bean` 대량 생산, OpenFeign 인터페이스 사슬, 사라진 charset, 들쭉날쭉한 로그에 지친 분들 환영.
>
> Maven Central: `io.github.skaca8:mido-client:1.1.0`
> GitHub: https://github.com/skaca8/mido-client

---

## 시작은 OTA였다

저는 여행업 IT에서 오래 일했습니다. 여행 도메인의 특수성 하나만 꼽으라면 — **외부 API 통신이 비즈니스의 전부**라는 점이에요.

여행 플랫폼은 자체 재고가 거의 없습니다. 호텔, 항공, 액티비티 — 거의 모든 상품은 OTA(Online Travel Agency)·공급사(부킹홀딩스, 익스피디아 그룹, GDS, 국내 PMS 등)와 **실시간 API 통신**으로 가져옵니다.

그래서 한 프로덕트에 외부 API 채널이 **10곳~수십 곳** 붙는 게 기본이에요.

이게 끝이 아니죠. 같은 OTA 안에서도:

- **조회용 host**와 **예약용 host**가 분리된 경우가 흔함 (`api.partner.com/search` vs `booking.partner.com/reserve`)
- 두 endpoint의 **타임아웃 정책이 다름** — 조회는 빠르게 끊어야 하고, 예약은 느려도 기다려야 함
- 인증 토큰도 endpoint별로 따로 발급되는 경우가 있음

즉 **OTA 1곳 = 채널 2~3개**가 평균입니다. 결국 한 프로덕트가 운영하는 RestClient 인스턴스가 30~50개가 됩니다.

## 처음에는 `@Bean`을 무한 복제했다

가장 흔히 본 패턴은 이거였어요:

```java
@Configuration
public class HotelBedsConfig {

    @Bean
    public RestClient hotelBedsSearchClient() {
        return RestClient.builder()
                .baseUrl("https://api.hotelbeds.com/search")
                .requestFactory(createFactory(3, 10))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + System.getenv("HB_SEARCH_TOKEN"))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .messageConverters(this::configureConverters)
                .requestInterceptors(this::configureInterceptors)
                .build();
    }

    @Bean
    public RestClient hotelBedsBookingClient() {
        return RestClient.builder()
                .baseUrl("https://api.hotelbeds.com/booking")
                .requestFactory(createFactory(5, 60))  // 예약은 더 길게
                ...
    }

    @Bean public RestClient expediaSearchClient()    { ... }
    @Bean public RestClient expediaBookingClient()   { ... }
    @Bean public RestClient bookingComSearchClient() { ... }
    // 끝없는 반복...
}
```

당연히 메서드 추출도 해봤어요. private 헬퍼로 공통 로직 뽑고, 토큰은 외부에서 주입받고, 타임아웃은 enum으로 관리하고… 그래도 남는 문제들:

- **설정 정보가 두 곳에 흩어짐** — `application.yml`엔 token만 있고, 나머지 (URL, 타임아웃, 헤더, 인터셉터)는 Java config 어딘가에 박혀 있음
- **OTA 추가 = 코드 수정 + 빌드 + 배포** — 운영 중 OTA 신규 연동·중단은 자주 있는 이벤트인데 매번 PR
- **로깅 표준화의 어려움** — 어떤 채널은 인터셉터 붙이고, 어떤 채널은 빠뜨림. 디버깅할 때 채널마다 로그 포맷이 다르게 찍혀서 미치겠음
- **boilerplate 비용** — OTA 30곳 + 채널 60개면 Java config가 1000줄 넘게 부풀어 오름

## OpenFeign? 우리 케이스엔 맞지 않았다

자연스럽게 OpenFeign을 검토했습니다. 마이크로서비스 진영의 사실상 표준이니까요. 다만 **우리 도메인엔 잘 안 맞았어요.** 이유를 정리하면:

### 1. OTA마다 응답 스키마가 너무 다르다

OpenFeign의 핵심 가치는 **타입 안전한 인터페이스 정의**입니다. 응답 DTO를 잘 정의하면 빛나죠.

근데 OTA들은:

- A사: `{"hotels": [...], "totalCount": 50}` (JSON)
- B사: `<HotelList><Hotel>...</Hotel></HotelList>` (XML)
- C사: `{"data": {"items": [...]}, "meta": {...}}` (또 다른 wrapping)
- D사: 환경(staging/prod)마다 응답 구조가 미묘하게 다름

이걸 다 Feign 인터페이스로 정의하려면 OTA별로 인터페이스 10개, 메서드 100개가 나오고, OTA가 스펙을 살짝만 바꿔도 `ClassCastException` 시한폭탄이 됩니다.

저희가 원했던 패턴은 — **String body로 일단 받고, 도메인 매퍼에서 우리 모델로 변환**. Feign으로도 가능하긴 한데, 그러면 Feign의 강점인 타입 안전을 다 버리는 셈이에요. **그럴 거면 굳이 Feign?**

### 2. 채널당 듀얼 endpoint 표현이 어색하다

조회용 host와 예약용 host가 분리된 OTA를 Feign으로 표현하면:

```java
@FeignClient(name = "hotelbedsSearch",  url = "https://api.hotelbeds.com/search")
public interface HotelBedsSearchClient { ... }

@FeignClient(name = "hotelbedsBooking", url = "https://api.hotelbeds.com/booking")
public interface HotelBedsBookingClient { ... }
```

→ OTA 1곳에 인터페이스 2개. OTA 30곳이면 인터페이스 60개. "이 클라이언트가 hotelbeds 거였나? expedia 거였나?" 헷갈리기 시작. 그리고 두 client가 같은 OTA에 속한다는 의미론적 묶음을 잃습니다.

### 3. 인코딩·content-type 처리에 손이 많이 간다

국내·일본 OTA는 의외로 비-UTF-8 charset이 살아 있습니다 (EUC-KR, Shift-JIS). 또 일부 legacy OTA는 여전히 XML/SOAP을 씁니다. Feign에서 이걸 채널별로 다르게 처리하려면 채널마다 `FeignClientConfiguration` 클래스를 따로 만들어야 해요. 가능은 하지만 **boilerplate가 다시 늘어남.**

### 4. YAML로 커스텀 인터셉터를 등록하기 까다롭다

운영 중 인터셉터 교체·추가가 잦은데, Feign은 `Configuration` 클래스로 끼우는 구조 → 빌드/배포 필요. "YAML에 클래스명만 적으면 끝나는 인터셉터 등록"이 우리에겐 필요했어요.

### 5. 로깅 정책 표준화

Feign의 `Logger.Level`은 3단계 (`BASIC`, `HEADERS`, `FULL`). 우리에겐 **콘솔/파일/둘 다**의 4단계 분리가 필요했어요. 채널마다 콘솔만 보고 싶고, 어떤 채널은 파일에만 남기고 싶고… 이 정책을 매번 코딩하는 게 비효율적.

---

정리하면 OpenFeign은 **"공통 스펙의 마이크로서비스끼리 통신할 때"** 강력하지만, **"제각각의 외부 시스템과 통신할 때"**는 strength가 매칭되지 않습니다.

## 다른 OSS도 찾아봤지만 입맛에 안 맞았다

- **Retrofit** — Android 진영 표준. Spring Boot 통합이 약하고, 인터페이스 사슬 문제는 동일
- **Apache HttpClient / OkHttp** — 너무 low-level. 우리가 만들고 싶은 게 이거의 wrapper
- **Spring 6.1 RestClient (vanilla)** — 좋아졌지만 멀티채널 관리는 여전히 손으로 해야 함

저희가 찾던 건 한 줄로 표현하면:

> **"YAML에 채널 N개 정의하면, 알아서 RestClient 만들어주고, 로깅·인증·인코딩·content-type 같은 운영 관심사를 자동으로 처리해주는 라이브러리"**

이게 없었어요. **그래서 만들었습니다.**

## `mido-client`

[![Maven Central](https://img.shields.io/maven-central/v/io.github.skaca8/mido-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.skaca8/mido-client)

> Spring Boot 3.2+ 기반의 **YAML-only multi-channel RestClient** 라이브러리

### 한 줄 가치

> **YAML에 채널만 정의하면, RestClient 인스턴스 + 로깅 + 인증 + gzip + 인코딩 + content-type을 자동으로 처리해주는 Spring Boot 3.2+ 라이브러리.**

### 사용법 — 30초 안에 감 잡기

**`application.yml`:**

```yaml
mido-client:
  enabled: true
  channels:
    hotelbeds:
      title: "Hotelbeds OTA"
      charset: UTF-8
      type: json
      primary:                                  # 조회용 endpoint
        url: https://api.hotelbeds.com/search
        read-timeout-seconds: 10
        connect-timeout-seconds: 3
        authorization:
          type: bearer
          token: ${HB_SEARCH_TOKEN}
        log: console
      secondary:                                # 예약용 endpoint
        url: https://api.hotelbeds.com/booking
        read-timeout-seconds: 60                # 예약은 길게
        connect-timeout-seconds: 5
        authorization:
          type: bearer
          token: ${HB_BOOK_TOKEN}
        log: all                                # 콘솔 + 파일 동시
    legacyKoreanOta:
      charset: EUC-KR                           # 국내 legacy OTA 인코딩
      type: xml                                 # XML 응답
      primary:
        url: https://legacy.partner.co.kr/api
        log: file
```

**서비스 코드:**

```java
@Service
public class HotelBedsService extends BaseExternalApi {

    private final RestClient searchClient;
    private final RestClient bookingClient;

    public HotelBedsService(MidoClientFactory factory) {
        this.searchClient  = factory.getOrCreateClient("hotelbeds");
        this.bookingClient = factory.getOrCreateClient("hotelbeds", EndpointType.SECONDARY);
    }

    @Override
    protected String getChannelName() {
        return "hotelbeds";
    }

    public String searchHotels(String city, LocalDate checkIn) {
        return withDefaultChannelAction("searchHotels", () ->
                searchClient.get()
                        .uri("/hotels?city={city}&date={date}", city, checkIn)
                        .retrieve()
                        .body(String.class)
        );
    }

    public String reserve(String reservationXml) {
        return withDefaultChannelAction("reserve", () ->
                bookingClient.post()
                        .uri("/reservations")
                        .body(reservationXml)
                        .retrieve()
                        .body(String.class)
        );
    }
}
```

`@Bean` 0개. `@Configuration` 0개. 채널 정의는 YAML에서 끝.

### 주요 기능

- **멀티채널 + 듀얼 endpoint** — 채널마다 `primary` / `secondary` 두 endpoint 정의 가능, 한 채널 안에서 의미론적 묶음 유지
- **4단계 빌트인 로깅** — `off` / `console` / `file` / `all` (콘솔+파일 동시). 응답 시간, body, URL, HTTP status 자동 포함
- **endpoint별 인증** — Bearer / Basic / API Key
- **스마트 charset 감지** — Content-Type 헤더 → UTF-8 유효성 검사 → 채널 기본값 순으로 fallback. EUC-KR / Shift-JIS도 자연스럽게 처리
- **채널별 gzip** — 요청 압축 (`min-size` 임계값 미만은 skip), 응답 자동 해제 + 압축 폭탄 방어 cap (`max-decompressed-size`)
- **채널별 content type** — `json` (기본) / `xml`, 요청 `Content-Type` 헤더 자동 설정
- **YAML로 커스텀 인터셉터 등록** — 클래스명만 적으면 됨. 빌드/배포 없이 운영 중 교체 용이
- **ChannelContext + MDC** — 스레드 로컬 채널 액션 추적, SLF4J MDC 자동 통합으로 분산 로그 트레이싱 가능
- **부팅 시 설정 검증** — `@Validated` Bean Validation으로 잘못된 YAML을 시작 시점에 거부. 오타가 prod까지 가지 않음

### 의도적으로 안 넣은 것 — 회복성(Circuit Breaker / Rate Limiter / Retry)

이 부분은 솔직하게 짚고 갈게요. **`mido-client` 본체에는 회복성 레이어가 없습니다.** 이유:

- Resilience4j vs Sentinel vs Failsafe — 팀마다 선호 차이가 큼
- Resilience4j 자체가 노브가 많아서 YAML로 평탄화하면 `mido-client` config가 두 배로 비대해짐
- `mido-client`는 "채널 구성"에 집중. 회복성은 직교(orthogonal) 관심사

대신 README에 [Resilience4j 통합 레시피](https://github.com/skaca8/mido-client/blob/master/README.ko.md#%ED%9A%8C%EB%B3%B5%EC%84%B1-rate-limiter--circuit-breaker--retry)를 박아뒀습니다. **10줄짜리 인터셉터 클래스 + YAML 한 줄**로 채널에 매답니다.

```yaml
interceptors:
  - "com.yourapp.interceptor.HotelBedsResilienceInterceptor"
```

향후 본체에 회복성을 통합할지는 사용자분들 피드백을 보고 결정할 생각이에요. 통합한다고 해도 본체에 직접 넣기보다는, `mido-client-resilience4j` 같은 **별도 starter 모듈로 분리**하는 방향을 1순위로 보고 있습니다.

## 시작하기

**Gradle:**
```gradle
implementation 'io.github.skaca8:mido-client:1.1.0'
```

**Maven:**
```xml
<dependency>
    <groupId>io.github.skaca8</groupId>
    <artifactId>mido-client</artifactId>
    <version>1.1.0</version>
</dependency>
```

`application.yml`에 `mido-client.enabled: true` 추가하면 끝.

자세한 사용법은 [README](https://github.com/skaca8/mido-client/blob/master/README.ko.md) 참고.

## 누구에게 적합한가

### ✅ 적합

- 외부 API를 **5개 이상** 붙이는 Spring Boot 3.2+ 프로덕트 (여행, 핀테크, B2B 미들웨어, 게임 결제, 헬스케어 등)
- 응답 스키마가 OTA·공급사마다 제각각이라 **String body 받아 도메인 매퍼로 변환하는 패턴**을 쓰는 팀
- "OpenFeign의 타입 안전이 우리 도메인엔 과함" 느끼는 팀
- YAML-only config 철학을 선호하는 팀
- 운영 중 채널 추가·제거·인터셉터 교체가 잦은 팀

### ❌ 안 맞을 수 있음

- 마이크로서비스 service-to-service 통신 (이 경우 OpenFeign + Spring Cloud LoadBalancer 권장)
- 글로벌 엔터프라이즈 환경에서 Resilience 깊은 통합이 필수인 경우 (현재는 BYO 방식)
- 응답을 강하게 타입화한 DTO로 다루는 게 정책인 팀

## 마치며

작은 라이브러리지만 **여행업 현장에서 OTA·공급사 수십 곳을 운영하며 깎아낸 패턴**을 압축했습니다. 같은 페인 포인트를 가진 분이 있다면 시도해보시고, 피드백 환영합니다.

- ⭐ [GitHub repo](https://github.com/skaca8/mido-client)
- 🐛 Issue / PR 환영
- 💬 의견 있으시면 댓글로 남겨주세요

같은 도메인에서 일하시는 분들과 케이스 공유하고 싶어요. "우리는 이렇게 해결했다" 같은 댓글이면 더 좋습니다.

---

### P.S. `mido`는 누구인가요

라이브러리 이름 `mido`는 — 제가 **13년째 함께 살고 있는 말티즈의 이름**입니다. 썸네일의 그 친구예요.

이름 짓다가 막혀서 옆에서 자고 있던 미도를 봤더니 그냥 그게 답 같더라고요. `spring-multi-channel-rest-client` 같은 정직한 이름보다 외우기도 쉽고, 무엇보다 `getOrCreateClient("hotelbeds")` 같은 코드를 짤 때 살짝 따뜻한 기분이 듭니다.

라이브러리 안정성은 미도가 옆에서 지켜보는 만큼 보장한다는 마음으로 짰습니다. (장난입니다만, 진심도 약간 섞여 있어요)
🐶