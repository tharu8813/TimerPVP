# TImer PVP
[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://www.oracle.com/kr/java/technologies/downloads/)
[![Paper](https://img.shields.io/badge/Paper-1.21.4%2B-brightgreen)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-GPL--3.0-lightgrey)](LICENSE.txt)
[![GitHub release](https://img.shields.io/github/v/release/tharu8813/TImerPVP)](https://github.com/tharu8813/TimerPVP/releases/latest)

[이루의 마인크래프트](https://www.youtube.com/watch?v=szIsdCTHgh8&pp=ygUT66eI7YGsIO2DgOydtOuouHB2cA%3D%3D)에서 영감을 받아 제작된 TImer PVP를 플러그인을 통해 즐겨보세요!

## 버전
현재 지원중인 버전은 `26.1.2` 입니다. 추후에 마인크래프트 버전이 올라간다면 되도록 바로바로 버전을 올릴려고 합니다, 참고하세요.

그리고 한 버전만 계속 업데이트 할 예정입니다. 나중에 구버전 사용시 참고하세요.

**Spigot은 지원하지 않습니다. Paper을 이용해주세요.**

## 게임 설명
모든 플레이어가 또다른 월드에서 야생을 통해 n분 동안 아이템을 파밍해서 최종적으로 맞짱을 까는 미니게임입니다!

## 진행
1. 게임 시작시 월드를 생성하고 모든 플레이어를 해당 원드에서 랜덤한 위치로 이동합니다.
2. 설정한 시간이 지나면 모든 플레이어를 정비장으로 이동합니다.
3. 정비장에서 건너뛰너가 시간이 모두 지나면 경기장으로 이동합니다.
4. 카운트 다운이 끝나면 PVP가 시작됩니다.
5. 진행되는 도중 시간이 지날때마다 오버타임이 시작됩니다.
6. 마지막으로 살아남은 플레이어가 승리합니다.

## 명령어
*   `/tf start`: 모든 플레이어를 참가자로 게임을 시작합니다.
*   `/tf stop`: 게임을 강제종료 합니다.
*   `/tf sp <player>`: `<player>`에게 관전자 상태를 토글합니다.
*   `/tf vote`: 준비단계에서 건너뛰기 투표를 합니다.
*   `/tf status`: 현재 게임 상태를 확인합니다.
*   `/tf reload`: 플러그인을 리로드합니다.
*   `/tf debug [...]`: 디버그 명령어를 실행합니다.
    *   `check`: 모든 플레이어 상태 확인
    *   `spectator`: 자신을 관전자로 변경
    *   `spectator_reset`: 자신을 참가자로 변경
    *   `game_reset`: 게임 상태 초기화
    *   `del_world`: 파밍 월드 강제 삭제
    *   `set_time [분] [초]`: 타이머 설정 (기본 13분)
    *   `skip_time`: 타이머를 3초로 설정
    *   `game_stop`: 게임 강제 종료
    *   `time_stop`: 타이머 정지
    *   `state`: 현재 게임 상태 출력
