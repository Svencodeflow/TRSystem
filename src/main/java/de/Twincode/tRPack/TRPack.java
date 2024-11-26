package de.Twincode.tRPack;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.h2.Driver; // Importiere den H2-Treiber

public final class TRPack extends JavaPlugin {
    private Connection connection;

    @Override
    public void onEnable() {
        getLogger().info("tRPack has been enabled!");

        // Standardkonfigurationsdatei speichern
        saveDefaultConfig();

        // Datenbank-Treiber registrieren
        try {
            Class.forName("org.h2.Driver");
            getLogger().info("H2-Treiber erfolgreich registriert");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            getLogger().severe("Fehler beim Registrieren des H2-Treibers: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Datenbank-Verbindung herstellen
        if (setupDatabase()) {
            TeamSystem teamSystem = new TeamSystem(this, connection);
            PlayerListener playerListener = new PlayerListener(connection);

            getCommand("trdoor").setExecutor(new DoorLock());
            getCommand("trchest").setExecutor(new ChestLock());
            getCommand("trteam").setExecutor(teamSystem);
            getCommand("trclaim").setExecutor(new ClaimSystem());

            // Event-Listener registrieren
            getServer().getPluginManager().registerEvents(new DoorLock(), this);
            getServer().getPluginManager().registerEvents(new ChestLock(), this);
            getServer().getPluginManager().registerEvents(teamSystem, this);
            getServer().getPluginManager().registerEvents(new ClaimSystem(), this);
            getServer().getPluginManager().registerEvents(playerListener, this);
        } else {
            getLogger().severe("Datenbankverbindung konnte nicht hergestellt werden. Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean setupDatabase() {
        String dbType = getConfig().getString("database.type");
        getLogger().info("Datenbanktyp: " + dbType);

        try {
            if ("h2".equalsIgnoreCase(dbType)) {
                String url = getConfig().getString("database.h2.url");
                String username = getConfig().getString("database.h2.username");
                String password = getConfig().getString("database.h2.password");
                getLogger().info("H2 Verbindungsdetails: URL=" + url + ", Benutzername=" + username + ", Passwort=" + (password.isEmpty() ? "(leer)" : "******"));
                connection = DriverManager.getConnection(url, username, password);
            } else if ("mariadb".equalsIgnoreCase(dbType)) {
                String url = getConfig().getString("database.mariadb.url");
                String username = getConfig().getString("database.mariadb.username");
                String password = getConfig().getString("database.mariadb.password");
                getLogger().info("MariaDB Verbindungsdetails: URL=" + url + ", Benutzername=" + username + ", Passwort=" + (password.isEmpty() ? "(leer)" : "******"));
                connection = DriverManager.getConnection(url, username, password);
            } else {
                throw new SQLException("Unbekannter Datenbanktyp: " + dbType);
            }

            Statement statement = connection.createStatement();
            String createTeamsTable = "CREATE TABLE IF NOT EXISTS teams (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(255) NOT NULL," +
                    "owner_uuid CHAR(36) NOT NULL" +
                    ")";
            statement.execute(createTeamsTable);

            String createMembersTable = "CREATE TABLE IF NOT EXISTS members (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "team_id INT NOT NULL," +
                    "member_uuid CHAR(36) NOT NULL," +
                    "rank VARCHAR(50) NOT NULL," +
                    "FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE" +
                    ")";
            statement.execute(createMembersTable);

            String createOnlineTimeTable = "CREATE TABLE IF NOT EXISTS player_online_time (" +
                    "player_uuid CHAR(36) PRIMARY KEY," +
                    "online_time DOUBLE NOT NULL" +
                    ")";
            statement.execute(createOnlineTimeTable);

            getLogger().info("Datenbank erfolgreich eingerichtet.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Fehler beim Einrichten der Datenbank: " + e.getMessage());
            return false;
        }
    }
}






