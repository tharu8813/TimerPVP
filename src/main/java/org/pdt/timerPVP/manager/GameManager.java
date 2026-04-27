package org.pdt.timerPVP.manager;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.pdt.timerPVP.TimerPVP;
import org.pdt.timerPVP.config.GameConfig;
import org.pdt.timerPVP.config.OvertimeConfig;
import org.pdt.timerPVP.game.GameState;
import org.pdt.timerPVP.game.PlayerState;
import org.pdt.timerPVP.util.MessageUtil;
import org.pdt.timerPVP.util.SoundUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 타이머 PVP 게임의 핵심 로직
 *
 * 게임 흐름:
 *   IDLE → STARTING → FARMING → TRANSITIONING_TO_PREP
 *        → PREPARATION → TRANSITIONING_TO_PVP → PVP(→OVERTIME) → ENDING → IDLE
 *
 * 오버타임 흐름 (PVP 시작 후 delay_seconds 뒤):
 *   Phase1(난이도↑, duration 지속) → Phase2(몬스터 소환, duration 지속) → Phase3(허기 고정, 무한)
 */
public class GameManager {

    private final TimerPVP     plugin;
    private final GameConfig   config;
    private final WorldManager worldManager;

    // ── 게임 상태 ──────────────────────────────────
    private volatile GameState state = GameState.IDLE;
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    // ── 타이머 ──────────────────────────────────────
    private int timerMinutes = 0;
    private int timerSeconds = 0;
    private BukkitTask timerTask     = null;
    private BukkitTask spectatorTask = null;
    private BukkitTask actionBarTask = null;

    // ── PVP ─────────────────────────────────────────
    private boolean pvpProtected = false;

    // ── 스킵 투표 ────────────────────────────────────
    private final Set<UUID> skipVotes = ConcurrentHashMap.newKeySet();

    // ── 오버타임 ──────────────────────────────────
    private boolean    overtime                = false;
    private int        overtimePhase           = 0;
    private int        overtimeRemainingSeconds = 0;
    private BukkitTask overtimePhaseTask       = null;
    private BukkitTask monsterSpawnTask        = null;
    private BukkitTask hungerTask              = null;

    private final Random rand = new Random();

    public GameManager(TimerPVP plugin, GameConfig config, WorldManager worldManager) {
        this.plugin       = plugin;
        this.config       = config;
        this.worldManager = worldManager;
    }

    // ================================================================
    // 공개 API
    // ================================================================

    public GameState getState()               { return state; }
    public int  getTimerMinutes()             { return timerMinutes; }
    public int  getTimerSeconds()             { return timerSeconds; }
    public boolean isPvpProtected()           { return pvpProtected; }
    public boolean isOvertime()               { return overtime; }
    public int  getOvertimePhase()            { return overtimePhase; }
    public int  getOvertimeRemainingSeconds() { return overtimeRemainingSeconds; }
    public int  getSkipVoteCount()            { return skipVotes.size(); }

    public PlayerState getPlayerState(Player player) {
        return playerStates.getOrDefault(player.getUniqueId(), PlayerState.NONE);
    }
    public boolean isParticipant(Player player) {
        return playerStates.get(player.getUniqueId()) == PlayerState.PARTICIPANT;
    }
    public boolean isSpectator(Player player) {
        PlayerState ps = playerStates.get(player.getUniqueId());
        return ps == PlayerState.SPECTATOR || ps == PlayerState.ELIMINATED;
    }
    public boolean isGameRunning() {
        return state != GameState.IDLE && state != GameState.ENDING;
    }

