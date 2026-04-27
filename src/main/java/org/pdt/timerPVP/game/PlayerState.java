package org.pdt.timerPVP.game;

/**
 * 게임 내 플레이어 상태를 나타내는 열거형
 */
public enum PlayerState {
    /** 게임에 참여하지 않은 일반 플레이어 */
    NONE,
    /** 게임 참가자 */
    PARTICIPANT,
    /** 관전자 */
    SPECTATOR,
    /** 전투에서 사망하여 관전자로 전환된 플레이어 */
    ELIMINATED
}
