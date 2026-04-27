package org.pdt.timerPVP.listener;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.pdt.timerPVP.config.GameConfig;
import org.pdt.timerPVP.game.GameState;
import org.pdt.timerPVP.manager.GameManager;
import org.pdt.timerPVP.util.MessageUtil;
import org.pdt.timerPVP.util.SoundUtil;

/**
 * 게임 진행 중 발생하는 모든 이벤트를 처리하는 리스너
 */
public class GameListener implements Listener {

    private final GameManager gameManager;
    private final GameConfig  config;

    public GameListener(GameManager gameManager, GameConfig config) {
        this.gameManager = gameManager;
        this.config      = config;
    }

    // ================================================================
    // 플레이어 접속 / 퇴장
    // ================================================================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        gameManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        gameManager.onPlayerQuit(event.getPlayer());
    }

    // ================================================================
    // 사망 / 리스폰
    // ================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player    player = event.getEntity();
        GameState state  = gameManager.getState();
        if (state == GameState.PVP && gameManager.isParticipant(player)) {
            gameManager.onPlayerDeath(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        gameManager.onPlayerRespawn(event.getPlayer());
    }

    // ================================================================
    // 데미지 (EntityDamageEvent 핸들러를 하나로 통합)
    // ================================================================

    /**
     * 단계별 데미지 제어 + VOID 낙사 처리를 단일 핸들러에서 담당.
     *
     * - FARMING:
     *     • VOID 낙사 → 취소 + 로비 텔레포트
     *     • 관전자 → 취소
     * - TRANSITIONING / PREPARATION:
     *     • 모든 데미지 취소
     * - PVP:
     *     • pvpProtected(시작 직후 무적) → 취소
     *     • 관전자 → 취소
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        GameState state = gameManager.getState();

        switch (state) {
            case FARMING -> {
                // VOID 낙사: 취소 후 로비로 복귀
                if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                    event.setCancelled(true);
                    Location lobby = config.getLobbyLocation();
                    if (lobby != null) {
                        victim.teleport(lobby);
                        victim.setHealth(1.0);
                    }
                    return;
                }
                // 관전자 데미지 차단
                if (gameManager.isSpectator(victim)) {
                    event.setCancelled(true);
                }
            }
            case TRANSITIONING_TO_PREP, PREPARATION, TRANSITIONING_TO_PVP ->
                event.setCancelled(true);
            case PVP -> {
                if (gameManager.isPvpProtected()) { event.setCancelled(true); return; }
                if (gameManager.isSpectator(victim)) event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (gameManager.isSpectator(victim)) event.setCancelled(true);
    }

    // ================================================================
    // 블록 파괴 / 설치
    // ================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isSpectator(player)) { event.setCancelled(true); return; }

        GameState state = gameManager.getState();
        switch (state) {
            case TRANSITIONING_TO_PREP -> {
                SoundUtil.soundErrorPlayer(player);
                event.setCancelled(true);
            }
            case PREPARATION -> {
                if (event.getBlock().getType() != Material.WATER) {
                    event.setCancelled(true);
                    MessageUtil.send(player, "&l" + MessageUtil.PREFIX_ERROR + "현재 사용할 수 없습니다.");
                    SoundUtil.soundErrorPlayer(player);
                }
            }
            case TRANSITIONING_TO_PVP -> {
                SoundUtil.soundErrorPlayer(player);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isSpectator(player)) { event.setCancelled(true); return; }

        GameState state = gameManager.getState();
        switch (state) {
            case TRANSITIONING_TO_PREP -> {
                SoundUtil.soundErrorPlayer(player);
                event.setCancelled(true);
            }
            case PREPARATION -> {
                event.setCancelled(true);
                MessageUtil.send(player, "&l" + MessageUtil.PREFIX_ERROR + "현재 사용할 수 없습니다.");
                SoundUtil.soundErrorPlayer(player);
            }
            case TRANSITIONING_TO_PVP -> {
                SoundUtil.soundErrorPlayer(player);
                event.setCancelled(true);
            }
        }
    }

    // ================================================================
    // 플레이어 이동 (TRANSITIONING 단계 잠금 + PVP 승리 체크)
    // ================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player    player = event.getPlayer();
        GameState state  = gameManager.getState();

        // 관전자는 이동 자유
        if (gameManager.isSpectator(player)) return;

        // 단계 전환 중 블록 이동 잠금
        if (state == GameState.TRANSITIONING_TO_PREP || state == GameState.TRANSITIONING_TO_PVP) {
            Location from = event.getFrom();
            Location to   = event.getTo();
            if (to == null) return;
            if (from.getBlockX() != to.getBlockX()
                    || from.getBlockY() != to.getBlockY()
                    || from.getBlockZ() != to.getBlockZ()) {
                event.setCancelled(true);
                SoundUtil.soundErrorPlayer(player);
            }
            return;
        }

        // PVP 승리 조건 체크 (MONITOR 대신 이동 시점마다 체크)
        if (state == GameState.PVP && !gameManager.isPvpProtected()) {
            gameManager.checkWinCondition();
        }
    }

    // ================================================================
    // 우클릭 / 상호작용
    // ================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player    player = event.getPlayer();
        GameState state  = gameManager.getState();
        if (gameManager.isSpectator(player)) return;

        Block block = event.getClickedBlock();

        // 이동 잠금 단계: 우클릭 차단
        if (state == GameState.TRANSITIONING_TO_PREP || state == GameState.TRANSITIONING_TO_PVP) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    || event.getAction() == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                SoundUtil.soundErrorPlayer(player);
            }
            return;
        }

        // PVP 단계: 특정 블록 상호작용 차단
        if (state == GameState.PVP && block != null) {
            Material blockType = block.getType();

            if (config.blockedBlocks.contains(blockType)) {
                event.setCancelled(true);
                MessageUtil.send(player, "&4권한이 부족해 "
                    + getBlockKoreanName(blockType) + "을(를) 사용할 수 없습니다.");
                return;
            }

            if (config.blockNetherPortal
                    && blockType == Material.OBSIDIAN
                    && player.getInventory().getItemInMainHand().getType() == Material.FLINT_AND_STEEL) {
                event.setCancelled(true);
                MessageUtil.send(player, "&4권한이 부족해 네더포탈을 사용할 수 없습니다.");
            }
        }
    }

    // ================================================================
    // 유틸리티
    // ================================================================

    private String getBlockKoreanName(Material material) {
        return switch (material) {
            case FURNACE         -> "화로";
            case ENDER_CHEST     -> "엔더상자";
            case END_PORTAL_FRAME -> "엔드 차원문";
            default -> material.name().toLowerCase().replace("_", " ");
        };
    }
}
