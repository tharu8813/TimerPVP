package org.pdt.timerPVP.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.pdt.timerPVP.TimerPVP;
import org.pdt.timerPVP.config.GameConfig;
import org.pdt.timerPVP.game.GameState;
import org.pdt.timerPVP.game.PlayerState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 사이드바 스코어보드 관리 클래스
 *
 * Paper 1.21.4 (빌드 26.x) 기준:
 * - Entry 문자열은 줄 구분용 고유 키로만 사용 (색상코드 X)
 * - 실제 표시 텍스트는 Score#customName(Component) 으로 설정
 *   → 이렇게 해야 &a, &c 등 색상이 정상적으로 렌더링됨
 */
public class ScoreboardManager {

    private static final LegacyComponentSerializer LEGACY =
        LegacyComponentSerializer.legacyAmpersand();

    private final TimerPVP plugin;
    private final GameConfig config;
    private final GameManager gameManager;

    /** 플레이어별 스코어보드 캐시 */
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    // 줄 구분용 고유 Entry 키 (색상코드 없는 고정 문자열)
    // 스코어보드는 Entry 문자열이 동일하면 같은 줄로 취급하므로
    // 각 줄마다 반드시 서로 다른 문자열을 사용해야 함
    private static final String[] LINE_KEYS = {
        "line_0", "line_1", "line_2", "line_3",
        "line_4", "line_5", "line_6", "line_7",
        "line_8", "line_9"
    };

    public ScoreboardManager(TimerPVP plugin, GameConfig config, GameManager gameManager) {
        this.plugin = plugin;
        this.config = config;
        this.gameManager = gameManager;
    }

    /** 스코어보드 갱신 태스크 시작 (4틱마다) */
    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 0L, 4L);
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

        // Objective 가져오기 (없으면 생성)
        Objective obj = sb.getObjective("timerpvp");
        if (obj == null) {
            obj = sb.registerNewObjective(
                "timerpvp",
                Criteria.DUMMY,
                legacy(config.scoreboardTitle)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            // Entry 키 사전 등록 (점수 할당)
            for (int i = 0; i < LINE_KEYS.length; i++) {
                obj.getScore(LINE_KEYS[i]).setScore(i);
            }
        }

        // 현재 상태 정보 수집
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
        setLine(obj, 5, Component.empty());
        setLine(obj, 4, legacy("&e&l타이머PVP &f준비중"));
        setLine(obj, 3, legacy("&7서버 대기중..."));
        setLine(obj, 2, Component.empty());
        setLine(obj, 1, legacy("&7닉네임: &f" + player.getName()));
        setLine(obj, 0, Component.empty());
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

        setLine(obj, 7, Component.empty());
        setLine(obj, 6, stageComp);
        setLine(obj, 5, Component.empty());
        setLine(obj, 4, nameComp);
        setLine(obj, 3, Component.empty());
        setLine(obj, 2, timerComp);
        setLine(obj, 1, Component.empty());
    }

    // ────────────────────────────────────────────────
    // 내부 유틸
    // ────────────────────────────────────────────────

    /**
     * 특정 줄(score)의 표시 텍스트를 Component로 설정
     * Entry 키는 고정 문자열(LINE_KEYS)을 사용하고,
     * 실제 보이는 텍스트는 customName으로 별도 지정
     */
    private void setLine(Objective obj, int lineIndex, Component display) {
        if (lineIndex < 0 || lineIndex >= LINE_KEYS.length) return;
        Score score = obj.getScore(LINE_KEYS[lineIndex]);
        score.setScore(lineIndex);
        score.customName(display);
    }

    /** &색상코드 → Adventure Component 변환 */
    private static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }

    /** 특정 플레이어 스코어보드 제거 후 메인 스코어보드 복원 */
    public void reset(Player player) {
        Scoreboard sb = scoreboards.remove(player.getUniqueId());
        if (sb != null) {
            Objective obj = sb.getObjective("timerpvp");
            if (obj != null) obj.unregister();
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** 모든 플레이어 스코어보드 초기화 */
    public void resetAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            reset(player);
        }
        scoreboards.clear();
    }
}
