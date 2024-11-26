package de.Twincode.tRPack;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final Connection connection;
    private final Map<UUID, Long> loginTimes = new HashMap<>();

    public PlayerListener(Connection connection) {
        this.connection = connection;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        loginTimes.put(playerUUID, System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        Long loginTime = loginTimes.remove(playerUUID);

        if (loginTime != null) {
            long sessionTime = System.currentTimeMillis() - loginTime;
            double hours = sessionTime / 3600000.0;

            try {
                PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO player_online_time (player_uuid, online_time) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE online_time = online_time + VALUES(online_time)"
                );
                stmt.setString(1, playerUUID.toString());
                stmt.setDouble(2, hours);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
