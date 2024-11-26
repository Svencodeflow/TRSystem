package de.Twincode.tRPack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClaimSystem implements CommandExecutor, Listener {

    private final Map<UUID, Set<Chunk>> playerClaims = new HashMap<>();
    private final Map<UUID, Boolean> claimMode = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return false;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            if (claimMode.getOrDefault(player.getUniqueId(), false)) {
                claimMode.put(player.getUniqueId(), false);
                player.sendMessage(ChatColor.GREEN + "Claim-Modus deaktiviert.");
            } else {
                claimMode.put(player.getUniqueId(), true);
                player.sendMessage(ChatColor.GREEN + "Claim-Modus aktiviert. Verwende 'C' um einen Chunk zu claimen oder 'T' für das Team.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("undo")) {
            undoLastClaim(player);
            player.sendMessage(ChatColor.GREEN + "Letzter Claim rückgängig gemacht.");
            return true;
        }

        if (args[0].equalsIgnoreCase("done")) {
            player.sendMessage(ChatColor.GREEN + "Claim abgeschlossen.");
            giveClaimDude(player);
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!claimMode.getOrDefault(player.getUniqueId(), false)) {
            return;
        }

        if (event.getAction().toString().contains("RIGHT_CLICK")) {
            Block block = event.getClickedBlock();
            if (block != null) {
                Chunk chunk = block.getChunk();
                playerClaims.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(chunk);
                player.sendMessage(ChatColor.GREEN + "Chunk " + chunk.toString() + " geclaimt.");
                event.setCancelled(true);
            }
        }
    }

    private void undoLastClaim(Player player) {
        Set<Chunk> claims = playerClaims.get(player.getUniqueId());
        if (claims != null && !claims.isEmpty()) {
            Chunk lastClaimedChunk = null;
            for (Chunk chunk : claims) {
                lastClaimedChunk = chunk;
            }
            if (lastClaimedChunk != null) {
                claims.remove(lastClaimedChunk);
                player.sendMessage(ChatColor.GREEN + "Claim für Chunk " + lastClaimedChunk.toString() + " rückgängig gemacht.");
            }
        }
    }

    private void giveClaimDude(Player player) {
        ItemStack claimDude = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = claimDude.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "ClaimDude");
            claimDude.setItemMeta(meta);
        }
        player.getInventory().addItem(claimDude);
        player.sendMessage(ChatColor.GREEN + "ClaimDude erhalten.");
    }
}
