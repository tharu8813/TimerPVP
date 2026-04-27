package org.pdt.timerPVP.manager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.pdt.timerPVP.TimerPVP;
import org.pdt.timerPVP.config.GameConfig;
import org.pdt.timerPVP.util.MessageUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * 파밍 월드의 생성 및 삭제를 관리하는 클래스
 * Multiverse 없이 Bukkit API만으로 동적 월드를 처리
 */
public class WorldManager {

    private final TimerPVP plugin;
    private final GameConfig config;

    public WorldManager(TimerPVP plugin, GameConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * 파밍 월드를 비동기로 생성합니다.
     * 완료 후 콜백을 메인 스레드에서 실행합니다.
     */
    public CompletableFuture<World> createFarmingWorld() {
        String worldName = config.farmingWorldName;
        CompletableFuture<World> future = new CompletableFuture<>();

        // 이미 로드된 월드 제거
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            unloadAndDeleteWorld(worldName).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> doCreateWorld(worldName, future))
            );
        } else {
            // 이전에 삭제 안 된 폴더가 있을 경우 먼저 삭제
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists()) {
                CompletableFuture.runAsync(() -> deleteDirectory(worldFolder)).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () -> doCreateWorld(worldName, future))
                );
            } else {
                doCreateWorld(worldName, future);
            }
        }
        return future;
    }

    private void doCreateWorld(String worldName, CompletableFuture<World> future) {
        try {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.type(WorldType.NORMAL);
            creator.generateStructures(true);

            World world = Bukkit.createWorld(creator);
            if (world == null) {
                future.completeExceptionally(new RuntimeException("월드 생성 실패: " + worldName));
                return;
            }

            // 월드 기본 설정
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
            world.setGameRule(GameRule.KEEP_INVENTORY, false);
            world.setTime(6000); // 낮

            // 월드보더 설정
            WorldBorder border = world.getWorldBorder();
            border.setCenter(config.borderCenterX, config.borderCenterZ);
            border.setSize(config.worldBorderSize);

            future.complete(world);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "월드 생성 중 오류 발생", e);
            future.completeExceptionally(e);
        }
    }

    /**
     * 파밍 월드에서 플레이어들을 대피시키고 월드를 언로드/삭제합니다.
     */
    public CompletableFuture<Void> unloadAndDeleteWorld(String worldName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // 이미 언로드된 경우 폴더만 삭제
            CompletableFuture.runAsync(() -> {
                File folder = new File(Bukkit.getWorldContainer(), worldName);
                if (folder.exists()) deleteDirectory(folder);
                future.complete(null);
            });
            return future;
        }

        // 플레이어 대피
        Location lobbyLoc = config.getLobbyLocation();
        for (Player p : world.getPlayers()) {
            if (lobbyLoc != null) {
                p.teleport(lobbyLoc);
            } else {
                // 폴백: 기본 월드 스폰
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean unloaded = Bukkit.unloadWorld(world, false);
            if (!unloaded) {
                plugin.getLogger().warning("월드 언로드 실패: " + worldName + " - 강제 삭제 시도");
            }
            // 비동기로 폴더 삭제
            CompletableFuture.runAsync(() -> {
                File folder = new File(Bukkit.getWorldContainer(), worldName);
                if (folder.exists()) deleteDirectory(folder);
                future.complete(null);
            });
        }, 2L);

        return future;
    }

    /** 파밍 월드 반환 (없으면 null) */
    public World getFarmingWorld() {
        return Bukkit.getWorld(config.farmingWorldName);
    }

    /** 파밍 월드가 현재 존재하는지 확인 */
    public boolean isFarmingWorldLoaded() {
        return Bukkit.getWorld(config.farmingWorldName) != null;
    }

    /**
     * spreadplayers 명령어를 Java로 구현:
     * 플레이어들을 월드 내 랜덤 위치에 분산 배치
     */
    public void spreadPlayers(World world, java.util.List<Player> players) {
        if (players.isEmpty()) return;

        int centerX = config.borderCenterX;
        int centerZ = config.borderCenterZ;
        int spread = config.spreadMax;
        java.util.Random random = new java.util.Random();

        for (Player player : players) {
            // 최대 10번 시도하여 안전한 위치 찾기
            for (int attempt = 0; attempt < 10; attempt++) {
                int x = centerX + (random.nextInt(spread * 2) - spread);
                int z = centerZ + (random.nextInt(spread * 2) - spread);

                // 거리 체크 (최소 분산 범위)
                double dist = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2));
                if (dist < config.spreadMin) continue;

                // 안전한 Y 위치 찾기
                int y = world.getHighestBlockYAt(x, z);
                Location spawnLoc = new Location(world, x + 0.5, y + 1, z + 0.5);

                player.teleport(spawnLoc);
                // 스폰 포인트 설정
                player.setBedSpawnLocation(spawnLoc, true);

                // 발 아래 블록을 sea_lantern으로 설정 (낙사 방지 플랫폼)
                Location platformLoc = spawnLoc.clone().subtract(0, 1, 0);
                platformLoc.getBlock().setType(Material.SEA_LANTERN);
                break;
            }
        }
    }

    /** 디렉토리 재귀 삭제 */
    private void deleteDirectory(File directory) {
        try {
            Path path = directory.toPath();
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "디렉토리 삭제 실패: " + directory.getPath(), e);
        }
    }
}
