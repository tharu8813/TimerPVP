package org.pdt.timerPVP.config;

import org.bukkit.Difficulty;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.file.FileConfiguration;
import org.pdt.timerPVP.TimerPVP;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

/**
 * 오버타임 관련 설정
 *
 * 단계 흐름 (PVP 시작 후 delay_seconds 뒤부터):
 *   Phase 1: 난이도 어려움  (phase1.duration_seconds 초 지속)
 *   Phase 2: 몬스터 소환   (phase2.duration_seconds 초 지속)
 *   Phase 3: 허기 강제 고정 (게임 종료까지 무한 지속)
 */
public class OvertimeConfig {

    public boolean enabled;

    /** PVP 시작 후 몇 초 뒤에 1단계를 시작할지 */
    public int delaySeconds;

    /** 2단계 시작 시 생존자 전원에게 발광 효과 적용 */
    public boolean glowing;

    // ── Phase 1: 난이도 어려움 ───────────────────────
    public boolean phase1Enabled;
    public int     phase1Duration;
    public Difficulty phase1Difficulty;
    public String  phase1Message;

    // ── Phase 2: 몬스터 소환 ─────────────────────────
    public boolean phase2Enabled;
    public int     phase2Duration;
    public int     spawnInterval;
    public boolean randomSpawn;
    public int     spawnRadius;
    public List<SpawnEntry>    spawnList  = new ArrayList<>();
    public List<WeightedEntry> randomPool = new ArrayList<>();
    public String  phase2Message;

    // ── Phase 3: 허기 강제 고정 (무한 지속) ──────────
    public boolean phase3Enabled;
    public int     hungerInterval;
    public String  phase3Message;

    // ── 레코드 ──────────────────────────────────────
    public record SpawnEntry(EntityType type, int count) {}
    public record WeightedEntry(EntityType type, int weight) {}

    public void load(TimerPVP plugin) {
        FileConfiguration cfg = plugin.getConfig();

        enabled      = cfg.getBoolean("overtime.enabled", true);
        delaySeconds = cfg.getInt("overtime.delay_seconds", 60);
        glowing      = cfg.getBoolean("overtime.glowing", true);

        // ── Phase 1 ──────────────────────────────────
        phase1Enabled  = cfg.getBoolean("overtime.phase1.enabled", true);
        phase1Duration = cfg.getInt("overtime.phase1.duration_seconds", 120);
        phase1Message  = cfg.getString("overtime.phase1.message",
                "&c&l오버타임 1단계! 난이도가 어려움으로 변경됩니다.");

        String diffStr = cfg.getString("overtime.phase1.difficulty", "HARD");
        try {
            phase1Difficulty = Difficulty.valueOf(diffStr.toUpperCase());
        } catch (Exception e) {
            phase1Difficulty = Difficulty.HARD;
            plugin.getLogger().warning("overtime.phase1.difficulty 값이 잘못됨: " + diffStr);
        }

        // ── Phase 2 ──────────────────────────────────
        phase2Enabled  = cfg.getBoolean("overtime.phase2.enabled", true);
        phase2Duration = cfg.getInt("overtime.phase2.duration_seconds", 180);
        spawnInterval  = cfg.getInt("overtime.phase2.spawn_interval_seconds", 20);
        randomSpawn    = cfg.getBoolean("overtime.phase2.random_spawn", true);
        spawnRadius    = cfg.getInt("overtime.phase2.spawn_radius", 20);
        phase2Message  = cfg.getString("overtime.phase2.message",
                "&4&l오버타임 2단계! 몬스터가 소환됩니다!");

        // 개체수 지정 목록
        spawnList.clear();
        for (var map : cfg.getMapList("overtime.phase2.spawn_list")) {
            try {
                EntityType et  = EntityType.valueOf(map.get("type").toString().toUpperCase());
                int        cnt = Integer.parseInt(map.get("count").toString());
                spawnList.add(new SpawnEntry(et, cnt));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "overtime.phase2.spawn_list 설정 오류: " + map, e);
            }
        }

        // 확률 기반 풀 (맵 형식: type + weight)
        randomPool.clear();
        for (var map : cfg.getMapList("overtime.phase2.random_pool")) {
            try {
                EntityType et     = EntityType.valueOf(map.get("type").toString().toUpperCase());
                int        weight = map.containsKey("weight")
                        ? Integer.parseInt(map.get("weight").toString()) : 1;
                randomPool.add(new WeightedEntry(et, weight));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "overtime.phase2.random_pool 설정 오류: " + map, e);
            }
        }
        // 풀이 비어있으면 기본값
        if (randomPool.isEmpty()) {
            randomPool.add(new WeightedEntry(EntityType.ZOMBIE,   3));
            randomPool.add(new WeightedEntry(EntityType.SKELETON, 3));
            randomPool.add(new WeightedEntry(EntityType.CREEPER,  2));
            randomPool.add(new WeightedEntry(EntityType.SPIDER,   2));
        }

        // ── Phase 3 ──────────────────────────────────
        phase3Enabled  = cfg.getBoolean("overtime.phase3.enabled", true);
        hungerInterval = cfg.getInt("overtime.phase3.hunger_interval_seconds", 1);
        phase3Message  = cfg.getString("overtime.phase3.message",
                "&6&l오버타임 3단계! 허기가 빠르게 줄어듭니다!");
    }

    /**
     * weight에 비례해 randomPool에서 엔티티 타입 하나를 뽑아 반환
     */
    public EntityType pickRandom(Random rand) {
        int totalWeight = randomPool.stream().mapToInt(WeightedEntry::weight).sum();
        if (totalWeight <= 0) return EntityType.ZOMBIE;
        int roll = rand.nextInt(totalWeight);
        int acc  = 0;
        for (WeightedEntry entry : randomPool) {
            acc += entry.weight();
            if (roll < acc) return entry.type();
        }
        return randomPool.getLast().type();
    }
}
