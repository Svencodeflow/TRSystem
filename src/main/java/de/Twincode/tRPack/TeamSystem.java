package de.Twincode.tRPack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
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

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import java.sql.*;
import java.util.*;

import static org.bukkit.Bukkit.getLogger;

public class TeamSystem implements CommandExecutor, Listener {

    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private final Map<UUID, Boolean> createTeamMode = new HashMap<>();
    private final Map<UUID, Boolean> promoteMemberMode = new HashMap<>();
    private final Connection connection;
    private final ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
    private final Scoreboard scoreboard = scoreboardManager.getMainScoreboard();

    public TeamSystem(TRPack plugin, Connection connection) {
        this.connection = connection;
        loadTeamsFromDatabase();
    }

    private void loadTeamsFromDatabase() {
        try {
            // Lade Teams
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM teams");

            while (rs.next()) {
                int teamId = rs.getInt("id");
                String teamName = rs.getString("name");
                UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
                Team team = new Team(teamName, ownerUUID);

                // Lade Mitglieder des Teams
                PreparedStatement memberStmt = connection.prepareStatement("SELECT * FROM members WHERE team_id = ?");
                memberStmt.setInt(1, teamId);
                ResultSet memberRs = memberStmt.executeQuery();

                while (memberRs.next()) {
                    UUID memberUUID = UUID.fromString(memberRs.getString("member_uuid"));
                    String rank = memberRs.getString("rank");
                    team.addMember(memberUUID, rank);
                    playerTeams.put(memberUUID, team);
                }

                playerTeams.put(ownerUUID, team);
            }

            getLogger().info("Teams erfolgreich aus der Datenbank geladen.");
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Fehler beim Laden der Teams aus der Datenbank.");
        }
    }

