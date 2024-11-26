package de.Twincode.tRPack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashSet;
import java.util.Set;

public class DoorLock implements CommandExecutor, Listener {

    private final Set<Block> lockedDoors = new HashSet<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return false;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Nutzung: /trdoor <lock|unlock>");
            return false;
        }

        Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock == null || !(targetBlock.getType() == Material.OAK_DOOR || targetBlock.getType() == Material.IRON_DOOR)) {
            player.sendMessage(ChatColor.RED + "Du musst auf eine Tür zeigen.");
            return false;
        }

        if (args[0].equalsIgnoreCase("lock")) {
            lockedDoors.add(targetBlock);
            player.sendMessage(ChatColor.GREEN + "Tür gesperrt.");
        } else if (args[0].equalsIgnoreCase("unlock")) {
            lockedDoors.remove(targetBlock);
            player.sendMessage(ChatColor.GREEN + "Tür entsperrt.");
        } else {
            player.sendMessage(ChatColor.RED + "Ungültige Option. Nutzung: /trdoor <lock|unlock>");
            return false;
        }

        return true;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (lockedDoors.contains(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Diese Tür ist gesperrt und kann nicht zerstört werden.");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (lockedDoors.contains(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Diese Tür ist gesperrt und kann nicht platziert werden.");
        }
    }
}
