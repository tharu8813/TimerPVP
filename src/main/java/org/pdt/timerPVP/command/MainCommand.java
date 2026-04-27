package org.pdt.timerPVP.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.pdt.timerPVP.manager.GameManager;
import org.pdt.timerPVP.TimerPVP;
import org.pdt.timerPVP.util.MessageUtil;
import org.pdt.timerPVP.util.SoundUtil;

import java.util.Arrays;
import java.util.List;

/**
 * /tf 메인 명령어 처리
 * 서브커맨드: start | stop | sp | vote | status | reload | debug
 */
public class MainCommand implements CommandExecutor, TabCompleter {

    private final TimerPVP      plugin;
    private final GameManager   gameManager;
    private final DebugCommand  debugCommand;

    public MainCommand(TimerPVP plugin, GameManager gameManager, DebugCommand debugCommand) {
        this.plugin       = plugin;
        this.gameManager  = gameManager;
        this.debugCommand = debugCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 콘솔 허용 명령어
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
                gameManager.forceStop();
                sender.sendMessage("[TimerPVP] 게임을 강제 종료했습니다.");
            } else if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
                debugCommand.handle(sender, Arrays.copyOfRange(args, 1, args.length));
            } else {
                sender.sendMessage("[TimerPVP] 플레이어만 사용 가능합니다. (stop / debug 는 콘솔 허용)");
            }
            return true;
        }

        // /tf vote 는 권한 체크 없이 모든 플레이어 허용
        if (args.length > 0 && args[0].equalsIgnoreCase("vote")) {
            gameManager.submitSkipVote(player);
            return true;
        }

        if (!player.hasPermission("timerpvp.admin")) {
            MessageUtil.send(player, "&4권한이 부족해 명령어를 사용할 수 없습니다.");
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "start"  -> gameManager.startGame(player);
            case "stop"   -> gameManager.stopGame(player);
            case "sp"     -> handleSpectator(player, args);
            case "reload" -> handleReload(player);
            case "status" -> handleStatus(player);
            case "debug"  -> debugCommand.handle(player, Arrays.copyOfRange(args, 1, args.length));
            default -> {
                MessageUtil.send(player, "&4잘못된 값을 입력하셨습니다. 확인후 다시 시도해 주세요.");
                sendHelp(player);
            }
        }
        return true;
    }

    private void handleSpectator(Player sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "&e/tf sp <플레이어>&f: 관전자↔참가자 전환");
            return;
        }
        Player target = sender.getServer().getPlayer(args[1]);
        if (target == null) {
            MessageUtil.send(sender, MessageUtil.PREFIX_ERROR + "플레이어 &e" + args[1] + "&f를 찾을 수 없습니다.");
            return;
        }
        boolean nowSpectator = gameManager.toggleSpectator(target);
        if (nowSpectator) {
            MessageUtil.send(sender, MessageUtil.PREFIX_OK + target.getName() + "님을 관전자로 변경했습니다.");
            MessageUtil.send(target, MessageUtil.PREFIX_OK + "관전자로 변경됩니다.");
        } else {
            MessageUtil.send(sender, MessageUtil.PREFIX_OK + target.getName() + "님을 참가자로 변경했습니다.");
            MessageUtil.send(target, MessageUtil.PREFIX_OK + "참가자로 변경됩니다.");
        }
    }

    private void handleReload(Player sender) {
        plugin.reloadPluginConfig();
        MessageUtil.send(sender, MessageUtil.PREFIX_OK + "설정 파일을 다시 로드했습니다.");
    }

    private void handleStatus(Player sender) {
        MessageUtil.send(sender, "&e===== [TimerPVP 상태] =====");
        MessageUtil.send(sender, "&f게임 상태: &a" + gameManager.getState().name());
        MessageUtil.send(sender, "&f타이머: &e"
            + gameManager.getTimerMinutes() + "분 " + gameManager.getTimerSeconds() + "초");
        MessageUtil.send(sender, "&f생존자: &a" + gameManager.getAliveCount() + "명");
        MessageUtil.send(sender, "&e====================");
    }

    private void sendHelp(Player player) {
        MessageUtil.send(player, "=====[&a명령어&f]=====");
        MessageUtil.send(player, "&e/tf start&f: 게임을 시작합니다.");
        MessageUtil.send(player, "&e/tf stop&f: 게임을 종료합니다.");
        MessageUtil.send(player, "&e/tf sp <플레이어>&f: 관전자↔참가자 전환.");
        MessageUtil.send(player, "&e/tf vote&f: 준비 단계 스킵 투표.");
        MessageUtil.send(player, "&e/tf status&f: 현재 게임 상태를 확인합니다.");
        MessageUtil.send(player, "&e/tf reload&f: 설정 파일을 다시 로드합니다.");
        MessageUtil.send(player, "&e/tf debug <...>&f: 디버그 명령어 목록을 확인합니다.");
        MessageUtil.send(player, "==================");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // vote 는 모든 플레이어에게 탭 완성 노출
            List<String> base = sender.hasPermission("timerpvp.admin")
                ? List.of("start", "stop", "sp", "vote", "status", "reload", "debug")
                : List.of("vote");
            return filterStart(base, args[0]);
        }
        if (!sender.hasPermission("timerpvp.admin")) return List.of();

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "sp" -> sender.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
                case "debug" -> debugCommand.tabComplete(args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("debug")
                && args[1].equalsIgnoreCase("set_time")) {
            return List.of("0", "1", "3", "5", "10", "13", "20");
        }
        return List.of();
    }

    private List<String> filterStart(List<String> all, String partial) {
        return all.stream()
            .filter(s -> s.startsWith(partial.toLowerCase()))
            .toList();
    }
}
