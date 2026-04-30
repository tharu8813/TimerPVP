package org.pdt.timerPVP.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.pdt.timerPVP.TimerPVP;
import org.pdt.timerPVP.config.GameConfig;
import org.pdt.timerPVP.game.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 안정화된 사이드바 스코어보드 관리자
 */
public class ScoreboardManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final TimerPVP plugin;
    private final GameConfig config;
    private final org.pdt.timerPVP.manager.GameManager gameManager;

    /** 플레이어별 스코어보드 캐시 */
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    /** 완전 빈 줄 대신 사용할 안전한 공백 */
    private static final Component BLANK = Component.text(" ");

    /**
     * Entry 키 (절대 중복되지 않도록 색코드 사용)
     * → 가장 안정적인 방식
     */
    private static final String[] LINE_KEYS = {
            "§0","§1","§2","§3","§4",
            "§5","§6","§7","§8","§9"
    };

    public ScoreboardManager(TimerPVP plugin, GameConfig config, org.pdt.timerPVP.manager.GameManager gameManager) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = gameManager;
    }

    /** 스코어보드 갱신 태스크 시작 (10틱 권장) */
    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 0L, 10L);
    }

    public void updateAll() {
        if (!config.scoreboardEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(Player player) {
        if (!config.scoreboardEnabled) return;

        Scoreboard sb = scoreboards.computeIfAbsent(
                player.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard()
        );

        player.setScoreboard(sb);

        Objective obj = sb.getObjective("timerpvp");
        if (obj == null) {
            obj = sb.registerNewObjective(
                    "timerpvp",
                    Criteria.DUMMY,
                    legacy(config.scoreboardTitle)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            for (int i = 0; i < LINE_KEYS.length; i++) {
                obj.getScore(LINE_KEYS[i]).setScore(i);
            }
        }

        GameState state = gameManager.getState();
        PlayerState pState = gameManager.getPlayerState(player);
        boolean isSpectator = pState == PlayerState.SPECTATOR || pState == PlayerState.ELIMINATED;

        if (state == GameState.IDLE) {
            renderIdle(obj, player);
        } else {
            renderGame(obj, player, state, isSpectator);
        }
    }

    // ────────────────────────────────────────────────
    // IDLE 화면
    // ────────────────────────────────────────────────
    private void renderIdle(Objective obj, Player player) {
        setLine(obj, 5, BLANK);
        setLine(obj, 4, legacy("&e&l타이머PVP &f준비중"));
        setLine(obj, 3, legacy("&7서버 대기중..."));
        setLine(obj, 2, BLANK);
        setLine(obj, 1, legacy("&7닉네임: &f" + player.getName()));
        setLine(obj, 0, BLANK);
    }

    // ────────────────────────────────────────────────
    // 게임 진행 중 화면
    // ────────────────────────────────────────────────
    private void renderGame(Objective obj, Player player, GameState state, boolean isSpectator) {

        Component stageComp = switch (state) {
            case STARTING              -> legacy("&e&l준비중");
            case FARMING               -> legacy("&a&l파밍");
            case TRANSITIONING_TO_PREP -> legacy("&e&l이동중");
            case PREPARATION           -> legacy("&6&l준비");
            case TRANSITIONING_TO_PVP  -> legacy("&e&l이동중");
            case PVP                   -> legacy("&c&lPVP");
            case ENDING                -> legacy("&f&l종료중");
            default                    -> legacy("&7대기");
        };

        Component spectatorTag = isSpectator
                ? legacy(" &7[관전]")
                : Component.empty();

        Component nameComp = legacy("&f&l닉네임: &7" + player.getName())
                .append(spectatorTag);

        Component timerComp;
        if (state == GameState.FARMING || state == GameState.PREPARATION) {
            timerComp = legacy("&a&l남은 시간: &f"
                    + gameManager.getTimerMinutes() + "분 "
                    + gameManager.getTimerSeconds() + "초");
        } else if (state == GameState.STARTING
                || state == GameState.TRANSITIONING_TO_PREP
                || state == GameState.TRANSITIONING_TO_PVP) {
            timerComp = legacy("&e&l전환중...");
        } else if (state == GameState.PVP) {
            timerComp = legacy("&c&l생존자: &f" + gameManager.getAliveCount() + "명");
        } else {
            timerComp = legacy("&7-");
        }

        setLine(obj, 7, BLANK);
        setLine(obj, 6, stageComp);
        setLine(obj, 5, BLANK);
        setLine(obj, 4, nameComp);
        setLine(obj, 3, BLANK);
        setLine(obj, 2, timerComp);
        setLine(obj, 1, BLANK);
    }

    // ────────────────────────────────────────────────
    // 내부 유틸
    // ────────────────────────────────────────────────

    private void setLine(Objective obj, int lineIndex, Component display) {
        if (lineIndex < 0 || lineIndex >= LINE_KEYS.length) return;

        Score score = obj.getScore(LINE_KEYS[lineIndex]);
        score.setScore(lineIndex);

        // 안정화 처리 (중요)
        score.customName(BLANK);
        score.customName(display == null ? BLANK : display);
    }

    private static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }

    /** 플레이어 스코어보드 초기화 */
    public void reset(Player player) {
        Scoreboard sb = scoreboards.remove(player.getUniqueId());
        if (sb != null) {
            Objective obj = sb.getObjective("timerpvp");
            if (obj != null) obj.unregister();
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** 전체 초기화 */
    public void resetAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            reset(player);
        }
        scoreboards.clear();
    }
}