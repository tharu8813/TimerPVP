package org.pdt.timerPVP.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * 메시지 포맷팅 및 전송 유틸리티
 * 레거시 색상 코드(&a, &4 등)를 Adventure API로 변환
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String PREFIX        = "&f[&aTimerPVP&f] ";
    public static final String PREFIX_ERROR  = "&f[&4-&f] ";
    public static final String PREFIX_OK     = "&f[&a+&f] ";
    public static final String PREFIX_INFO   = "&f[&e=&f] ";

    private MessageUtil() {}

    /** 레거시 색상 코드 문자열을 Component로 변환 */
    public static Component of(String legacyText) {
        return LEGACY.deserialize(legacyText);
    }

    /** 전체 서버 브로드캐스트 */
    public static void broadcast(String legacyText) {
        Bukkit.broadcast(of(legacyText));
    }

    /** 특정 플레이어에게 메시지 전송 */
    public static void send(Player player, String legacyText) {
        player.sendMessage(of(legacyText));
    }

    /** 특정 플레이어에게 액션바 메시지 전송 */
    public static void actionBar(Player player, String legacyText) {
        player.sendActionBar(of(legacyText));
    }

    /** 전체 플레이어에게 타이틀 표시 */
    public static void broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title t = Title.title(
            of(title),
            of(subtitle),
            Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
            )
        );
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
        }
    }

    /** 특정 플레이어에게 타이틀 표시 */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title t = Title.title(
            of(title),
            of(subtitle),
            Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
            )
        );
        player.showTitle(t);
    }

    /** 초를 "M분 S초" 형식으로 변환 */
    public static String formatTime(int minutes, int seconds) {
        return minutes + "분 " + seconds + "초";
    }

    /** 색상 코드 제거 */
    public static String stripColor(String text) {
        return text.replaceAll("&[0-9a-fklmnor]", "");
    }
}
