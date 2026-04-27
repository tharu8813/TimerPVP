package org.pdt.timerPVP.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pdt.timerPVP.TimerPVP;
import org.pdt.timerPVP.manager.GameManager;
import org.pdt.timerPVP.util.MessageUtil;

import java.util.Arrays;
import java.util.List;

/**
 * /tf debug <서브명령어> 처리기
 */
public class DebugCommand {

    private static final String DBG = "&e&l타이머PVP디버그:&f ";

    private final TimerPVP    plugin;
    private final GameManager gameManager;

    public DebugCommand(TimerPVP plugin, GameManager gameManager) {
        this.plugin      = plugin;
        this.gameManager = gameManager;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("timerpvp.debug")) {
            msg(sender, "&4권한이 부족해 디버그 명령어를 사용할 수 없습니다.");
            return true;
        }

        if (args.length == 0) { sendDebugHelp(sender); return true; }

        switch (args[0].toLowerCase()) {

            case "check" -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    msg(sender, DBG + p.getName() + " 상태: &e" + gameManager.getPlayerState(p).name());
                }
            }

            case "spectator" -> {
                if (!(sender instanceof Player player)) { msg(sender, "플레이어만 사용 가능합니다."); return true; }
                if (!gameManager.isSpectator(player)) gameManager.toggleSpectator(player);
                msg(sender, DBG + "관전자 여부를 &aTRUE&f로 변경했습니다.");
            }

            case "spectator_reset" -> {
                if (!(sender instanceof Player player)) { msg(sender, "플레이어만 사용 가능합니다."); return true; }
                if (gameManager.isSpectator(player)) gameManager.toggleSpectator(player);
                msg(sender, DBG + "관전자 여부를 &cFALSE&f로 변경했습니다.");
            }

            case "game_reset" -> {
                Bukkit.broadcast(MessageUtil.of(DBG + "모든 플레이어 게임 참여 여부를 초기화합니다."));
                if (gameManager.isGameRunning()) gameManager.forceStop();
            }

            case "del_world" -> {
                Bukkit.broadcast(MessageUtil.of(DBG + "파밍 월드 삭제 시도중..."));
                plugin.getWorldManager()
                    .unloadAndDeleteWorld(plugin.getGameConfig().farmingWorldName)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                        () -> Bukkit.broadcast(MessageUtil.of(DBG + "월드 삭제 완료!"))));
            }

            case "set_time" -> {
                int m = args.length > 1 ? parseIntSafe(args[1], 13) : 13;
                int s = args.length > 2 ? parseIntSafe(args[2], 0)  : 0;
                gameManager.debugSetTimer(m, s);
                Bukkit.broadcast(MessageUtil.of(DBG + "타이머를 &e" + m + "분 " + s + "초&f로 설정했습니다."));
            }

            case "skip_time" -> {
                gameManager.debugSetTimer(0, 3);
                Bukkit.broadcast(MessageUtil.of(DBG + "타이머를 &e0분 3초&f로 설정했습니다."));
            }

            case "game_stop" -> {
                if (gameManager.isGameRunning()) {
                    gameManager.forceStop();
                    Bukkit.broadcast(MessageUtil.of(DBG + "게임을 강제 종료했습니다."));
                } else {
                    msg(sender, DBG + "&c게임이 실행중이지 않습니다.");
                }
            }

            case "time_stop" -> {
                gameManager.debugStopTimer();
                Bukkit.broadcast(MessageUtil.of(DBG + "타이머를 정지했습니다."));
            }

            case "state" -> {
                msg(sender, DBG + "GameState: &e" + gameManager.getState().name());
                msg(sender, DBG + "Timer: &e"
                    + gameManager.getTimerMinutes() + "분 " + gameManager.getTimerSeconds() + "초");
                msg(sender, DBG + "생존자: &e" + gameManager.getAliveCount() + "명");
            }

            default -> sendDebugHelp(sender);
        }
        return true;
    }

    private void sendDebugHelp(CommandSender sender) {
        msg(sender, "&e===== [Debug 명령어] =====");
        msg(sender, "&e/tf debug check           &f- 모든 플레이어 상태 확인");
        msg(sender, "&e/tf debug spectator       &f- 자신을 관전자로 변경");
        msg(sender, "&e/tf debug spectator_reset &f- 자신을 참가자로 변경");
        msg(sender, "&e/tf debug game_reset      &f- 게임 상태 초기화");
        msg(sender, "&e/tf debug del_world       &f- 파밍 월드 강제 삭제");
        msg(sender, "&e/tf debug set_time [분] [초] &f- 타이머 설정 (기본 13분)");
        msg(sender, "&e/tf debug skip_time       &f- 타이머를 3초로 설정");
        msg(sender, "&e/tf debug game_stop       &f- 게임 강제 종료");
        msg(sender, "&e/tf debug time_stop       &f- 타이머 정지");
        msg(sender, "&e/tf debug state           &f- 현재 게임 상태 출력");
        msg(sender, "&e==========================");
    }

    public List<String> tabComplete(String partial) {
        return Arrays.asList(
            "check", "spectator", "spectator_reset", "game_reset",
            "del_world", "set_time", "skip_time", "game_stop", "time_stop", "state"
        ).stream()
            .filter(s -> s.startsWith(partial.toLowerCase()))
            .toList();
    }

    private int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    /** CommandSender가 Player든 콘솔이든 안전하게 메시지 전송 */
    private void msg(CommandSender sender, String legacyText) {
        sender.sendMessage(MessageUtil.of(legacyText));
    }
}
