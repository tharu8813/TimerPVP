package org.pdt.timerPVP.manager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.pdt.timerPVP.TimerPVP;
import org.pdt.timerPVP.config.GameConfig;
import org.pdt.timerPVP.game.GameState;

/**
 * 보스바 타이머 관리 클래스
 * 파밍/준비/PVP/오버타임 각 단계에서 남은 시간을 표시
 *
 * 보스바 제목 형식:
 *   파밍     : &a&l파밍 | 남은 시간: M:SS
 *   준비     : &e&l준비 | 남은 시간: M:SS  (스킵 투표 수 포함)
 *   PVP      : &c&lPVP | 생존자: N명
 *   오버타임1: &c오버타임 1단계 [난이도 어려움] | 남은 시간: M:SS → 2단계: 몬스터 소환
 *   오버타임2: &4오버타임 2단계 [몬스터 소환] | 남은 시간: M:SS → 3단계: 허기 고정
 *   오버타임3: &6오버타임 3단계 [허기 고정] | 생존자: N명
 */
public class BossBarManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final TimerPVP  plugin;
    private final GameConfig config;
    private final GameManager gameManager;

    private BossBar bossBar = null;

    public BossBarManager(TimerPVP plugin, GameConfig config, GameManager gameManager) {
        this.plugin      = plugin;
        this.config      = config;
        this.gameManager = gameManager;
    }

    /** 보스바 갱신 태스크 시작 (20틱 = 1초마다) */
    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 0L, 20L);
    }

    /** 현재 게임 상태에 맞게 보스바 갱신 */
    public void update() {
        if (!config.bossbarEnabled) { hideAll(); return; }

        GameState state = gameManager.getState();

        switch (state) {
            case FARMING -> show(
                    makeFarmingTitle(),
                    calcProgress(gameManager.getTimerMinutes(), gameManager.getTimerSeconds(),
                            config.farmingMinutes, config.farmingSeconds),
                    parseColor(config.bossbarFarmingColor)
            );
            case PREPARATION -> show(
                    makePrepTitle(),
                    calcProgress(gameManager.getTimerMinutes(), gameManager.getTimerSeconds(),
                            config.prepMinutes, config.prepSeconds),
                    parseColor(config.bossbarPrepColor)
            );
            case PVP -> {
                if (gameManager.isOvertime()) {
                    showOvertime();
                } else {
                    show(
                            "&c&lPVP &f| 생존자: &c" + gameManager.getAliveCount() + "명",
                            1.0,
                            parseColor(config.bossbarPvpColor)
                    );
                }
            }
            case IDLE, ENDING -> hideAll();
            default -> { /* STARTING / TRANSITIONING 중에는 유지 */ }
        }
    }

    // ─────────────────────────────────────────────────────
    // 오버타임 보스바
    // ─────────────────────────────────────────────────────

    private void showOvertime() {
        int phase = gameManager.getOvertimePhase();
        int rem   = gameManager.getOvertimeRemainingSeconds();

        BarColor color = parseColor(config.bossbarOvertimeColor);

        switch (phase) {
            case 1 -> {
                // 현재: 난이도 어려움 / 다음: 몬스터 소환
                int total = config.overtime.phase1Duration;
                String title = "&c&l오버타임 1단계 &f[&c난이도 어려움&f]"
                        + " &7| &f남은 시간: &c" + formatTime(rem / 60, rem % 60)
                        + " &7→ &e다음: 몬스터 소환";
                double progress = total > 0 ? Math.max(0.0, Math.min(1.0, (double) rem / total)) : 1.0;
                show(title, progress, color);
            }
            case 2 -> {
                // 현재: 몬스터 소환 / 다음: 허기 고정
                int total = config.overtime.phase2Duration;
                String title = "&4&l오버타임 2단계 &f[&4몬스터 소환&f]"
                        + " &7| &f남은 시간: &c" + formatTime(rem / 60, rem % 60)
                        + " &7→ &e다음: 허기 고정";
                double progress = total > 0 ? Math.max(0.0, Math.min(1.0, (double) rem / total)) : 1.0;
                show(title, progress, color);
            }
            case 3 -> {
                // 현재: 허기 고정 / 무한 지속 → 진행 바 꽉 참
                String title = "&6&l오버타임 3단계 &f[&6허기 고정&f]"
                        + " &7| &f생존자: &c" + gameManager.getAliveCount() + "명";
                show(title, 1.0, color);
            }
            default -> {
                // 오버타임 플래그는 true인데 phase가 아직 0인 짧은 순간 방어
                show("&5&l오버타임 &f| 생존자: &c" + gameManager.getAliveCount() + "명",
                        1.0, color);
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // 파밍 / 준비 제목
    // ─────────────────────────────────────────────────────

    private String makeFarmingTitle() {
        int m = gameManager.getTimerMinutes();
        int s = gameManager.getTimerSeconds();
        return "&a&l파밍 &f| 남은 시간: &a" + formatTime(m, s);
    }

    private String makePrepTitle() {
        int m      = gameManager.getTimerMinutes();
        int s      = gameManager.getTimerSeconds();
        int votes  = gameManager.getSkipVoteCount();
        int needed = gameManager.getAliveCount();
        String voteStr = config.skipVoteEnabled
                ? " &7| 스킵 투표: &e" + votes + "/" + needed
                : "";
        return "&e&l준비 &f| 남은 시간: &e" + formatTime(m, s) + voteStr;
    }

    // ─────────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────────

    private void show(String legacyTitle, double progress, BarColor color) {
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("", color, BarStyle.SOLID);
        }
        // Adventure legacy → §-코드 문자열로 변환 (BossBar API는 §-코드 사용)
        bossBar.setTitle(LegacyComponentSerializer.legacySection()
                .serialize(LEGACY.deserialize(legacyTitle)));
        bossBar.setColor(color);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bossBar.setVisible(true);

        // 온라인 플레이어 동기화
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
        }
        // 로그아웃한 플레이어 제거
        for (Player p : bossBar.getPlayers()) {
            if (!p.isOnline()) bossBar.removePlayer(p);
        }
    }

    public void hideAll() {
        if (bossBar != null) {
            bossBar.setVisible(false);
            // removeAll() 은 호출하지 않음 — 플레이어 목록을 지우면
            // 이후 show() 에서 다시 addPlayer 되기 전까지 보스바가 뜨지 않음
        }
    }

    public void addPlayer(Player player) {
        if (bossBar != null && bossBar.isVisible()) bossBar.addPlayer(player);
    }

    public void removePlayer(Player player) {
        if (bossBar != null) bossBar.removePlayer(player);
    }

    private double calcProgress(int currentMin, int currentSec, int totalMin, int totalSec) {
        int current = currentMin * 60 + currentSec;
        int total   = totalMin   * 60 + totalSec;
        if (total <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (double) current / total));
    }

    private BarColor parseColor(String name) {
        try {
            return BarColor.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return BarColor.WHITE;
        }
    }

    private String formatTime(int minutes, int seconds) {
        return String.format("%d:%02d", minutes, seconds);
    }
}