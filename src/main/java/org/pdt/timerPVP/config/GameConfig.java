package org.pdt.timerPVP.config;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.pdt.timerPVP.TimerPVP;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * config.yml의 값을 캐싱하여 제공하는 설정 클래스
 * 플러그인 reload 시 갱신됨
 */
public class GameConfig {

    private final TimerPVP plugin;

    // 타이머
    public int farmingMinutes;
    public int farmingSeconds;
    public int prepMinutes;
    public int prepSeconds;
    public int endCountdown;

    // 게임 기본
    public int minPlayers;
    public int startCountdown;

    // 파밍 월드
    public String farmingWorldName;
    public int spreadMin;
    public int spreadMax;
    public int worldBorderSize;
    public int borderCenterX;
    public int borderCenterZ;

    // 로비/스폰
    public String lobbyWorld;
    public double lobbyX, lobbyY, lobbyZ;

    // 준비 구역
    public String prepWorld;
    public double prepX, prepY, prepZ;

    // 준비 레드스톤 트리거
    public String redstone1World;
    public int redstone1X, redstone1Y, redstone1Z;

    // PVP 구역
    public String pvpWorld;
    public double pvpX, pvpY, pvpZ;

    // PVP 레드스톤 트리거
    public String redstone2World;
    public int redstone2X, redstone2Y, redstone2Z;

    // 준비 지급 아이템
    public List<PrepItem> prepItems = new ArrayList<>();

    // 공지 타이머
    public List<Integer> announcementMinutes = new ArrayList<>();
    public List<Integer> announcementSeconds = new ArrayList<>();

    // 특별 공지 (분+초)
    public List<int[]> specialAnnouncements = new ArrayList<>();

    // 스코어보드
    public boolean scoreboardEnabled;
    public String scoreboardTitle;

    // 제한 블록
    public List<Material> blockedBlocks = new ArrayList<>();
    public boolean blockNetherPortal;
    public boolean blockEndPortal;

    // 스킵 투표
    public boolean skipVoteEnabled;

    // 보스바
    public boolean bossbarEnabled;
    public String bossbarFarmingColor;
    public String bossbarPrepColor;
    public String bossbarPvpColor;
    public String bossbarOvertimeColor;

    // 사망 효과
    public boolean lightningOnDeath;

    // 오버타임 (별도 클래스)
    public final OvertimeConfig overtime = new OvertimeConfig();

    public record PrepItem(Material material, int amount) {}

    public GameConfig(TimerPVP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        farmingMinutes  = cfg.getInt("timer.farming.minutes", 3);
        farmingSeconds  = cfg.getInt("timer.farming.seconds", 10);
        prepMinutes     = cfg.getInt("timer.preparation.minutes", 5);
        prepSeconds     = cfg.getInt("timer.preparation.seconds", 3);
        endCountdown    = cfg.getInt("timer.end_countdown", 3);

        minPlayers      = cfg.getInt("min_players", 2);
        startCountdown  = cfg.getInt("start_countdown", 5);

        farmingWorldName = cfg.getString("farming_world.name", "time_pvp");
        spreadMin       = cfg.getInt("farming_world.spread_min", 300);
        spreadMax       = cfg.getInt("farming_world.spread_max", 750);
        worldBorderSize = cfg.getInt("farming_world.world_border", 1500);
        borderCenterX   = cfg.getInt("farming_world.border_center_x", 0);
        borderCenterZ   = cfg.getInt("farming_world.border_center_z", 0);

        lobbyWorld = cfg.getString("lobby.spawn.world", "world");
        lobbyX     = cfg.getDouble("lobby.spawn.x", 2000.5);
        lobbyY     = cfg.getDouble("lobby.spawn.y", 4.0);
        lobbyZ     = cfg.getDouble("lobby.spawn.z", 2000.5);

        prepWorld  = cfg.getString("lobby.preparation_area.world", "world");
        prepX      = cfg.getDouble("lobby.preparation_area.x", 1992.5);
        prepY      = cfg.getDouble("lobby.preparation_area.y", 5.0);
        prepZ      = cfg.getDouble("lobby.preparation_area.z", 2036.5);

        redstone1World = cfg.getString("lobby.redstone_trigger_1.world", "world");
        redstone1X     = cfg.getInt("lobby.redstone_trigger_1.x", 1981);
        redstone1Y     = cfg.getInt("lobby.redstone_trigger_1.y", 13);
        redstone1Z     = cfg.getInt("lobby.redstone_trigger_1.z", 2046);

        pvpWorld = cfg.getString("pvp_arena.spawn.world", "world");
        pvpX     = cfg.getDouble("pvp_arena.spawn.x", 2065.5);
        pvpY     = cfg.getDouble("pvp_arena.spawn.y", 6.0);
        pvpZ     = cfg.getDouble("pvp_arena.spawn.z", 2005.5);

        redstone2World = cfg.getString("pvp_arena.redstone_trigger_2.world", "world");
        redstone2X     = cfg.getInt("pvp_arena.redstone_trigger_2.x", 2040);
        redstone2Y     = cfg.getInt("pvp_arena.redstone_trigger_2.y", 4);
        redstone2Z     = cfg.getInt("pvp_arena.redstone_trigger_2.z", 1976);

        // 준비 지급 아이템
        prepItems.clear();
        var itemList = cfg.getMapList("preparation_items");
        for (var map : itemList) {
            try {
                Material mat = Material.valueOf(map.get("material").toString());
                int amt = Integer.parseInt(map.get("amount").toString());
                prepItems.add(new PrepItem(mat, amt));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "preparation_items 설정 오류: " + map, e);
            }
        }

