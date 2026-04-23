# Colemak Shortcut Traction

**패키지**: `com.kore2.shortcutime`  
**버전**: 1.0 (개발 중)  
**플랫폼**: Android  

---

## 앱 개요

Colemak 키보드 배열을 기반으로 한 **단축어 확장 IME(입력기)** 앱.  
짧은 단축어를 입력하면 미리 저장된 긴 문장으로 자동 확장되며, 영어 예문과 한국어 번역을 함께 관리할 수 있다.

---

## 주요 기능

### 1. Colemak IME 키보드
- Colemak 배열 커스텀 키보드
- 단축어 입력 시 Colemak 경로 애니메이션 시각화 (`ColemakPreviewView`)
- 테마별 키보드 색상 변경 버튼 (IME 내 색상 토글)
- 한글/영어 전환 지원

### 2. 단축어(Shortcut) 관리
- **폴더** 단위로 단축어 그룹화
- 단축어(Shortcut) → 확장 문장(Expands To) 매핑
- 단축어 입력 시 후보 자동 표시 및 탭으로 삽입

### 3. 예문(Example) 관리
- 수동 예문 추가 (영어 + 한국어 번역)
- AI 자동 예문 생성 (1개 / 3개 / 5개 선택)
- 예문 접기/펼치기 토글

### 4. AI 예문 생성 (BYOK)
- 지원 공급자: **OpenAI, Anthropic Claude, Google Gemini, xAI Grok, DeepSeek**
- 사용자 직접 API 키 입력 (앱 내 저장)
- 하루 호출 한도 설정
- 월별 AI 생성 횟수 무료 한도 관리

### 5. CSV 내보내기 (Pro 전용)
- 단축어의 수동/자동 예문 전체를 `.csv` 파일로 내보내기
- UTF-8 BOM 인코딩 (Excel 한글 호환)
- 컬럼: `type`, `english`, `korean`

### 6. 키보드 테마
- 10종 테마 지원 (무료: 3종, Pro: 10종 전체)
- 앱 전체 및 IME 키보드에 테마 동기간 적용
- 다크/라이트 테마 자동 대비 처리 (`isColorDark` WCAG 공식)

### 7. 스크롤바 (자동 표시)
- 폴더 카드 목록: 콘텐츠가 화면을 넘으면 자동 표시, 미사용 시 자동 페이드
- Shortcut 목록: 동일 방식
- 예문 목록: 동일 방식

### 8. Pro 업그레이드 (인앱결제)
- **일회성 결제** (평생 이용권) — ₩5,900
- 결제 SDK: **RevenueCat 9.23.1**
- Google Play 구독 상품: `pro_lifetime` (Prepaid)
- Pro 기능: 폴더 무제한 / Shortcut 무제한 / AI 생성 무제한 / CSV 내보내기 / 테마 10종 / 향후 신기능 우선 제공

---

## 화면 구성

| 화면 | 설명 |
|------|------|
| 폴더 목록 | 폴더 추가/삭제/수정, 각 폴더의 shortcut 수 표시 |
| Shortcut 목록 | 폴더 내 shortcut 목록, 매칭 횟수 표시 |
| Shortcut 편집기 | 단축어/확장문 입력, Colemak 미리보기, 예문 관리, AI 생성, CSV 내보내기 |
| AI 설정 | 공급자 선택, API 키 등록/검증, 모델 선택, 하루 한도 설정 |
| Pro 업그레이드 | 기능 비교 테이블, 구매 버튼, 구매 복원 |
| 설정 | AI 설정 접근, Pro 업그레이드 접근 |

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Kotlin |
| UI | Fragment + ViewBinding, Material Design 3 |
| 네비게이션 | Navigation Component |
| DB | Room (SQLite) |
| 결제 | RevenueCat SDK 9.23.1 |
| AI | OkHttp (REST API 직접 호출) |
| 빌드 | Gradle (AGP 8.x) |
| 최소 SDK | Android 7.0 (API 24) |

---

## 프리미엄(Pro) 제한 정책

| 기능 | Free | Pro |
|------|------|-----|
| 폴더 수 | 2개 | 무제한 |
| Shortcut 수 | 10개/폴더 | 무제한 |
| AI 예문 생성 | 월 20회 | 무제한 |
| CSV 내보내기 | ✕ | ✓ |
| 키보드 테마 | 3종 | 10종 |
| 향후 신기능 | ✕ | 우선 제공 |

---

## 빌드 정보

- **디버그 키스토어 SHA-256**:  
  `C3:5D:4E:29:42:09:60:DD:91:78:B9:81:97:12:66:1E:0E:99:80:A2:58:EA:03:D4:63:F2:63:47:CF:29:3D:14`
- **Google Play 패키지 인증**: 검토 중 (Android 개발자 인증)
- **RevenueCat 프로젝트**: 연결 완료 (테스트 키 적용 중)

---

## 남은 작업

- [ ] Google Play Console 패키지 인증 승인 대기
- [ ] Google Play 앱 만들기 (`com.kore2.shortcutime`)
- [ ] Google Play 구독 상품 등록 (`pro_lifetime`, ₩5,900)
- [ ] Google Play 서비스 계정 → RevenueCat 연결
- [ ] 샌드박스 계정으로 Pro 구매 테스트
- [ ] RevenueCat 테스트 키 → 프로덕션 키 교체
- [ ] 앱 스토어 등록 (스크린샷, 설명, 정책 등)
- [ ] 릴리즈 APK 서명 키스토어 생성 및 적용
