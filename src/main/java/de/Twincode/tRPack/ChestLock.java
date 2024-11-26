package de.Twincode.tRPack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChestLock implements CommandExecutor, Listener {

    private final Map<UUID, Set<Block>> playerLockedChests = new HashMap<>();
    private final Map<Block, Set<UUID>> chestAccess = new HashMap<>();
    private final Set<UUID> playersInLockMode = new HashSet<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return false;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            if (playersInLockMode.contains(player.getUniqueId())) {
                playersInLockMode.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Kisten-Lock-Modus deaktiviert.");
            } else {
                playersInLockMode.add(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Kisten-Lock-Modus aktiviert. Wähle Kisten mit Linksklick aus.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("trust") && args.length == 2) {
            // Implementiere die Logik für /trchest trust <spieler>
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return false;
            }
            for (Block chest : playerLockedChests.getOrDefault(player.getUniqueId(), new HashSet<>())) {
                chestAccess.computeIfAbsent(chest, k -> new HashSet<>()).add(targetPlayer.getUniqueId());
            }
            player.sendMessage(ChatColor.GREEN + "Spieler " + targetPlayer.getName() + " hat nun Zugriff auf deine Kisten.");
            return true;
        }

        //! Weitere Befehle wie /trchest trust <team> können hier hinzugefügt werden.

        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!playersInLockMode.contains(player.getUniqueId())) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.CHEST) {
                playerLockedChests.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(block);
                player.sendMessage(ChatColor.GREEN + "Kiste ausgewählt und gesperrt.");
                event.setCancelled(true); // Abbrechen, damit die Kiste nicht geöffnet wird
            }
        }
    }
}
