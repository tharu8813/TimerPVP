package org.pdt.timerPVP.game;

/**
 * 게임 진행 단계를 나타내는 열거형
 */
public enum GameState {
    /** 게임이 진행되지 않는 대기 상태 */
    IDLE,
    /** 게임 시작 준비 중 (카운트다운) */
    STARTING,
    /** 1단계: 파밍 단계 (랜덤 생성 월드) */
    FARMING,
    /** 1단계 종료 후 2단계 전환 중 */
    TRANSITIONING_TO_PREP,
    /** 2단계: 준비 단계 (본 맵 준비 구역) */
    PREPARATION,
    /** 2단계 종료 후 3단계 전환 중 */
    TRANSITIONING_TO_PVP,
    /** 3단계: PVP 전투 단계 */
    PVP,
    /** 게임 종료 처리 중 */
    ENDING
}