    private int getTeamIdByTeamName(String teamName) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(
                "SELECT id FROM teams WHERE name = ?"
        );
        stmt.setString(1, teamName);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return rs.getInt("id");
        }

        throw new SQLException("Team not found");
    }

    private int getTeamIdByOwnerUUID(UUID ownerUUID) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(
                "SELECT id FROM teams WHERE owner_uuid = ?"
        );
        stmt.setString(1, ownerUUID.toString());
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return rs.getInt("id");
        }

        throw new SQLException("Team not found");
    }
    private Team getTeamById(int teamId) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM teams WHERE id = ?"
        );
        stmt.setInt(1, teamId);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            String teamName = rs.getString("name");
            UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
            Team team = new Team(teamName, ownerUUID);

            // Lade die Mitglieder des Teams
            PreparedStatement memberStmt = connection.prepareStatement(
                    "SELECT * FROM members WHERE team_id = ?"
            );
            memberStmt.setInt(1, teamId);
            ResultSet memberRs = memberStmt.executeQuery();

            while (memberRs.next()) {
                UUID memberUUID = UUID.fromString(memberRs.getString("member_uuid"));
                String rank = memberRs.getString("rank");
                team.addMember(memberUUID, rank);
            }

            return team;
        }

        throw new SQLException("Team not found");
    }

    private void updateScoreboardTeam(Team team) {
        org.bukkit.scoreboard.Team scoreboardTeam = scoreboard.getTeam(team.getName());
        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.registerNewTeam(team.getName());
            scoreboardTeam.setPrefix(team.getColor() + "[" + team.getName() + "] ");
        } else {
            // Setze das neue Präfix, falls es sich geändert hat
            scoreboardTeam.setPrefix(team.getColor() + "[" + team.getName() + "] ");
            // Entferne alle bisherigen Einträge
            for (String entry : scoreboardTeam.getEntries()) {
                scoreboardTeam.removeEntry(entry);
            }
        }
        // Füge die aktuellen Teammitglieder hinzu
        for (UUID memberUUID : team.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                scoreboardTeam.addEntry(member.getName());
            }
        }
    }
    private void updateScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team != null) {
            org.bukkit.scoreboard.Team scoreboardTeam = scoreboard.getTeam(team.getName());
            if (scoreboardTeam == null) {
                scoreboardTeam = scoreboard.registerNewTeam(team.getName());
                scoreboardTeam.setPrefix(ChatColor.BLUE + "[" + team.getName() + "] ");
            }
            scoreboardTeam.addEntry(player.getName());
            player.setScoreboard(scoreboard);
        }
    }





    private void updateTeamGUI(Player player) {
        Inventory teamGUI = player.getOpenInventory().getTopInventory();
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team != null) {
            ItemStack teamInfoItem = new ItemStack(Material.PAPER);
            ItemMeta teamInfoMeta = teamInfoItem.getItemMeta();
            teamInfoMeta.setDisplayName(ChatColor.BLUE + "Team Informationen");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Team Name: " + ChatColor.WHITE + team.getName());
            lore.add(ChatColor.GRAY + "Besitzer: " + ChatColor.WHITE + Bukkit.getPlayer(team.getOwner()).getName());
            teamInfoMeta.setLore(lore);
            teamInfoItem.setItemMeta(teamInfoMeta);
            teamGUI.setItem(2, teamInfoItem);
        }

        player.updateInventory();
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

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                handleCreateCommand(player, args);
                break;
            case "join":
                handleJoinCommand(player, args);
                break;
            case "disband":
                disbandTeamCommand(player);
                break;
            case "leave":
                leaveTeamCommand(player);
                break;
            case "invite":
                handleInviteCommand(player, args);
                break;
            case "color":
                handleColorCommand(player, args);
                break;
            case "ban":
                handleBanCommand(player, args);
                break;
            case "unban":
                handleUnbanCommand(player, args);
                break;
            case "kick":
                handleKickCommand(player, args);
                break;
            case "allychat":
                sendAllyChatMessageCommand(player, args);
                break;
            case "promote":
                handlePromoteCommand(player, args);
                break;
            case "demote":
                handleDemoteCommand(player, args);
                break;
            case "list":
                listTeamsCommand(player);
                break;
            case "info":
                handleInfoCommand(player, args);
                break;
            case "ally":
                handleAllyCommand(player, args);
                break;
            case "neutral":
                handleNeutralCommand(player, args);
                break;
            case "description":
                handleDescriptionCommand(player, args);
                break;
            case "title":
                handleTitleCommand(player, args);
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

    private void handleCreateCommand(Player player, String[] args) {
        if (args.length == 3) {
            createTeamCommand(player, args[1], args[2]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team create [Name] [tag]");
        }
    }

    private void handleJoinCommand(Player player, String[] args) {
        if (args.length == 2) {
            joinTeamCommand(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team join [Team]");
        }
    }

    private void handleInviteCommand(Player player, String[] args) {
        if (args.length == 2) {
            invitePlayerCommand(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team invite [Spieler]");
        }
    }

    private void handleColorCommand(Player player, String[] args) {
        if (args.length == 2) {
            changeTeamColor(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team color [Farbe]");
        }
    }

    private void handleBanCommand(Player player, String[] args) {
        if (args.length == 2) {
            banPlayerCommand(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team ban [Spieler]");
        }
    }

    private void handleUnbanCommand(Player player, String[] args) {
        if (args.length == 2) {
            unbanPlayerCommand(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team unban [Spieler]");
        }
    }

    private void handleKickCommand(Player player, String[] args) {
        if (args.length == 2) {
            kickPlayerCommand(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team kick [Spieler]");
        }
    }

    private void handlePromoteCommand(Player player, String[] args) {
        if (args.length == 2) {
            promoteMember(player.getUniqueId(), args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team promote [Spieler]");
        }
    }

    private void handleDemoteCommand(Player player, String[] args) {
        if (args.length == 2) {
            demoteMember(player.getUniqueId(), args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team demote [Spieler]");
        }
    }

    private void handleInfoCommand(Player player, String[] args) {
        if (args.length == 3) {
            teamInfoCommand(player, args[1], args[2]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team info [Spieler] [Team]");
        }
    }

    private void handleAllyCommand(Player player, String[] args) {
        if (args.length == 2) {
            allyTeamCommand(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team ally [Team]");
        }
    }

    private void handleNeutralCommand(Player player, String[] args) {
        if (args.length == 2) {
            neutralTeamCommand(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team neutral [Team]");
        }
    }

    private void handleDescriptionCommand(Player player, String[] args) {
        if (args.length == 2) {
            changeTeamDescriptionCommand(player, args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team description");
        }
    }

    private void handleTitleCommand(Player player, String[] args) {
        if (args.length == 3) {
            changePlayerTitleCommand(player, args[1], args[2]);
        } else {
            player.sendMessage(ChatColor.RED + "Verwendung: /team title [Spieler] [title]");
        }
    }

    private void createTeamCommand(UUID ownerUUID, String teamName) {
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO teams (name, owner_uuid) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, teamName);
            stmt.setString(2, ownerUUID.toString());
            stmt.executeUpdate();

            ResultSet generatedKeys = stmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                int teamId = generatedKeys.getInt(1);

                Team newTeam = new Team(teamName, ownerUUID);
                playerTeams.put(ownerUUID, newTeam);

                PreparedStatement memberStmt = connection.prepareStatement(
                        "INSERT INTO members (team_id, member_uuid, rank) VALUES (?, ?, ?)");
                memberStmt.setInt(1, teamId);
                memberStmt.setString(2, ownerUUID.toString());
                memberStmt.setString(3, "OWNER");
                memberStmt.executeUpdate();

                Player player = Bukkit.getPlayer(ownerUUID);
                if (player != null) {
                    player.sendMessage(ChatColor.GREEN + "Team '" + teamName + "' wurde erfolgreich erstellt!");
                    updateScoreboard(player, teamName);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private void joinTeamCommand(Player player, String teamName) {
        UUID playerUUID = player.getUniqueId();

        if (playerTeams.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Du bist bereits in einem Team.");
            return;
        }

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT id FROM teams WHERE name = ?");
            stmt.setString(1, teamName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int teamId = rs.getInt("id");

                PreparedStatement memberStmt = connection.prepareStatement(
                        "INSERT INTO members (team_id, member_uuid, rank) VALUES (?, ?, ?)");
                memberStmt.setInt(1, teamId);
                memberStmt.setString(2, playerUUID.toString());
                memberStmt.setString(3, "MEMBER");
                memberStmt.executeUpdate();

                Team team = getTeamById(teamId);
                if (team != null) {
                    team.addMember(playerUUID, "MEMBER");
                    playerTeams.put(playerUUID, team);
                    player.sendMessage(ChatColor.GREEN + "Du bist dem Team '" + teamName + "' beigetreten.");
                    updateScoreboard(player, teamName);
                }
            } else {
                player.sendMessage(ChatColor.RED + "Team '" + teamName + "' nicht gefunden.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Beitritt zum Team.");
        }
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

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM teams WHERE owner_uuid = ?");
            stmt.setString(1, playerUUID.toString());
            stmt.executeUpdate();

            PreparedStatement memberStmt = connection.prepareStatement(
                    "DELETE FROM members WHERE team_id = ?");
            memberStmt.setInt(1, getTeamIdByOwnerUUID(playerUUID));
            memberStmt.executeUpdate();

            playerTeams.values().removeIf(t -> t.equals(team));
            player.sendMessage(ChatColor.GREEN + "Das Team '" + team.getName() + "' wurde aufgelöst.");
            removeScoreboardTeam(team);
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Auflösen des Teams.");
        }
    }


    private void leaveTeamCommand(Player player) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        if (team.getOwner().equals(playerUUID)) {
            player.sendMessage(ChatColor.RED + "Teambesitzer können ihr Team nicht verlassen.");
            return;
        }

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM members WHERE team_id = ? AND member_uuid = ?");
            stmt.setInt(1, getTeamIdByTeamName(team.getName()));
            stmt.setString(2, playerUUID.toString());
            stmt.executeUpdate();

            team.removeMember(playerUUID);
            playerTeams.remove(playerUUID);
            player.sendMessage(ChatColor.GREEN + "Du hast das Team '" + team.getName() + "' verlassen.");
            updateScoreboardTeam(team);
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Verlassen des Teams.");
        }
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
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM members WHERE team_id = ? AND member_uuid = ?");
            stmt.setInt(1, getTeamIdByTeamName(team.getName()));
            stmt.setString(2, bannedPlayerUUID.toString());
            stmt.executeUpdate();

            PreparedStatement banStmt = connection.prepareStatement(
                    "INSERT INTO banned_players (team_id, player_uuid) VALUES (?, ?)");
            banStmt.setInt(1, getTeamIdByTeamName(team.getName()));
            banStmt.setString(2, bannedPlayerUUID.toString());
            banStmt.executeUpdate();

            team.removeMember(bannedPlayerUUID);
            playerTeams.remove(bannedPlayerUUID);
            player.sendMessage(ChatColor.GREEN + "Der Spieler '" + playerName + "' wurde aus dem Team gebannt.");
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Bannen des Spielers.");
        }
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
        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM banned_players WHERE team_id = ? AND player_uuid = ?");
            stmt.setInt(1, getTeamIdByTeamName(team.getName()));
            stmt.setString(2, unbannedPlayerUUID.toString());
            stmt.executeUpdate();

            player.sendMessage(ChatColor.GREEN + "Der Spieler '" + playerName + "' wurde entbannt.");
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Entbannen des Spielers.");
        }
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
        Team team = getTeamByNameOrPlayer(playerName, teamName);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Team oder Spieler nicht gefunden.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Team: " + team.getName());
        player.sendMessage(ChatColor.GREEN + "Besitzer: " + Bukkit.getPlayer(team.getOwner()).getName());
        player.sendMessage(ChatColor.GREEN + "Mitglieder:");
        for (UUID memberUUID : team.getMembers().keySet()) {
            player.sendMessage(ChatColor.YELLOW + "- " + Bukkit.getPlayer(memberUUID).getName() + " (" + team.getMembers().get(memberUUID) + ")");
        }
    }

    private Team getTeamByNameOrPlayer(String playerName, String teamName) {
        if (!teamName.isEmpty()) {
            for (Team t : new HashSet<>(playerTeams.values())) {
                if (t.getName().equalsIgnoreCase(teamName)) {
                    return t;
                }
            }
        }

        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            return playerTeams.get(player.getUniqueId());
        }

        return null;
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

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO alliances (team1_id, team2_id) VALUES (?, ?)"
            );
            stmt.setInt(1, getTeamIdByTeamName(playerTeam.getName()));
            stmt.setInt(2, getTeamIdByTeamName(targetTeam.getName()));
            stmt.executeUpdate();

            player.sendMessage(ChatColor.GREEN + "Du hast eine Allianz mit dem Team '" + teamName + "' gebildet.");
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Bilden der Allianz.");
        }
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

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM alliances WHERE (team1_id = ? AND team2_id = ?) OR (team1_id = ? AND team2_id = ?)"
            );
            int playerTeamId = getTeamIdByTeamName(playerTeam.getName());
            int targetTeamId = getTeamIdByTeamName(targetTeam.getName());
            stmt.setInt(1, playerTeamId);
            stmt.setInt(2, targetTeamId);
            stmt.setInt(3, targetTeamId);
            stmt.setInt(4, playerTeamId);
            stmt.executeUpdate();

            player.sendMessage(ChatColor.GREEN + "Die Allianz mit dem Team '" + teamName + "' wurde entfernt.");
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Entfernen der Allianz.");
        }
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

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE teams SET description = ? WHERE owner_uuid = ?"
            );
            stmt.setString(1, description);
            stmt.setString(2, playerUUID.toString());
            stmt.executeUpdate();

            player.sendMessage(ChatColor.GREEN + "Die Beschreibung deines Teams wurde geändert zu: " + description);
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Ändern der Teambeschreibung.");
        }
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

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE members SET title = ? WHERE member_uuid = ? AND team_id = ?"
            );
            stmt.setString(1, title);
            stmt.setString(2, targetPlayer.getUniqueId().toString());
            stmt.setInt(3, getTeamIdByTeamName(team.getName()));
            stmt.executeUpdate();

            player.sendMessage(ChatColor.GREEN + "Der Titel des Spielers '" + playerName + "' wurde zu '" + title + "' geändert.");
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Ändern des Titels.");
        }
    }


    private void listTopTeamsCommand(Player player) {
        Map<Team, Double> teamScores = new HashMap<>();
        for (Team team : new HashSet<>(playerTeams.values())) {
            teamScores.put(team, calculateTeamScore(team));
        }

        List<Map.Entry<Team, Double>> sortedTeams = new ArrayList<>(teamScores.entrySet());
        sortedTeams.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        player.sendMessage(ChatColor.GREEN + "Top Teams:");
        for (Map.Entry<Team, Double> entry : sortedTeams) {
            player.sendMessage(ChatColor.YELLOW + entry.getKey().getName() + " - Punkte: " + entry.getValue());
        }
    }


    private int calculateTeamScore(Team team) {
        int score = 0;

        // Punkte pro Mitglied
        score += team.getMembers().size() * 10;

        // Punkte für PlayerKills
        for (UUID memberUUID : team.getMembers().keySet()) {
            score += getPlayerKills(memberUUID) * 1;
        }

        // Punkte für Achievements
        for (UUID memberUUID : team.getMembers().keySet()) {
            score += getPlayerAchievements(memberUUID) * 1;
        }

        // Punkte für Online-Zeit
        for (UUID memberUUID : team.getMembers().keySet()) {
            score += getPlayerOnlineHours(memberUUID) * 0.01;
        }

        return score;
    }

    private int getPlayerKills(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) {
            return 0;
        }

        return player.getStatistic(Statistic.PLAYER_KILLS); // Statistik für Spieler-Kills
    }


    private int getPlayerAchievements(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) {
            return 0;
        }

        int achievementsCount = 0;
        for (Advancement advancement : Bukkit.advancementIterator()) {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (progress.isDone()) {
                achievementsCount++;
            }
        }

        return achievementsCount;
    }


    private int getPlayerOnlineHours(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) {
            return 0;
        }

        // Erhalte die Online-Zeit aus einer gespeicherten Datenbank
        return (int) getStoredOnlineTime(playerUUID); // Diese Methode sollte die gespeicherte Online-Zeit in Stunden zurückgeben
    }

    private double getStoredOnlineTime(UUID playerUUID) {
        double onlineTime = 0.0;

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT online_time FROM player_online_time WHERE player_uuid = ?"
            );
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                onlineTime = rs.getDouble("online_time");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return onlineTime;
    }

    private void sendTeamMessage(Player player, String message) {
        UUID playerUUID = player.getUniqueId();
        Team team = playerTeams.get(playerUUID);

        if (team == null) {
            player.sendMessage(ChatColor.RED + "Du bist in keinem Team.");
            return;
        }

        for (UUID memberUUID : team.getMembers().keySet()) {
            Player teamPlayer = Bukkit.getPlayer(memberUUID);
            if (teamPlayer != null) {
                teamPlayer.sendMessage(ChatColor.GRAY + "[Team] " + team.getName() + " " + player.getName() + ": " + message);
            }
        }
    }

    private void createTeam(UUID ownerUUID, String teamName) {
        Team team = new Team(teamName, ownerUUID);
        playerTeams.put(ownerUUID, team);

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO teams (name, owner_uuid) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            stmt.setString(1, teamName);
            stmt.setString(2, ownerUUID.toString());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int teamId = rs.getInt(1);
                team.setId(teamId);
            }

            player.sendMessage(ChatColor.GREEN + "Team '" + teamName + "' wurde erfolgreich erstellt.");
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Fehler beim Erstellen des Teams.");
        }
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }
    // ... die bestehenden Methoden für die Teamverwaltung ...
}