        // 공지 타이머
        announcementMinutes = cfg.getIntegerList("announcements.minutes");
        announcementSeconds = cfg.getIntegerList("announcements.seconds");

        specialAnnouncements.clear();
        var specials = cfg.getMapList("announcements.special");
        for (var map : specials) {
            try {
                int m = Integer.parseInt(map.get("minutes").toString());
                int s = Integer.parseInt(map.get("seconds").toString());
                specialAnnouncements.add(new int[]{m, s});
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "special 공지 설정 오류: " + map, e);
            }
        }

        scoreboardEnabled = cfg.getBoolean("scoreboard.enabled", true);
        scoreboardTitle   = cfg.getString("scoreboard.title", "&f[&a타이머 &4PVP&f&l]");

        // 제한 블록
        blockedBlocks.clear();
        for (String s : cfg.getStringList("restrictions.blocked_blocks")) {
            try {
                blockedBlocks.add(Material.valueOf(s));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "blocked_blocks 설정 오류: " + s, e);
            }
        }
        blockNetherPortal = cfg.getBoolean("restrictions.block_nether_portal", true);
        blockEndPortal    = cfg.getBoolean("restrictions.block_end_portal", true);

        skipVoteEnabled = cfg.getBoolean("skip_vote.enabled", true);

        bossbarEnabled      = cfg.getBoolean("bossbar.enabled", true);
        bossbarFarmingColor = cfg.getString("bossbar.farming_color", "GREEN");
        bossbarPrepColor    = cfg.getString("bossbar.preparation_color", "YELLOW");
        bossbarPvpColor     = cfg.getString("bossbar.pvp_color", "RED");
        bossbarOvertimeColor = cfg.getString("bossbar.overtime_color", "PURPLE");

        lightningOnDeath = cfg.getBoolean("death_effects.lightning_on_death", true);

        overtime.load(plugin);
    }

    /** 로비 스폰 위치 반환 (월드가 없으면 null) */
    public Location getLobbyLocation() {
        World w = plugin.getServer().getWorld(lobbyWorld);
        return w == null ? null : new Location(w, lobbyX, lobbyY, lobbyZ);
    }

    /** 준비 구역 위치 반환 */
    public Location getPrepLocation() {
        World w = plugin.getServer().getWorld(prepWorld);
        return w == null ? null : new Location(w, prepX, prepY, prepZ);
    }

    /** PVP 구역 위치 반환 */
    public Location getPvpLocation() {
        World w = plugin.getServer().getWorld(pvpWorld);
        return w == null ? null : new Location(w, pvpX, pvpY, pvpZ);
    }

    /** 레드스톤 트리거 1 위치 반환 */
    public Location getRedstone1Location() {
        World w = plugin.getServer().getWorld(redstone1World);
        return w == null ? null : new Location(w, redstone1X, redstone1Y, redstone1Z);
    }

    /** 레드스톤 트리거 2 위치 반환 */
    public Location getRedstone2Location() {
        World w = plugin.getServer().getWorld(redstone2World);
        return w == null ? null : new Location(w, redstone2X, redstone2Y, redstone2Z);
    }
}
