package org.pdt.timerPVP;

import org.bukkit.plugin.java.JavaPlugin;
import org.pdt.timerPVP.command.DebugCommand;
import org.pdt.timerPVP.command.MainCommand;
import org.pdt.timerPVP.config.GameConfig;
import org.pdt.timerPVP.listener.GameListener;
import org.pdt.timerPVP.manager.BossBarManager;
import org.pdt.timerPVP.manager.GameManager;
import org.pdt.timerPVP.manager.ScoreboardManager;
import org.pdt.timerPVP.manager.WorldManager;

import java.util.Objects;

/**
 * TimerPVP 플러그인 메인 클래스
 *
 * 게임 흐름:
 *   IDLE → STARTING → FARMING → TRANSITIONING_TO_PREP
 *        → PREPARATION → TRANSITIONING_TO_PVP → PVP → ENDING → IDLE
 */
public final class TimerPVP extends JavaPlugin {

    private GameConfig        gameConfig;
    private WorldManager      worldManager;
    private GameManager       gameManager;
    private ScoreboardManager scoreboardManager;
    private BossBarManager    bossBarManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 설정 로드
        gameConfig = new GameConfig(this);
        gameConfig.load();

        // 매니저 초기화
        worldManager      = new WorldManager(this, gameConfig);
        gameManager       = new GameManager(this, gameConfig, worldManager);
        scoreboardManager = new ScoreboardManager(this, gameConfig, gameManager);
        bossBarManager    = new BossBarManager(this, gameConfig, gameManager);

        // 이벤트 리스너 등록
        getServer().getPluginManager().registerEvents(
                new GameListener(gameManager, gameConfig), this
        );

        // 갱신 태스크 시작
        scoreboardManager.startTask();
        bossBarManager.startTask();

        // 명령어 등록
        DebugCommand debugCommand = new DebugCommand(this, gameManager);
        MainCommand  mainCommand  = new MainCommand(this, gameManager, debugCommand);
        var tfCmd = Objects.requireNonNull(getCommand("tf"));
        tfCmd.setExecutor(mainCommand);
        tfCmd.setTabCompleter(mainCommand);

        getLogger().info("TimerPVP v0.0.1v (Beta) 이 활성화되었습니다.");
        getLogger().info("제작: 타루(Tharu) tharu_81");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.isGameRunning()) {
            gameManager.forceStop();
        }
        if (scoreboardManager != null) {
            scoreboardManager.resetAll();
        }
        if (bossBarManager != null) {
            bossBarManager.hideAll();
        }

        getServer().getScheduler().cancelTasks(this);
        getLogger().info("TimerPVP가 비활성화되었습니다.");
    }

    /** config.yml reload 처리 */
    public void reloadPluginConfig() {
        gameConfig.load();
        getLogger().info("설정 파일을 다시 로드했습니다.");
    }

    // ================================================================
    // Getter
    // ================================================================

    public GameConfig        getGameConfig()        { return gameConfig; }
    public WorldManager      getWorldManager()      { return worldManager; }
    public GameManager       getGameManager()       { return gameManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public BossBarManager    getBossBarManager()    { return bossBarManager; }
}