    public int getAliveCount() {
        int count = 0;
        for (Map.Entry<UUID, PlayerState> e : playerStates.entrySet()) {
            if (e.getValue() == PlayerState.PARTICIPANT) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null && p.isOnline()) count++;
            }
        }
        return count;
    }

    public List<Player> getAlivePlayers() {
        List<Player> alive = new ArrayList<>();
        for (Map.Entry<UUID, PlayerState> e : playerStates.entrySet()) {
            if (e.getValue() == PlayerState.PARTICIPANT) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null && p.isOnline()) alive.add(p);
            }
        }
        return alive;
    }

    // ================================================================
    // 관전자 토글
    // ================================================================

    public boolean toggleSpectator(Player target) {
        UUID       uid     = target.getUniqueId();
        PlayerState current = playerStates.getOrDefault(uid, PlayerState.NONE);
        if (current == PlayerState.SPECTATOR || current == PlayerState.ELIMINATED) {
            playerStates.put(uid, PlayerState.PARTICIPANT);
            if (isGameRunning()) applyParticipantGameMode(target);
            return false;
        } else {
            playerStates.put(uid, PlayerState.SPECTATOR);
            if (isGameRunning()) applySpectatorGameMode(target);
            return true;
        }
    }

    // ================================================================
    // 게임 시작
    // ================================================================

    public void startGame(Player initiator) {
        if (state != GameState.IDLE) {
            MessageUtil.send(initiator, MessageUtil.PREFIX_ERROR + "게임이 이미 진행중입니다.");
            SoundUtil.soundError();
            return;
        }

        long participantCount = Bukkit.getOnlinePlayers().stream()
                .filter(p -> playerStates.getOrDefault(
                        p.getUniqueId(), PlayerState.NONE) != PlayerState.SPECTATOR)
                .count();

        if (participantCount < config.minPlayers) {
            MessageUtil.broadcast(MessageUtil.PREFIX_ERROR
                    + "최소 " + config.minPlayers + "명이 필요하므로 게임을 시작하지 못했습니다.");
            SoundUtil.soundError();
            return;
        }

        state = GameState.STARTING;

        Map<UUID, PlayerState> snapshot = new HashMap<>(playerStates);
        playerStates.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (snapshot.getOrDefault(p.getUniqueId(), PlayerState.NONE) == PlayerState.SPECTATOR) {
                playerStates.put(p.getUniqueId(), PlayerState.SPECTATOR);
                MessageUtil.send(p, "&l&a당신은 관전자이므로 게임에서 제외되었습니다.");
            } else {
                playerStates.put(p.getUniqueId(), PlayerState.PARTICIPANT);
            }
        }

        runStartSequence();
    }

    private void runStartSequence() {
        MessageUtil.broadcast("&e-------------");
        MessageUtil.broadcast("제작: &6타루&8(Tharu) tharu_81");
        MessageUtil.broadcast("버전: &a2.1.0");
        MessageUtil.broadcast("&e-------------");
        SoundUtil.soundClick();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            MessageUtil.broadcast("&f[&a타이머 PVP&f]");
            SoundUtil.soundClick();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                StringBuilder sb = new StringBuilder("=====[&a참가자&f]=====");
                int idx = 1;
                for (Map.Entry<UUID, PlayerState> e : playerStates.entrySet()) {
                    if (e.getValue() == PlayerState.PARTICIPANT) {
                        Player p = Bukkit.getPlayer(e.getKey());
                        if (p != null) sb.append("\n").append(idx++).append(". &e").append(p.getName()).append("&f");
                    }
                }
                sb.append("\n==============");
                MessageUtil.broadcast(sb.toString());
                SoundUtil.soundClick();

                Bukkit.getScheduler().runTaskLater(plugin, this::createFarmingWorldAndStart, 10L);
            }, 20L);
        }, 40L);
    }

    private void createFarmingWorldAndStart() {
        MessageUtil.broadcast(MessageUtil.PREFIX_INFO + "월드 생성중...");
        worldManager.createFarmingWorld().thenAccept(world -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (world == null) {
                    MessageUtil.broadcast(MessageUtil.PREFIX_ERROR + "월드 생성 실패.");
                    forceStop();
                    return;
                }
                MessageUtil.broadcast(MessageUtil.PREFIX_OK + "월드 생성완료!");
                runPreStartCountdown(world);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "월드 생성 실패", ex);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MessageUtil.broadcast(MessageUtil.PREFIX_ERROR + "월드 생성에 실패했습니다.");
                forceStop();
            });
            return null;
        });
    }

    private void runPreStartCountdown(World farmingWorld) {
        MessageUtil.broadcast(MessageUtil.PREFIX_INFO + "잠시후 시작합니다.");
        SoundUtil.soundClick();
        final int[] remaining = {config.startCountdown};

        new BukkitRunnable() {
            @Override
            public void run() {
                if (remaining[0] > 0) {
                    MessageUtil.broadcast(MessageUtil.PREFIX_INFO + "&a" + remaining[0] + "&f초뒤에 시작합니다.");
                    SoundUtil.soundCountdown();
                    remaining[0]--;
                } else {
                    this.cancel();
                    MessageUtil.broadcast(MessageUtil.PREFIX_OK + "&e시작!");
                    SoundUtil.soundCountdownHigh();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> startFarmingStage(farmingWorld), 20L);
                }
            }
        }.runTaskTimer(plugin, 60L, 20L);
    }

    // ================================================================
    // Stage 1: 파밍
    // ================================================================

    private void startFarmingStage(World farmingWorld) {
        state = GameState.FARMING;
        List<Player> participants = getAlivePlayers();
        worldManager.spreadPlayers(farmingWorld, participants);

        // 관전자를 파밍 월드 내 생존자 근처로 텔레포트
        for (Map.Entry<UUID, PlayerState> e : playerStates.entrySet()) {
            if (e.getValue() == PlayerState.SPECTATOR || e.getValue() == PlayerState.ELIMINATED) {
                Player sp = Bukkit.getPlayer(e.getKey());
                if (sp != null) {
                    sp.teleport(randomSpectatorLocation(farmingWorld, participants));
                    applySpectatorGameMode(sp);
                }
            }
        }

        for (Player p : participants) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }

        farmingWorld.setDifficulty(Difficulty.NORMAL);
        timerMinutes = config.farmingMinutes;
        timerSeconds = config.farmingSeconds;
        startTimer(this::endFarmingStage);
        startSpectatorWatchTask();
        startActionBarTask();
    }

    private void endFarmingStage() {
        if (state != GameState.FARMING) return;
        state = GameState.TRANSITIONING_TO_PREP;
        stopTimerTask();
        MessageUtil.broadcast("&l" + MessageUtil.PREFIX_OK + "시간이 종료되었습니다.");
        SoundUtil.soundWitherSpawn();
        broadcastEndCountdown(config.endCountdown, this::teleportToPreparationArea);
    }

    // ================================================================
    // Stage 2: 준비
    // ================================================================

    private void teleportToPreparationArea() {
        Location prepLoc = config.getPrepLocation();
        if (prepLoc == null) { plugin.getLogger().severe("준비 구역 위치 없음"); forceStop(); return; }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(prepLoc);
            p.setRespawnLocation(prepLoc, true);
        }

        worldManager.unloadAndDeleteWorld(config.farmingWorldName);

        Location red1 = config.getRedstone1Location();
        if (red1 != null) {
            red1.getBlock().setType(Material.REDSTONE_BLOCK);
            Bukkit.getScheduler().runTaskLater(plugin, () -> red1.getBlock().setType(Material.AIR), 100L);
        }

        for (Player p : getAlivePlayers()) {
            p.getInventory().clear();
            for (GameConfig.PrepItem item : config.prepItems) {
                p.getInventory().addItem(new ItemStack(item.material(), item.amount()));
            }
        }

        World mainWorld = prepLoc.getWorld();
        if (mainWorld != null) { mainWorld.setDifficulty(Difficulty.PEACEFUL); mainWorld.setTime(6000); }

        skipVotes.clear();
        state        = GameState.PREPARATION;
        timerMinutes = config.prepMinutes;
        timerSeconds = config.prepSeconds;
        startTimer(this::endPreparationStage);

        MessageUtil.broadcast("&l" + MessageUtil.PREFIX_OK + "마지막으로 준비하세요!");
        if (config.skipVoteEnabled) {
            MessageUtil.broadcast("&7준비가 완료됐다면 &e/tf vote &7를 입력하세요. 전원 투표시 즉시 시작합니다.");
        }
        SoundUtil.soundClick();
    }

    // ── 스킵 투표 ────────────────────────────────────

    public void submitSkipVote(Player player) {
        if (state != GameState.PREPARATION) {
            MessageUtil.send(player, MessageUtil.PREFIX_ERROR + "준비 단계에서만 투표할 수 있습니다.");
            return;
        }
        if (!config.skipVoteEnabled) {
            MessageUtil.send(player, MessageUtil.PREFIX_ERROR + "스킵 투표가 비활성화되어 있습니다.");
            return;
        }
        if (isSpectator(player)) {
            MessageUtil.send(player, MessageUtil.PREFIX_ERROR + "관전자는 투표할 수 없습니다.");
            return;
        }
        if (skipVotes.contains(player.getUniqueId())) {
            MessageUtil.send(player, MessageUtil.PREFIX_ERROR + "이미 투표했습니다.");
            return;
        }

        skipVotes.add(player.getUniqueId());
        int votes  = skipVotes.size();
        int needed = getAliveCount();
        MessageUtil.broadcast(MessageUtil.PREFIX_OK
                + player.getName() + "님이 스킵에 투표했습니다. (&e" + votes + "/" + needed + "&f)");
        SoundUtil.soundClick();

        if (votes >= needed) {
            MessageUtil.broadcast("&a&l전원 투표 완료! 즉시 시작합니다!");
            SoundUtil.soundCountdownHigh();
            stopTimerTask();
            Bukkit.getScheduler().runTaskLater(plugin, this::endPreparationStage, 20L);
        }
    }

    private void endPreparationStage() {
        if (state != GameState.PREPARATION) return;
        state = GameState.TRANSITIONING_TO_PVP;
        stopTimerTask();
        skipVotes.clear();
        MessageUtil.broadcast("&l" + MessageUtil.PREFIX_OK + "시간이 종료되었습니다.");
        SoundUtil.soundWitherSpawn();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            MessageUtil.broadcast("&l" + MessageUtil.PREFIX_INFO + "잠시후 TP됩니다.");
            SoundUtil.soundClick();
            broadcastEndCountdown(config.endCountdown, this::teleportToPvpArena);
        }, 20L);
    }

    // ================================================================
    // Stage 3: PVP
    // ================================================================

    private void teleportToPvpArena() {
        Location pvpLoc = config.getPvpLocation();
        if (pvpLoc == null) { plugin.getLogger().severe("PVP 구역 위치 없음"); forceStop(); return; }

        SoundUtil.soundClick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(pvpLoc);
            p.setRespawnLocation(pvpLoc, true);
        }
        World pvpWorld = pvpLoc.getWorld();
        if (pvpWorld != null) pvpWorld.setTime(6000);

        Location red2 = config.getRedstone2Location();
        if (red2 != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                red2.getBlock().setType(Material.REDSTONE_BLOCK);
                Bukkit.getScheduler().runTaskLater(plugin, () -> red2.getBlock().setType(Material.AIR), 100L);
            }, 40L);
        }

        pvpProtected = true;
        state        = GameState.PVP;
        overtime     = false;
        overtimePhase = 0;
        overtimeRemainingSeconds = 0;

        runPvpStartCountdown(() -> {
            pvpProtected = false;
            if (pvpWorld != null) pvpWorld.setDifficulty(Difficulty.NORMAL);
            // PVP 시작 후 delay_seconds 뒤에 오버타임 1단계 시작
            scheduleOvertimeStart();
            checkWinCondition();
        });
    }

    private void runPvpStartCountdown(Runnable onComplete) {
        MessageUtil.broadcastTitle("&a잠시후 시작합니다.", "", 10, 60, 10);
        SoundUtil.soundClick();
        final int[] counts = {5, 4, 3, 2, 1, 0};
        final int[] idx    = {0};

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (idx[0] >= counts.length) {
                        this.cancel();
                        MessageUtil.broadcastTitle("&aSTART!", "", 5, 40, 10);
                        SoundUtil.soundCountdownHigh();
                        onComplete.run();
                        return;
                    }
                    int c   = counts[idx[0]];
                    String col = c >= 3 ? "&a" : (c >= 1 ? "&e" : "&c");
                    MessageUtil.broadcastTitle(col + c, "&a잠시후 시작합니다.", 5, 25, 5);
                    SoundUtil.soundCountdown();
                    idx[0]++;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }, 60L);
    }

    // ================================================================
    // 오버타임 진입 스케줄
    // ================================================================

    /**
     * PVP 시작 후 config.overtime.delaySeconds 만큼 기다렸다가 1단계 시작
     */
    private void scheduleOvertimeStart() {
        if (!config.overtime.enabled) return;
        long delayTicks = (long) config.overtime.delaySeconds * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == GameState.PVP) startOvertimePhase1();
        }, delayTicks);
    }

    // ================================================================
    // 오버타임 Phase 1: 난이도 어려움
    // ================================================================

    private void startOvertimePhase1() {
        // overtime 플래그는 어떤 단계가 enabled 여부와 무관하게 먼저 세팅
        overtime = true;
        if (!config.overtime.phase1Enabled) { startOvertimePhase2(); return; }

        cancelOvertimePhaseTask(); // 이전 task 정리
        overtimePhase            = 1;
        overtimeRemainingSeconds = config.overtime.phase1Duration;

        MessageUtil.broadcast(config.overtime.phase1Message);
        SoundUtil.soundWitherSpawn();

        Location pvpLoc = config.getPvpLocation();
        if (pvpLoc != null && pvpLoc.getWorld() != null) {
            pvpLoc.getWorld().setDifficulty(config.overtime.phase1Difficulty);
        }

        final int[] rem = {config.overtime.phase1Duration};
        overtimePhaseTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PVP) { this.cancel(); return; }
                overtimeRemainingSeconds = rem[0];
                rem[0]--;
                if (rem[0] < 0) {
                    this.cancel();
                    startOvertimePhase2();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ================================================================
    // 오버타임 Phase 2: 몬스터 소환
    // ================================================================

    private void startOvertimePhase2() {
        overtime = true; // Phase 1이 skip된 경우에도 보장
        if (!config.overtime.phase2Enabled) { startOvertimePhase3(); return; }

        cancelOvertimePhaseTask();
        overtimePhase            = 2;
        overtimeRemainingSeconds = config.overtime.phase2Duration;

        MessageUtil.broadcast(config.overtime.phase2Message);
        SoundUtil.soundWitherSpawn();

        // 발광 효과
        if (config.overtime.glowing) {
            for (Player p : getAlivePlayers()) {
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING,
                        config.overtime.phase2Duration * 20 + 200, 0, false, true));
            }
            MessageUtil.broadcast(MessageUtil.PREFIX_INFO + "&b모든 플레이어에게 발광 효과가 적용됩니다!");
        }

        final int[] rem = {config.overtime.phase2Duration};
        overtimePhaseTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.PVP) { this.cancel(); return; }
                overtimeRemainingSeconds = rem[0];
                rem[0]--;
                if (rem[0] < 0) {
                    this.cancel();
                    stopMonsterSpawn();
                    startOvertimePhase3();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        startMonsterSpawnTask();
    }

    // ── 몬스터 소환 태스크 ──────────────────────────

    private void startMonsterSpawnTask() {
        stopMonsterSpawn();
        long intervalTicks = (long) config.overtime.spawnInterval * 20L;
        monsterSpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.PVP) return;
            spawnOvertimeMonsters();
        }, intervalTicks, intervalTicks);
    }

    private void spawnOvertimeMonsters() {
        Location pvpLoc = config.getPvpLocation();
        if (pvpLoc == null || pvpLoc.getWorld() == null) return;

        World          world = pvpLoc.getWorld();
        OvertimeConfig ot    = config.overtime;

        if (ot.randomSpawn) {
            // 확률 기반: 생존자 수만큼 소환 (최소 1마리)
            int count = Math.max(1, getAliveCount());
            for (int i = 0; i < count; i++) {
                EntityType type = ot.pickRandom(rand);
                world.spawnEntity(randomNearby(pvpLoc, ot.spawnRadius, rand), type);
            }
            MessageUtil.broadcast(MessageUtil.PREFIX_INFO + "&c몬스터 &e" + count + "마리&c가 소환됐습니다!");
        } else {
            // 개체수 지정
            int total = 0;
            for (OvertimeConfig.SpawnEntry entry : ot.spawnList) {
                for (int i = 0; i < entry.count(); i++) {
                    world.spawnEntity(randomNearby(pvpLoc, ot.spawnRadius, rand), entry.type());
                    total++;
                }
            }
            MessageUtil.broadcast(MessageUtil.PREFIX_INFO + "&c몬스터 &e" + total + "마리&c가 소환됐습니다!");
        }
    }

    private Location randomNearby(Location center, int radius, Random r) {
        double angle = r.nextDouble() * Math.PI * 2;
        double dist  = r.nextInt(Math.max(1, radius)) + 5;

        double x = center.getX() + Math.cos(angle) * dist;
        double z = center.getZ() + Math.sin(angle) * dist;

        World world = center.getWorld();
        int maxY = world.getMaxHeight();

        for (int y = maxY; y > world.getMinHeight(); y--) {
            Material mat = world.getBlockAt((int)x, y, (int)z).getType();

            // ❌ 제외할 블록 (여기서 커스텀 가능)
            if (mat == Material.BARRIER || mat.isAir()) {
                continue;
            }

            // ✔️ 첫 유효 블록 찾으면 반환
            return new Location(world, x, y + 1, z);
        }

        // fallback (못 찾은 경우)
        return center.clone();
    }

    private void stopMonsterSpawn() {
        if (monsterSpawnTask != null && !monsterSpawnTask.isCancelled()) {
            monsterSpawnTask.cancel(); monsterSpawnTask = null;
        }
    }

    // ================================================================
    // 오버타임 Phase 3: 허기 강제 고정 (무한 지속)
    // ================================================================

    private void startOvertimePhase3() {
        overtime = true; // Phase 1, 2가 skip된 경우에도 보장
        if (!config.overtime.phase3Enabled) return; // Phase 3 비활성 → 오버타임 종료

        cancelOvertimePhaseTask();
        overtimePhase            = 3;
        overtimeRemainingSeconds = -1; // 무한 지속이므로 카운트다운 없음

        MessageUtil.broadcast(config.overtime.phase3Message);
        SoundUtil.soundWitherSpawn();

        startHungerTask();
        // Phase 3는 타이머 없이 게임 종료까지 지속 → overtimePhaseTask 없음
    }

    /**
     * 허기를 강제로 1레벨에 고정
     */
    private void startHungerTask() {
        stopHungerTask();
        long intervalTicks = (long) config.overtime.hungerInterval * 20L;
        hungerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.PVP) return;
            for (Player p : getAlivePlayers()) {
                p.setFoodLevel(1);
                p.setSaturation(0f);
            }
        }, intervalTicks, intervalTicks);
    }

    private void stopHungerTask() {
        if (hungerTask != null && !hungerTask.isCancelled()) {
            hungerTask.cancel(); hungerTask = null;
        }
    }

    // ── 공통: 단계 전환 시 이전 phaseTask 정리 ────────
    private void cancelOvertimePhaseTask() {
        if (overtimePhaseTask != null && !overtimePhaseTask.isCancelled()) {
            overtimePhaseTask.cancel(); overtimePhaseTask = null;
        }
    }

    // ================================================================
    // 승리 조건 체크
    // ================================================================

    public void checkWinCondition() {
        if (state != GameState.PVP || pvpProtected) return;
        if (getAliveCount() <= 1) triggerGameEnd();
    }

    // ================================================================
    // 게임 종료
    // ================================================================

    private void triggerGameEnd() {
        if (state == GameState.ENDING || state == GameState.IDLE) return;
        state = GameState.ENDING;
        stopAllTasks();

        List<Player> winners = getAlivePlayers();
        String winnerText = winners.isEmpty() ? "무승부"
                : winners.size() == 1 ? "&e" + winners.getFirst().getName() + "&a님"
                : winners.stream()
                    .map(p -> "&e" + p.getName() + "&a님")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("무승부");

        MessageUtil.broadcastTitle("&l&aWIN!", "&l" + winnerText, 10, 80, 20);
        SoundUtil.soundWin();
        MessageUtil.broadcast(MessageUtil.PREFIX_OK + "&l승리자: " + winnerText);

        Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 100L);
    }

    public void stopGame(Player initiator) {
        if (state == GameState.IDLE) {
            MessageUtil.send(initiator, MessageUtil.PREFIX_ERROR + "게임이 진행되고 있지 않습니다.");
            SoundUtil.soundError();
            return;
        }
        MessageUtil.broadcast(MessageUtil.PREFIX_ERROR + initiator.getName() + "님이 게임을 종료했습니다.");
        forceStop();
    }

    public void forceStop() {
        if (state == GameState.ENDING || state == GameState.IDLE) return;
        state = GameState.ENDING;
        stopAllTasks();

        Location lobby = config.getLobbyLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.removePotionEffect(PotionEffectType.GLOWING);
            if (lobby != null) {
                p.teleport(lobby);
                p.setRespawnLocation(lobby, true);
            } else {
                p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
            }
        }

        if (worldManager.isFarmingWorldLoaded()) worldManager.unloadAndDeleteWorld(config.farmingWorldName);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetGame();
            MessageUtil.broadcast(MessageUtil.PREFIX_OK + "정상적으로 종료됐습니다.");
        }, 40L);
    }

    private void resetGame() {
        state = GameState.IDLE;
        playerStates.clear();
        skipVotes.clear();
        timerMinutes = 0; timerSeconds = 0;
        pvpProtected = false;
        overtime     = false; overtimePhase = 0; overtimeRemainingSeconds = 0;
        stopAllTasks();

        Location lobby = config.getLobbyLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
            p.removePotionEffect(PotionEffectType.GLOWING);
            if (lobby != null) {
                p.teleport(lobby);
                p.setRespawnLocation(lobby, true);
            }
        }
    }

    private void stopAllTasks() {
        stopTimerTask();
        stopSpectatorTask();
        stopActionBarTask();
        stopMonsterSpawn();
        stopHungerTask();
        cancelOvertimePhaseTask();
    }

    // ================================================================
    // 이벤트 핸들러
    // ================================================================

    public void onPlayerDeath(Player player) {
        if (state != GameState.PVP) return;
        if (!isParticipant(player)) return;
        playerStates.put(player.getUniqueId(), PlayerState.ELIMINATED);
        MessageUtil.broadcast(MessageUtil.PREFIX_ERROR + player.getName() + "님이 탈락했습니다!");

        if (config.lightningOnDeath) {
            player.getWorld().strikeLightningEffect(player.getLocation());
        }
    }

    public void onPlayerRespawn(Player player) {
        if (state != GameState.PVP) return;
        if (playerStates.get(player.getUniqueId()) == PlayerState.ELIMINATED) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applySpectatorGameMode(player);
                MessageUtil.send(player, "&l" + MessageUtil.PREFIX_ERROR + "탈락하셨습니다. 관전자로 전환됩니다.");
                checkWinCondition();
            }, 1L);
        }
    }

    public void onPlayerJoin(Player player) {
        if (state == GameState.IDLE || state == GameState.STARTING) {
            playerStates.putIfAbsent(player.getUniqueId(), PlayerState.NONE);
            return;
        }
        playerStates.put(player.getUniqueId(), PlayerState.SPECTATOR);
        MessageUtil.send(player, "&l" + MessageUtil.PREFIX_ERROR + "현재 게임이 진행중입니다. 관전자로 변경합니다.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applySpectatorGameMode(player);
            if (state == GameState.FARMING && worldManager.isFarmingWorldLoaded()) {
                World fw = worldManager.getFarmingWorld();
                // 생존자 중 랜덤 1명 위치로 텔레포트 (없으면 월드 중심)
                player.teleport(randomSpectatorLocation(fw, getAlivePlayers()));
            } else {
                Location loc = (state == GameState.PVP)
                        ? config.getPvpLocation() : config.getPrepLocation();
                if (loc != null) player.teleport(loc);
            }
        }, 2L);
    }

    public void onPlayerQuit(Player player) {
        if (state == GameState.IDLE) { playerStates.remove(player.getUniqueId()); return; }
        if (playerStates.get(player.getUniqueId()) == PlayerState.PARTICIPANT) {
            playerStates.put(player.getUniqueId(), PlayerState.ELIMINATED);
            MessageUtil.broadcast(MessageUtil.PREFIX_ERROR
                    + player.getName() + "님이 게임 도중 퇴장했습니다. (탈락 처리)");
            if (state == GameState.PVP)
                Bukkit.getScheduler().runTaskLater(plugin, this::checkWinCondition, 1L);
        }
    }

    // ================================================================
    // 게임모드
    // ================================================================

    private void applySpectatorGameMode(Player player)   { player.setGameMode(GameMode.SPECTATOR); }
    private void applyParticipantGameMode(Player player) { player.setGameMode(GameMode.SURVIVAL); }

    // ================================================================
    // 타이머
    // ================================================================

    private void startTimer(Runnable onEnd) {
        stopTimerTask();
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (timerSeconds <= 0 && timerMinutes <= 0) { stopTimerTask(); onEnd.run(); return; }
            timerSeconds--;
            if (timerSeconds < 0) { timerSeconds = 59; timerMinutes--; }
            checkTimerAnnouncements();
        }, 20L, 20L);
    }

    private void checkTimerAnnouncements() {
        if (timerSeconds == 0 && config.announcementMinutes.contains(timerMinutes)) {
            MessageUtil.broadcast(MessageUtil.PREFIX_OK + timerMinutes + "분 남았습니다.");
            SoundUtil.soundClick();
        }
        for (int[] special : config.specialAnnouncements) {
            if (timerMinutes == special[0] && timerSeconds == special[1]) {
                MessageUtil.broadcast(MessageUtil.PREFIX_OK
                        + timerMinutes + "분 " + timerSeconds + "초 남았습니다.");
                SoundUtil.soundClick();
            }
        }
        if (timerMinutes == 0 && config.announcementSeconds.contains(timerSeconds)) {
            MessageUtil.broadcast(MessageUtil.PREFIX_OK + timerSeconds + "초 남았습니다.");
            SoundUtil.soundClick();
        }
    }

    private void stopTimerTask() {
        if (timerTask != null && !timerTask.isCancelled()) { timerTask.cancel(); timerTask = null; }
    }

    // ================================================================
    // 관전자 감시 태스크
    // ================================================================

    private void startSpectatorWatchTask() {
        stopSpectatorTask();
        spectatorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.FARMING) return;
            World fw = worldManager.getFarmingWorld();
            if (fw == null) return;
            List<Player> alive = getAlivePlayers();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerState ps = playerStates.getOrDefault(p.getUniqueId(), PlayerState.NONE);
                // 생존자인데 파밍 월드 밖에 있으면 관전자 처리
                if (ps == PlayerState.PARTICIPANT && !p.getWorld().equals(fw)) {
                    MessageUtil.send(p, "&l" + MessageUtil.PREFIX_ERROR
                            + "현재 게임이 진행중입니다. 관전자로 변경합니다.");
                    playerStates.put(p.getUniqueId(), PlayerState.SPECTATOR);
                    p.teleport(randomSpectatorLocation(fw, alive));
                    applySpectatorGameMode(p);
                }
                // 게임모드 동기화
                if ((ps == PlayerState.SPECTATOR || ps == PlayerState.ELIMINATED)
                        && p.getGameMode() != GameMode.SPECTATOR)
                    applySpectatorGameMode(p);
                if (ps == PlayerState.PARTICIPANT && p.getGameMode() == GameMode.SPECTATOR)
                    applyParticipantGameMode(p);
            }
        }, 0L, 20L);
    }

    private void stopSpectatorTask() {
        if (spectatorTask != null && !spectatorTask.isCancelled()) {
            spectatorTask.cancel(); spectatorTask = null;
        }
    }

    // ================================================================
    // 액션바 태스크
    // ================================================================

    private void startActionBarTask() {
        stopActionBarTask();
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerState ps = playerStates.getOrDefault(p.getUniqueId(), PlayerState.NONE);
                if (ps == PlayerState.SPECTATOR || ps == PlayerState.ELIMINATED)
                    MessageUtil.actionBar(p, "&l&a당신은 관전자 입니다.");
            }
        }, 0L, 2L);
    }

    private void stopActionBarTask() {
        if (actionBarTask != null && !actionBarTask.isCancelled()) {
            actionBarTask.cancel(); actionBarTask = null;
        }
    }

    // ================================================================
    // 유틸
    // ================================================================

    /**
     * 관전자를 생존자 중 랜덤 1명 위치로 텔레포트할 Location 반환.
     * 생존자가 없으면 월드 스폰 근처의 안전한 위치를 반환.
     */
    private Location randomSpectatorLocation(World world, List<Player> alivePlayers) {
        if (!alivePlayers.isEmpty()) {
            Player target = alivePlayers.get(rand.nextInt(alivePlayers.size()));
            return target.getLocation();
        }
        // 생존자 없음 → 월드 중심 안전 위치
        int x = config.borderCenterX;
        int z = config.borderCenterZ;
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private void broadcastEndCountdown(int seconds, Runnable onComplete) {
        SoundUtil.soundWitherSpawn();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            MessageUtil.broadcast(MessageUtil.PREFIX_INFO + "잠시후 이동합니다.");
            SoundUtil.soundClick();
            final int[] remaining = {seconds};

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (remaining[0] > 0) {
                        MessageUtil.broadcast("&l" + MessageUtil.PREFIX_INFO + remaining[0] + "초 남았습니다.");
                        SoundUtil.soundCountdownHigh();
                        remaining[0]--;
                    } else {
                        this.cancel();
                        onComplete.run();
                    }
                }
            }.runTaskTimer(plugin, 60L, 20L);
        }, 20L);
    }

    public void debugSetTimer(int minutes, int seconds) {
        this.timerMinutes = minutes;
        this.timerSeconds = seconds;
    }

    public void debugStopTimer() { stopTimerTask(); }
}
