package org.pdt.timerPVP.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 효과음 재생 유틸리티 클래스
 * 원래 스크립트의 효과음.sk 파일 기능을 담당
 */
public final class SoundUtil {

    private SoundUtil() {}

    /** 전체 플레이어에게 효과음 재생 */
    public static void playAll(Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    /** 특정 플레이어에게 효과음 재생 */
    public static void play(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /** 위치 기준으로 주변 플레이어에게 효과음 재생 */
    public static void playAt(Location location, Sound sound, float volume, float pitch) {
        if (location.getWorld() == null) return;
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    // ============================================================
    // 효과음.sk 에서 변환된 개별 사운드 메서드
    // ============================================================

    /** sound_1: UI 클릭 소리 */
    public static void soundClick() {
        playAll(Sound.UI_BUTTON_CLICK, 100f, 1f);
    }

    /** sound_2: 위더 스폰 소리 */
    public static void soundWitherSpawn() {
        playAll(Sound.ENTITY_WITHER_SPAWN, 100f, 1f);
    }

    /** sound_3: 노트블럭 pling (낮은 음) */
    public static void soundPlingLow() {
        playAll(Sound.BLOCK_NOTE_BLOCK_PLING, 100f, 0f);
    }

    /** sound_4: 노트블럭 pling (중간) */
    public static void soundPlingMid() {
        playAll(Sound.BLOCK_NOTE_BLOCK_PLING, 100f, 1f);
    }

    /** sound_5: 노트블럭 pling (높은 음) */
    public static void soundPlingHigh() {
        playAll(Sound.BLOCK_NOTE_BLOCK_PLING, 100f, 2f);
    }

    /** sound_6: 경험치 획득 소리 */
    public static void soundXpPickup() {
        playAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 100f, 1f);
    }

    /** 노트블럭 하프 (카운트다운 용) */
    public static void soundCountdown() {
        playAll(Sound.BLOCK_NOTE_BLOCK_HARP, 100f, 1f);
    }

    /** 높은 노트블럭 하프 (시작/종료 강조) */
    public static void soundCountdownHigh() {
        playAll(Sound.BLOCK_NOTE_BLOCK_HARP, 100f, 2f);
    }

    /** 챌린지 완료 토스트 소리 (승리) */
    public static void soundWin() {
        playAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    /** 베이스 노트블럭 (오류/차단) */
    public static void soundError() {
        playAll(Sound.BLOCK_NOTE_BLOCK_BASS, 100f, 1f);
    }

    /** 베이스 노트블럭 - 특정 플레이어만 */
    public static void soundErrorPlayer(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 100f, 1f);
    }
}