public class Team {
    private final String name;
    private final UUID owner;
    private final Map<UUID, String> members;
    private ChatColor color;

    public Team(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
        this.members = new HashMap<>();
        this.members.put(owner, "OWNER");
        this.color = ChatColor.WHITE; // Standardfarbe
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public Map<UUID, String> getMembers() {
        return members;
    }

    public ChatColor getColor() {
        return color;
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public void addMember(UUID member, String rank) {
        members.put(member, rank);
    }

    public void removeMember(UUID member) {
        members.remove(member);
    }
}



    /*
    Teams Feature
 /team create [Name] [tag] | Erstelle ein Team
 /team join [Team] | Trete einem Team bei
 /team disband | Löst dein Team auf
 /team leave  |  Verlasse dein Team
 /team invite [Spieler] | Lade ein Spieler in dein Team ein
 /team color [Farbe] | Ändert Die Farbe deines Teams
 /team ban [Spieler] | Entfernt einen Spieler permanent aus deinem Team
 /team unban [Spieler] | Hebt den Ban eines Spielers auf
 /team kick [Spieler] | Werfe einen Spieler aus deinem Team
 /team allychat | Sende Nachrichten an deine Verbündeten
 /team promote [Spieler] | Befördere einen Spieler in deinem Team
 /team demote [Spieler] | Stufe einen Spieler in deinem Team herab
 /team list | Zeigt dir eine Liste mit den ganzen Teams auf dem Server
 /team info [Spieler] [Team] |  Schaue dir Informationen über ein Team/einen Spieler an
 /team ally [Team]  | Bilde eine Allianz mit einem anderen Team
 /team neutral [Team] | Entferne Allianzen und lehne Allianzanfragen Ab
 /team description | Ändere die Beschreibung deines Teams
 /team title [Spieler] [title] | Ändere den Titel eines Spielers in deinem Team
 /team chat | Sende ein Nachricht nur zu deinem Team
 /team top | Listet die besten Teams auf
 */

