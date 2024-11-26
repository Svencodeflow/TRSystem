package de.Twincode.tRPack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.sql.*;
import java.util.*;

public class TeamSystem implements CommandExecutor, Listener {

    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private final Map<UUID, Boolean> createTeamMode = new HashMap<>();
    private final Map<UUID, Boolean> promoteMemberMode = new HashMap<>();
    private final TRPack plugin;
    private final Connection connection;
    private final ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
    private final Scoreboard scoreboard = scoreboardManager.getMainScoreboard();

    public TeamSystem(TRPack plugin, Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
        loadTeamsFromDatabase();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return false;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            openTeamGUI(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length == 3) {
                    createTeamCommand(player, args[1], args[2]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team create [Name] [tag]");
                }
                break;
            case "join":
                if (args.length == 2) {
                    joinTeamCommand(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team join [Team]");
                }
                break;
            case "disband":
                disbandTeamCommand(player);
                break;
            case "leave":
                leaveTeamCommand(player);
                break;
            case "invite":
                if (args.length == 2) {
                    invitePlayerCommand(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team invite [Spieler]");
                }
                break;
            case "color":
                if (args.length == 2) {
                    changeTeamColor(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team color [Farbe]");
                }
                break;
            case "ban":
                if (args.length == 2) {
                    banPlayerCommand(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team ban [Spieler]");
                }
                break;
            case "unban":
                if (args.length == 2) {
                    unbanPlayerCommand(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team unban [Spieler]");
                }
                break;
            case "kick":
                if (args.length == 2) {
                    kickPlayerCommand(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team kick [Spieler]");
                }
                break;
            case "allychat":
                sendAllyChatMessageCommand(player, args);
                break;
            case "promote":
                if (args.length == 2) {
                    promoteMember(player.getUniqueId(), args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team promote [Spieler]");
                }
                break;
            case "demote":
                if (args.length == 2) {
                    demoteMember(player.getUniqueId(), args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team demote [Spieler]");
                }
                break;
            case "list":
                listTeamsCommand(player);
                break;
            case "info":
                if (args.length == 3) {
                    teamInfoCommand(player, args[1], args[2]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team info [Spieler] [Team]");
                }
                break;
            case "ally":
                if (args.length == 2) {
                    allyTeamCommand(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team ally [Team]");
                }
                break;
            case "neutral":
                if (args.length == 2) {
                    neutralTeamCommand(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team neutral [Team]");
                }
                break;
            case "description":
                if (args.length == 2) {
                    changeTeamDescriptionCommand(player, args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team description");
                }
                break;
            case "title":
                if (args.length == 3) {
                    changePlayerTitleCommand(player, args[1], args[2]);
                } else {
                    player.sendMessage(ChatColor.RED + "Verwendung: /team title [Spieler] [title]");
                }
                break;
            case "chat":
                sendTeamMessage(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;
            case "top":
                listTopTeamsCommand(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unbekannter Team-Befehl.");
                break;
        }

        return true;
    }

    private void createTeamCommand(Player player, String teamName, String tag) {
        UUID playerUUID = player.getUniqueId();

        if (playerTeams.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Du bist bereits in einem Team.");
            return;
        }

        createTeam(playerUUID, teamName);
        player.sendMessage(ChatColor.GREEN + "Team '" + teamName + "' wurde erfolgreich erstellt!");
    }

    private void joinTeamCommand(Player player, String teamName) {
        UUID playerUUID = player.getUniqueId();

        if (playerTeams.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Du bist bereits in einem Team.");
            return;
        }

        for (Team team : playerTeams.values()) {
            if (team.getName().equalsIgnoreCase(teamName)) {
                team.addMember(playerUUID, "MEMBER");
                playerTeams.put(playerUUID, team);
                player.sendMessage(ChatColor.GREEN + "Du bist dem Team '" + teamName + "' beigetreten.");
                updateScoreboard(player, teamName);
                return;
            }
        }

        player.sendMessage(ChatColor.RED + "Team '" + teamName + "' nicht gefunden.");
    }

    private void disbandTeamCommand(Player player) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (!team.getOwner().equals(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Nur der Teambesitzer kann das Team auflösen.");
            return;
        }

        playerTeams.values().removeIf(t -> t.equals(team));
        player.sendMessage(ChatColor.GREEN + "Das Team '" + team.getName() + "' wurde aufgelöst.");
        removeScoreboardTeam(team);
    }

    private void leaveTeamCommand(Player player) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        team.removeMember(playerUUID);
        playerTeams.remove(playerUUID);
        player.sendMessage(ChatColor.GREEN + "Du hast das Team '" + team.getName() + "' verlassen.");
        updateScoreboardTeam(team);
    }

    private void invitePlayerCommand(Player player, String playerName) {
        Player invitedPlayer = Bukkit.getPlayer(playerName);
        if (invitedPlayer == null) {
            player.sendMessage(ChatColor.RED + "Spieler '" + playerName + "' nicht gefunden.");
            return;
        }

        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        invitedPlayer.sendMessage(ChatColor.GREEN + "Du wurdest eingeladen, dem Team '" + team.getName() + "' beizutreten.");
        // Implementiere die Logik für das Annehmen der Einladung
    }

    private void banPlayerCommand(Player player, String playerName) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (!team.getOwner().equals(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Nur der Teambesitzer kann einen Spieler bannen.");
            return;
        }

        Player bannedPlayer = Bukkit.getPlayer(playerName);
        if (bannedPlayer == null) {
            player.sendMessage(ChatColor.RED + "Spieler '" + playerName + "' nicht gefunden.");
            return;
        }

        UUID bannedPlayerUUID = bannedPlayer.getUniqueId();
        team.removeMember(bannedPlayerUUID);
        playerTeams.remove(bannedPlayerUUID);
        player.sendMessage(ChatColor.GREEN + "Der Spieler '" + playerName + "' wurde aus dem Team gebannt.");
        // Optional: Fügt den Spieler zur Bannliste hinzu
    }

    private void unbanPlayerCommand(Player player, String playerName) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (!team.getOwner().equals(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Nur der Teambesitzer kann einen Spieler entbannen.");
            return;
        }

        Player unbannedPlayer = Bukkit.getPlayer(playerName);
        if (unbannedPlayer == null) {
            player.sendMessage(ChatColor.RED + "Spieler '" + playerName + "' nicht gefunden.");
            return;
        }

        UUID unbannedPlayerUUID = unbannedPlayer.getUniqueId();
        // Implementiere die Logik, um den Spieler von der Bannliste zu entfernen
        // Optional: Fügt den Spieler wieder zur Bannliste hinzu

        player.sendMessage(ChatColor.GREEN + "Der Spieler '" + playerName + "' wurde entbannt.");
    }

    private void kickPlayerCommand(Player player, String playerName) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (!team.getOwner().equals(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Nur der Teambesitzer kann einen Spieler kicken.");
            return;
        }

        Player kickedPlayer = Bukkit.getPlayer(playerName);
        if (kickedPlayer == null) {
            player.sendMessage(ChatColor.RED + "Spieler '" + playerName + "' nicht gefunden.");
            return;
        }

        UUID kickedPlayerUUID = kickedPlayer.getUniqueId();
        team.removeMember(kickedPlayerUUID);
        playerTeams.remove(kickedPlayerUUID);
        player.sendMessage(ChatColor.GREEN + "Der Spieler '" + playerName + "' wurde aus dem Team gekickt.");
        updateScoreboardTeam(team);
    }

    private void sendAllyChatMessageCommand(Player player, String[] message) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        String chatMessage = String.join(" ", Arrays.copyOfRange(message, 1, message.length));
        for (Team allyTeam : team.getAllyTeams()) {
            for (UUID memberUUID : allyTeam.getMembers().keySet()) {
                Player allyPlayer = Bukkit.getPlayer(memberUUID);
                if (allyPlayer != null) {
                    allyPlayer.sendMessage(ChatColor.GRAY + "[Ally] " + team.getName() + " " + player.getName() + ": " + chatMessage);
                }
            }
        }
    }

    private void promoteMember(UUID ownerUUID, String memberName) {
        Player owner = Bukkit.getPlayer(ownerUUID);
        Team team = playerTeams.get(ownerUUID);

        if (team == null) {
            owner.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (!team.getOwner().equals(ownerUUID)) {
            owner.sendMessage(ChatColor.RED + "Nur der Teambesitzer kann einen Spieler befördern.");
            return;
        }

        Player member = Bukkit.getPlayer(memberName);
        if (member == null) {
            owner.sendMessage(ChatColor.RED + "Spieler '" + memberName + "' nicht gefunden.");
            return;
        }

        team.addMember(member.getUniqueId(), "CO-OWNER");
        owner.sendMessage(ChatColor.GREEN + "Der Spieler '" + memberName + "' wurde befördert.");
    }

    private void demoteMember(UUID ownerUUID, String memberName) {
        Player owner = Bukkit.getPlayer(ownerUUID);
        Team team = playerTeams.get(ownerUUID);

        if (team == null) {
            owner.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (!team.getOwner().equals(ownerUUID)) {
            owner.sendMessage(ChatColor.RED + "Nur der Teambesitzer kann einen Spieler degradieren.");
            return;
        }

        Player member = Bukkit.getPlayer(memberName);
        if (member == null) {
            owner.sendMessage(ChatColor.RED + "Spieler '" + memberName + "' nicht gefunden.");
            return;
        }

        team.addMember(member.getUniqueId(), "MEMBER");
        owner.sendMessage(ChatColor.GREEN + "Der Spieler '" + memberName + "' wurde degradiert.");
    }



    private void listTeamsCommand(Player player) {
        if (playerTeams.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Es gibt keine Teams auf dem Server.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Liste der Teams:");
        for (Team team : new HashSet<>(playerTeams.values())) {
            player.sendMessage(ChatColor.YELLOW + "- " + team.getName());
        }
    }

    private void teamInfoCommand(Player player, String playerName, String teamName) {
        Team team = null;

        if (!teamName.isEmpty()) {
            for (Team t : new HashSet<>(playerTeams.values())) {
                if (t.getName().equalsIgnoreCase(teamName)) {
                    team = t;
                    break;
                }
            }
        }

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Team '" + teamName + "' nicht gefunden.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Team: " + team.getName());
        player.sendMessage(ChatColor.GREEN + "Besitzer: " + Bukkit.getPlayer(team.getOwner()).getName());
        player.sendMessage(ChatColor.GREEN + "Mitglieder:");
        for (UUID memberUUID : team.getMembers().keySet()) {
            player.sendMessage(ChatColor.YELLOW + "- " + Bukkit.getPlayer(memberUUID).getName() + " (" + team.getMembers().get(memberUUID) + ")");
        }
    }

    private void allyTeamCommand(Player player, String teamName) {
        UUID playerUUID = player.getUniqueId();
        Team playerTeam = playerTeams.get(playerUUID);

        if (playerTeam == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        Team targetTeam = null;
        for (Team t : new HashSet<>(playerTeams.values())) {
            if (t.getName().equalsIgnoreCase(teamName)) {
                targetTeam = t;
                break;
            }
        }

        if (targetTeam == null) {
            player.sendMessage(ChatColor.RED + "Team '" + teamName + "' nicht gefunden.");
            return;
        }

        // Implementiere die Logik, um eine Allianz zu bilden
        player.sendMessage(ChatColor.GREEN + "Du hast eine Allianz mit dem Team '" + teamName + "' gebildet.");
    }

    private void neutralTeamCommand(Player player, String teamName) {
        UUID playerUUID = player.getUniqueId();
        Team playerTeam = playerTeams.get(playerUUID);

        if (playerTeam == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        Team targetTeam = null;
        for (Team t : new HashSet<>(playerTeams.values())) {
            if (t.getName().equalsIgnoreCase(teamName)) {
                targetTeam = t;
                break;
            }
        }

        if (targetTeam == null) {
            player.sendMessage(ChatColor.RED + "Team '" + teamName + "' nicht gefunden.");
            return;
        }

        // Implementiere die Logik, um die Allianz zu entfernen
        player.sendMessage(ChatColor.GREEN + "Die Allianz mit dem Team '" + teamName + "' wurde entfernt.");
    }

    private void changeTeamDescriptionCommand(Player player, String description) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (!team.getOwner().equals(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Nur der Teambesitzer kann die Beschreibung ändern.");
            return;
        }

        // Implementiere die Logik, um die Beschreibung des Teams zu ändern
        player.sendMessage(ChatColor.GREEN + "Die Beschreibung deines Teams wurde geändert zu: " + description);
    }

    private void changePlayerTitleCommand(Player player, String playerName, String title) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (!team.getOwner().equals(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Nur der Teambesitzer kann den Titel eines Spielers ändern.");
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            player.sendMessage(ChatColor.RED + "Spieler '" + playerName + "' nicht gefunden.");
            return;
        }

        // Implementiere die Logik, um den Titel des Spielers zu ändern
        player.sendMessage(ChatColor.GREEN + "Der Titel des Spielers '" + playerName + "' wurde zu '" + title + "' geändert.");
    }

    private void listTopTeamsCommand(Player player) {
        // Beispielhafte Logik zur Anzeige der besten Teams
        // Annahme: Eine Methode `calculateTeamScore` existiert, um die Team-Punktzahl zu berechnen
        Map<Team, Integer> teamScores = new HashMap<>();
        for (Team team : new HashSet<>(playerTeams.values())) {
            teamScores.put(team, calculateTeamScore(team));
        }

        List<Map.Entry<Team, Integer>> sortedTeams = new ArrayList<>(teamScores.entrySet());
        sortedTeams.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        player.sendMessage(ChatColor.GREEN + "Top Teams:");
        for (Map.Entry<Team, Integer> entry : sortedTeams) {
            player.sendMessage(ChatColor.YELLOW + entry.getKey().getName() + " - Punkte: " + entry.getValue());
        }
    }

    private int calculateTeamScore(Team team) {
        // Beispielhafte Implementierung einer Methode zur Berechnung der Team-Punktzahl
        // Hier kann jede gewünschte Logik zur Berechnung der Punkte verwendet werden
        return team.getMembers().size() * 10; // Beispiel: 10 Punkte pro Mitglied
    }



    // ... die bestehenden Methoden für die Teamverwaltung ...
}
