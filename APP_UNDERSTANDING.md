# Shortcut Animator Android App 이해 정리

## 앱의 큰 목적

이 앱은 단순한 키보드 앱이 아니라, 아래 2가지를 결합한 안드로이드 앱으로 이해하고 있습니다.

1. `Shortcut / Text Expander` 기능
2. `Colemak 키보드 경로 시각화(traction / animation)` 기능

즉, 사용자가 저장한 shortcut를 다른 입력 환경에서 빠르게 확장해서 쓰게 해주면서, 동시에 그 shortcut가 Colemak 자판에서 어떤 손가락 이동 경로를 가지는지 시각적으로 보여주는 앱입니다.

---

## 핵심 구성

앱은 크게 `2개의 실행 맥락`을 가집니다.

### 1. 앱을 직접 실행했을 때

이 경우는 `DB 관리 화면`이 열려야 합니다.

이 DB 관리 화면의 목적은:

- 카테고리 폴더 만들기
- 각 폴더 안에 shortcut와 full sentence 저장하기
- 저장된 shortcut 목록 보기
- shortcut 수정 / 삭제하기
- full sentence를 활용한 영어 예문 자동 생성하기
- 내가 직접 영어문장 / 한국어 번역문장 예문 추가하기

즉, 앱 실행 화면은 `데이터를 만들고 관리하는 백오피스` 역할입니다.

### 2. 휴대폰 입력창에서 우리 키보드를 선택했을 때

이 경우는 `IME 키보드 화면`이 열려야 합니다.

이 키보드 화면의 목적은:

- 영어 입력 시 Colemak 배열로 타이핑
- 한글 입력 시 2벌식 한글 키보드로 타이핑
- shortcut를 입력하면 expands to 문장으로 확장
- shortcut 입력 흐름을 Colemak preview에서 시각화
- ON / LINK / OFF 모드로 traction 표시 수준 조절
- 색상 옵션 변경
- `+` 버튼으로 DB 앱 화면으로 이동

즉, 키보드 화면은 `실제 입력 도구` 역할입니다.

---

## 사용자가 원하는 DB 화면 구조

제가 이해한 DB 구조는 `3단계`입니다.

### 1단계. 폴더 목록 화면

이 화면은 shortcut들을 카테고리별로 정리하는 `상위 폴더 화면`입니다.

여기서 보이는 것:

- 폴더명
- 그 폴더 안에 들어 있는 shortcut 개수
- 우측 화살표 또는 꺽쇠
- 우하단의 새 폴더 추가 버튼

이 화면의 목적:

- 예: `부사구`, `명사구`, `시간 부사구` 같은 카테고리 단위로 분류

### 2단계. 폴더 추가 화면

새 폴더를 만들 때 들어가는 화면입니다.

여기서 필요한 것:

- 폴더 제목
- 선택사항인 참고 메모

이 화면은 단순해야 하고, 복잡한 설정은 핵심이 아닙니다.

### 3단계. 폴더 하위 shortcut list 화면

폴더 하나를 눌러 들어가면 그 폴더 안에 속한 shortcut들이 리스트로 보여야 합니다.

각 카드에 보이는 것:

- 번호
- shortcut 이름
- full sentence 일부 또는 요약
- 우측 화살표

이 화살표를 누르면 `상세 / 편집 화면`으로 들어가야 합니다.

---

## 사용자가 원하는 상세 / 편집 화면

이 화면은 첨부하신 `Shortcut Animator` 예시 이미지와 최대한 유사해야 합니다.

필수 구성 요소:

1. 상단 제목: `Shortcut Animator`
2. 설명 문구: shortcut를 입력하면 Colemak 경로가 애니메이션된다는 안내
3. `Shortcut` 입력창
4. `Expands to` 입력창
5. `SAVE SHORTCUT` 버튼
6. `Colemak preview` 영역
7. preview 상태 문구
8. 이미 저장된 shortcut들의 `Saved shortcuts` 리스트
9. 각 저장 항목의 `Edit / Delete`

즉, 이 화면은:

- shortcut 저장
- shortcut 수정
- Colemak preview 확인
- 저장 목록 확인

