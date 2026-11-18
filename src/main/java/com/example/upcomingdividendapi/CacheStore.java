package com.example.upcomingdividendapi;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * ============================================================
 * CACHE STORE (backup only)
 * ============================================================
 *
 * Users are ALWAYS served from memory (DividendService).
 * This class only keeps a backup copy so data survives a
 * restart / redeploy:
 *
 * 1. Local file  cache/dividend-cache.json
 *      - written atomically (temp file + atomic move), so a
 *        crash can never leave a missing or half-written file
 *      - previous good copy kept as dividend-cache.json.bak
 *
 * 2. Neon Postgres (optional)
 *      - enabled only when the DATABASE_URL environment
 *        variable is set
 *      - one row holding the same JSON
 *      - never called while serving a user request
 *
 * On startup the newest copy (highest version) wins.
 *
 * DATABASE_URL accepts the Neon connection string as-is:
 *   postgresql://user:password@host/db?sslmode=require
 */
@Component
public class CacheStore {

    private static final Path CACHE_DIRECTORY =
            Paths.get("cache");

    private static final Path CACHE_FILE =
            CACHE_DIRECTORY.resolve("dividend-cache.json");

    private static final Path BACKUP_FILE =
            CACHE_DIRECTORY.resolve("dividend-cache.json.bak");

    private static final Path TEMP_FILE =
            CACHE_DIRECTORY.resolve("dividend-cache.json.tmp");

    private static final String TABLE_NAME =
            "dividend_app_cache";

    private static final String ROW_ID =
            "main";

    private final String jdbcUrl;

    private final Properties jdbcProperties;

    private volatile boolean tableReady = false;

    public CacheStore(
            @Value("${DATABASE_URL:}") String databaseUrl
    ) {
        String url = null;
        Properties properties = null;

        if (databaseUrl != null
                && !databaseUrl.trim().isEmpty()) {

            try {
                properties = new Properties();
                url = toJdbcUrl(databaseUrl.trim(), properties);

                System.out.println(
                        "Neon backup enabled: "
                                + url
                );

            } catch (Exception e) {
                url = null;
                properties = null;

                System.out.println(
                        "DATABASE_URL is invalid. Neon backup disabled: "
                                + e.getMessage()
                );
            }
        } else {
            System.out.println(
                    "DATABASE_URL not set. Using local file backup only."
            );
        }

        this.jdbcUrl = url;
        this.jdbcProperties = properties;
    }

    public boolean isNeonEnabled() {
        return jdbcUrl != null;
    }

    // ============================================================
    // LOAD (startup only)
    // ============================================================

    /**
     * Returns the newest cache JSON among the file, the backup
     * file and Neon, or null when none exists.
     */
    public String loadNewest() {

        String best = null;
        long bestVersion = Long.MIN_VALUE;
        String bestSource = null;

        String[] sources = {
                "file",
                "backup file",
                "Neon"
        };

        for (String source : sources) {

            String json;

            if ("file".equals(source)) {
                json = readFile(CACHE_FILE);
            } else if ("backup file".equals(source)) {
                json = readFile(BACKUP_FILE);
            } else {
                json = readNeon();
            }

            long version =
                    readVersion(json);

            if (version > bestVersion) {
                best = json;
                bestVersion = version;
                bestSource = source;
            }
        }

        if (best != null) {
            System.out.println(
                    "Cache loaded from "
                            + bestSource
                            + " (version "
                            + bestVersion
                            + ")."
            );
        } else {
            System.out.println(
                    "No saved cache found. Starting fresh."
            );
        }

        return best;
    }

    // ============================================================
    // FILE
    // ============================================================

