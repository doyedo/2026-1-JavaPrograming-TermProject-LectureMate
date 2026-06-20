# LectureMate

LectureMate는 Java 17, JavaFX, SQLite로 만든 데스크톱 학습 노트 앱입니다. 강의 녹음 파일을 첨부해 음성 원문을 만들고, 원문을 기반으로 자동 요약을 생성한 뒤 사용자가 최종 정리 노트로 직접 편집해 저장할 수 있습니다. 강의별 복습, 과제, 시험 일정도 월간 캘린더와 리스트로 관리합니다.

## 주요 기능

- 강의 생성, 조회, 선택, 삭제
- 강의 제목, 과목명, 생성일 표시
- mp3, wav, m4a 녹음 파일 선택
- 마이크로 직접 녹음, 일시정지, 다시 시작 후 WAV 파일로 첨부
- 강의 목록에서 첨부된 녹음 파일 재생 바, 시간 표시, 재생 및 정지
- 25MB 초과 파일 경고
- 녹음 언어, STT 모델, 요약 형식 선택
- OpenAI Audio Transcriptions API 연동 구조
- OpenAI Responses API 기반 요약 생성 구조
- 현재 열린 노트 내용을 기반으로 하는 AI 질문하기
- API Key 없음 또는 API 호출 실패 시 fallback 샘플 원문/요약 표시
- 원문 텍스트 직접 수정 및 `lectures.transcript` 저장
- 자동 요약을 편집해 최종 정리 노트로 저장
- 강의 목록 탭에서 원문 텍스트와 정리 노트를 크게 열어 편집
- 정리 노트 검색, 글자 크기/폰트 변경
- 강의별 일정 추가, 날짜 필터링, 완료 처리, 삭제
- 완료된 일정은 `is_done = 1`로 저장하고 기본 목록에서는 숨김
- SQLite 테이블 자동 생성 및 기존 간단 스키마 마이그레이션

## 프로젝트 구조

```text
src/main/java/com/lecturemate
├─ MainApp.java
├─ controller/MainController.java
├─ model/
│  ├─ Lecture.java
│  ├─ Note.java
│  └─ Schedule.java
├─ dao/
│  ├─ DatabaseManager.java
│  ├─ LectureDAO.java
│  ├─ NoteDAO.java
│  └─ ScheduleDAO.java
├─ service/
│  ├─ AudioTranscriptionService.java
│  ├─ AudioRecordingService.java
│  ├─ LectureQuestionService.java
│  ├─ LectureSummaryService.java
│  └─ ScheduleSuggestionService.java
└─ util/
   ├─ ApiConfig.java
   ├─ DateUtil.java
   └─ FileValidator.java

src/main/resources
├─ main-view.fxml
└─ styles.css
```

`Note`, `NoteDAO`는 이전 구현과의 호환을 위해 남겨둘 수 있지만, 현재 UI의 직접 필기 기능은 `lectures.summary`를 편집하는 방식으로 통합되어 있습니다.

## 실행 방법

Java 17과 Maven이 필요합니다.

```powershell
mvn clean javafx:run
```

이 작업공간에 설치된 Maven을 바로 사용할 수도 있습니다.

```powershell
& "C:\Users\cyd12\Documents\Codex\tools\apache-maven-3.9.16\bin\mvn.cmd" clean javafx:run
```

컴파일만 확인하려면 다음 명령을 사용합니다.

```powershell
mvn clean compile
```

## API Key 설정

API Key는 코드에 저장하지 않습니다. 앱은 환경 변수 `OPENAI_API_KEY`에서 키를 읽습니다.

PowerShell 현재 세션에서만 설정:

```powershell
$env:OPENAI_API_KEY="sk-your-api-key"
mvn clean javafx:run
```

Windows 사용자 환경 변수로 저장:

```powershell
setx OPENAI_API_KEY "sk-your-api-key"
```

`setx` 사용 후에는 새 PowerShell 창을 열어야 반영됩니다.

요약 모델은 기본값으로 `gpt-4.1-mini`를 사용합니다. 필요하면 `OPENAI_SUMMARY_MODEL`로 변경할 수 있습니다.

```powershell
$env:OPENAI_SUMMARY_MODEL="gpt-4.1-mini"
```

## fallback 모드

`OPENAI_API_KEY`가 없거나 API 호출이 실패해도 앱은 종료되지 않습니다.

- 텍스트 변환 실패 시 샘플 강의 원문을 표시합니다.
- 요약 생성 실패 시 규칙 기반 샘플 요약을 표시합니다.
- 429 오류는 보통 사용량 한도, 결제/크레딧, rate limit 문제입니다. 이 경우에도 앱은 fallback 결과로 시연할 수 있습니다.

## 사용 방법

1. `음성 변환` 탭에서 강의 제목과 과목명을 입력하고 `새 강의`를 누릅니다.
2. 생성된 강의를 선택합니다.
3. 왼쪽 `변환 설정` 카드에서 `파일`로 mp3, wav, m4a 파일을 첨부하거나 `녹음`, `일시정지`, `중지`로 직접 녹음합니다.
4. 녹음 언어, 음성 인식 모델, 자동 요약 형식을 선택합니다.
5. `텍스트 변환`을 누르면 원문 텍스트 영역에 결과가 표시되고 저장됩니다.
6. `요약 생성`을 누르면 오른쪽 정리 노트 영역에 요약이 표시됩니다.
7. 정리 노트를 직접 수정한 뒤 `정리 노트 저장`을 누릅니다.
8. `강의 목록` 탭에서 원문 텍스트 또는 최종 정리 노트를 선택해 큰 편집 영역에서 수정하고, 첨부된 녹음 파일을 재생할 수 있습니다.
9. `질문하기` 탭에서 현재 열린 노트 내용을 기반으로 AI에게 질문할 수 있습니다.
10. `일정 관리` 탭에서 날짜를 선택하고 `일정 추가`로 복습, 과제, 시험, 기타 일정을 등록합니다.
11. 일정 체크박스를 누르면 완료 처리되어 목록과 캘린더 강조 표시에서 즉시 사라집니다.

## 화면 구성

- 상단: 앱 제목, 작업 상태, ProgressIndicator, 성공/실패 메시지
- 좌측 `음성 변환` 탭: 새 강의 생성, 파일 선택, 직접 녹음, 녹음 언어, STT 모델, 요약 형식, 변환/요약 버튼
- 좌측 `강의 목록` 탭: 원문 텍스트 목록, 최종 정리 노트 목록, 녹음 파일 재생, 큰 편집 영역 연동
- 좌측 `일정 관리` 탭: 월간 캘린더, 날짜 필터, 미완료 일정 목록
- 좌측 `질문하기` 탭: 현재 열린 노트 기반 AI 대화
- 메인 중앙 카드: 원문 텍스트 조회 및 수정
- 메인 우측 카드: 자동 요약 및 최종 정리 노트 편집

## 데이터베이스

앱 실행 시 작업 디렉터리에 `lecturemate.db`가 생성됩니다. 없으면 다음 테이블을 자동 생성합니다.

- `lectures`
- `notes`
- `schedules`

일정 완료는 삭제가 아니라 `schedules.is_done = 1` 업데이트로 처리합니다.