을 한 화면 안에서 할 수 있어야 합니다.

---

## Colemak preview / traction 기능에 대한 이해

이 기능은 이 앱의 차별점입니다.

예를 들어 `mike`를 입력하면:

1. `m` 키가 Colemak 자판 위치에서 1초 동안 밝게 빛남
2. `m`이 꺼짐
3. `i` 키가 1초 동안 밝게 빛남
4. `i`가 꺼짐
5. `k` 키가 1초 동안 밝게 빛남
6. `k`가 꺼짐
7. `e` 키가 1초 동안 밝게 빛남
8. `e`가 꺼짐
9. 마지막에 `m -> i -> k -> e` 순서를 화살표 선으로 이어서 보여줌

즉, 요구사항의 핵심은:

- `동시 점등`이 아니라 `순차 점등`
- 각 글자는 `밝아졌다가 꺼져야 함`
- 마지막에만 `전체 경로 화살표`가 보여야 함

---

## shortcut 확장 기능에 대한 이해

이 앱은 단순 저장 앱이 아니라 실제 확장 기능이 중요합니다.

핵심 동작:

- 사용자가 shortcut와 expands to를 저장
- 입력 중 shortcut를 치면
- 조건이 맞을 때 expands to가 입력되거나 후보로 제안됨

플랫폼별로 약간 방식이 다를 수 있지만, 안드로이드 버전에서는 기본적으로:

- IME 키보드 내부에서 shortcut 매칭
- 다른 앱 / 브라우저 / 메시지 입력창에서도 동작할 수 있도록 설계

이 방향을 목표로 하고 있습니다.

---

## 키보드 자체에 대해 이해한 내용

### 영어 키보드

- Colemak 배열이어야 함
- punctuation 포함
- preview와 실제 입력 키보드가 서로 맞아야 함

### 한글 키보드

- 2벌식 한글 키보드여야 함
- shift 동작 포함
- 특수문자 / 숫자 화면 포함
- 한글 모드일 때는 상단 traction preview가 접히거나 사라질 수 있음

### 상단 제어 버튼

- `ON`: 입력할 때마다 traction 표시
- `LINK`: 저장된 shortcut와 맞는 경우 중심으로 표시
- `OFF`: 상단 traction을 접고 키보드만 크게 사용
- 색상 버튼: 키보드와 preview 테마 변경
- `+` 버튼: DB 관리 앱 화면으로 이동

---

## 데이터 구조에 대한 이해

현재 의도하는 저장 구조는 대략 아래와 같습니다.

- Folder
  - title
  - note
  - shortcuts[]

- ShortcutEntry
  - shortcut
  - expandsTo
  - usageCount
  - note
  - examples[]
  - caseSensitive
  - backspaceToUndo

- ExampleItem
  - english
  - korean
  - sourceType(auto/manual)

즉:

- 폴더는 상위 카테고리
- 그 안에 shortcut list
- 각 shortcut에는 예문과 옵션이 달림

---

## 현재까지 제가 이해한 우선순위

이 앱에서 가장 중요한 기능 우선순위는 아래와 같습니다.

1. `키보드 화면`이 정상적으로 떠야 함
2. `Colemak preview 애니메이션`이 정확히 동작해야 함
3. `shortcut 저장 / 수정 / 삭제`가 안정적으로 되어야 함
4. `폴더 -> shortcut list -> 상세 화면` 이동이 안정적이어야 함
5. `saved DB가 꼬여서 화면 동작이 이상해지는 문제`가 없어야 함
6. `색상 옵션`, `한글/영문 전환`, `예문 자동 생성` 등 부가기능이 자연스럽게 붙어야 함

---

## 한 줄 요약

이 앱은

`Colemak 경로 애니메이션이 붙은 shortcut expander 키보드 + 폴더형 DB 관리 앱`

으로 이해하고 있습니다.

앱 실행 시에는 폴더와 shortcut DB를 관리하고,
키보드 실행 시에는 실제 입력과 shortcut expansion, 그리고 Colemak traction 시각화를 제공하는 것이 핵심입니다.