    public void saveFile(String json) {

        if (json == null) {
            return;
        }

        try {
            Files.createDirectories(CACHE_DIRECTORY);

            Files.write(
                    TEMP_FILE,
                    json.getBytes(StandardCharsets.UTF_8)
            );

            if (Files.exists(CACHE_FILE)
                    && readVersion(readFile(CACHE_FILE)) != Long.MIN_VALUE) {

                Files.copy(
                        CACHE_FILE,
                        BACKUP_FILE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            try {
                Files.move(
                        TEMP_FILE,
                        CACHE_FILE,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        TEMP_FILE,
                        CACHE_FILE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (Exception e) {
            System.out.println(
                    "Cache file save failed: "
                            + e.getMessage()
            );
        }
    }

    private String readFile(Path path) {

        try {
            if (!Files.exists(path)) {
                return null;
            }

            String json =
                    new String(
                            Files.readAllBytes(path),
                            StandardCharsets.UTF_8
                    );

            return readVersion(json) == Long.MIN_VALUE
                    ? null
                    : json;

        } catch (IOException e) {
            System.out.println(
                    "Could not read "
                            + path
                            + ": "
                            + e.getMessage()
            );
            return null;
        }
    }

    // ============================================================
    // NEON
    // ============================================================

    /**
     * @return true when the backup was written.
     */
    public boolean saveNeon(
            String json,
            long version
    ) {
        if (!isNeonEnabled()
                || json == null) {
            return false;
        }

        try (Connection connection = openConnection()) {

            ensureTable(connection);

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 "INSERT INTO " + TABLE_NAME
                                         + " (id, version, data, updated_at)"
                                         + " VALUES (?, ?, ?, now())"
                                         + " ON CONFLICT (id) DO UPDATE SET"
                                         + " version = EXCLUDED.version,"
                                         + " data = EXCLUDED.data,"
                                         + " updated_at = now()"
                         )) {

                statement.setString(1, ROW_ID);
                statement.setLong(2, version);
                statement.setString(3, json);
                statement.executeUpdate();
            }

            return true;

        } catch (Exception e) {
            System.out.println(
                    "Neon backup save failed: "
                            + e.getMessage()
            );
            return false;
        }
    }

    private String readNeon() {

        if (!isNeonEnabled()) {
            return null;
        }

        try (Connection connection = openConnection()) {

            ensureTable(connection);

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 "SELECT data FROM "
                                         + TABLE_NAME
                                         + " WHERE id = ?"
                         )) {

                statement.setString(1, ROW_ID);

                try (ResultSet resultSet =
                             statement.executeQuery()) {

                    if (resultSet.next()) {
                        return resultSet.getString(1);
                    }
                }
            }

        } catch (Exception e) {
            System.out.println(
                    "Neon backup read failed: "
                            + e.getMessage()
            );
        }

        return null;
    }

    private Connection openConnection() throws Exception {

        DriverManager.setLoginTimeout(15);

        return DriverManager.getConnection(
                jdbcUrl,
                jdbcProperties
        );
    }

    private void ensureTable(Connection connection) throws Exception {

        if (tableReady) {
            return;
        }

        try (Statement statement =
                     connection.createStatement()) {

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                            + " id VARCHAR(50) PRIMARY KEY,"
                            + " version BIGINT NOT NULL,"
                            + " data TEXT NOT NULL,"
                            + " updated_at TIMESTAMPTZ NOT NULL DEFAULT now()"
                            + ")"
            );
        }

        tableReady = true;
    }

    /**
     * Converts a Neon / Postgres URL into a JDBC URL.
     *
     * Accepts:
     *   postgresql://user:pass@host[:port]/db?sslmode=require
     *   postgres://...
     *   jdbc:postgresql://...   (used as-is)
     */
    static String toJdbcUrl(
            String databaseUrl,
            Properties properties
    ) throws Exception {

        if (databaseUrl.startsWith("jdbc:")) {
            return databaseUrl;
        }

        URI uri =
                new URI(databaseUrl);

        String scheme =
                uri.getScheme();

        if (!"postgresql".equalsIgnoreCase(scheme)
                && !"postgres".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "Expected postgresql:// URL"
            );
        }

        String host =
                uri.getHost();

        if (host == null) {
            throw new IllegalArgumentException(
                    "Missing host"
            );
        }

        int port =
                uri.getPort() > 0
                        ? uri.getPort()
                        : 5432;

        String database =
                uri.getPath() == null
                        || uri.getPath().length() <= 1
                        ? "postgres"
                        : uri.getPath().substring(1);

        String userInfo =
                uri.getUserInfo();

        if (userInfo != null) {

            int colon =
                    userInfo.indexOf(':');

            if (colon >= 0) {
                properties.setProperty("user", userInfo.substring(0, colon));
                properties.setProperty("password", userInfo.substring(colon + 1));
            } else {
                properties.setProperty("user", userInfo);
            }
        }

        String sslMode = "require";

        String query =
                uri.getQuery();

        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("sslmode=")) {
                    sslMode = part.substring("sslmode=".length());
                }
            }
        }

        properties.setProperty("sslmode", sslMode);
        properties.setProperty("connectTimeout", "15");
        properties.setProperty("socketTimeout", "30");
        properties.setProperty("ApplicationName", "upcoming-dividend-api");

        return "jdbc:postgresql://"
                + host
                + ":"
                + port
                + "/"
                + database;
    }

    private static long readVersion(String json) {

        if (json == null
                || json.trim().isEmpty()) {
            return Long.MIN_VALUE;
        }

        try {
            JsonElement root =
                    JsonParser.parseString(json);

            if (!root.isJsonObject()
                    || !root.getAsJsonObject().has("version")) {
                return Long.MIN_VALUE;
            }

            return root.getAsJsonObject()
                    .get("version")
                    .getAsLong();

        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }
}
