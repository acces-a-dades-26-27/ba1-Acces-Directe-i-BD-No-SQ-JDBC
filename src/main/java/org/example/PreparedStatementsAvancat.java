package org.example;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PreparedStatementsAvancat {

    public static void main(String[] args) {
        // Dades de la connexió
        String url = "jdbc:mysql://localhost:3306/house";
        String user = "root";
        String password = "super3";

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connexió exitosa!");

            // 1) INJECCIÓ SQL: mateixa consulta, dues maneres
            // Un usuari "normal" escriu 'Cuina Principal'. Un usuari malintencionat escriu:
            String entradaMaliciosa = "x' OR '1'='1";
            System.out.println("\n--- Amb Statement i concatenació (VULNERABLE) ---");
            cercaPerNomVulnerable(connection, entradaMaliciosa); // retorna TOTES les cuines
            System.out.println("\n--- Amb PreparedStatement (SEGUR) ---");
            cercaPerNomSegur(connection, entradaMaliciosa); // no retorna res: busca literalment aquest nom

            // 2) DATES: actualitzem les dues columnes noves
            LocalDate instalacio = LocalDate.of(2024, 3, 12);
            LocalDateTime neteja = LocalDateTime.now();
            actualitzaDates(connection, "Cuina Principal", instalacio, neteja);

            // 3) LIKE: cuines el nom de les quals conté un text
            System.out.println("\n--- Cuines que contenen 'Cuina' ---");
            cercaPerText(connection, "Cuina");

            // 4) BETWEEN amb dates
            System.out.println("\n--- Cuines netejades entre ahir i demà ---");
            cercaPerRangNeteja(connection, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

            // 5) WHERE DINÀMIC: tres combinacions de filtres
            System.out.println("\n--- Filtre: només text ---");
            cercaAmbFiltres(connection, "Cuina", null, null);
            System.out.println("\n--- Filtre: mida mínima 10 i amb forn ---");
            cercaAmbFiltres(connection, null, 10.0, true);
            System.out.println("\n--- Sense cap filtre ---");
            cercaAmbFiltres(connection, null, null, null);

        } catch (SQLException e) {
            System.out.println("Error al connectar amb la base de dades o executar consultes");
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // 1) Injecció SQL
    // ------------------------------------------------------------------

    // MALAMENT: el text de l'usuari passa a formar part del SQL
    // Amb l'entrada  x' OR '1'='1  la consulta acaba sent:
    //   SELECT ... WHERE name = 'x' OR '1'='1'   -> sempre cert
    private static void cercaPerNomVulnerable(Connection connection, String nom) throws SQLException {
        String query = "SELECT id, name FROM kitchen WHERE name = '" + nom + "'";
        System.out.println("SQL executat: " + query);
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(query)) {
            mostraResultats(rs);
        }
    }

    // BÉ: el ? és un buit que el driver omple com a VALOR, mai com a codi SQL
    private static void cercaPerNomSegur(Connection connection, String nom) throws SQLException {
        String query = "SELECT id, name FROM kitchen WHERE name = ?";
        try (PreparedStatement prepSt = connection.prepareStatement(query)) {
            prepSt.setString(1, nom);
            try (ResultSet rs = prepSt.executeQuery()) {
                mostraResultats(rs);
            }
        }
    }

    // ------------------------------------------------------------------
    // 2) Dates: java.time -> java.sql
    // ------------------------------------------------------------------
    private static void actualitzaDates(Connection connection, String nom, LocalDate instalacio, LocalDateTime neteja)
            throws SQLException {
        String query = "UPDATE kitchen SET installed_at = ?, last_cleaning = ? WHERE name = ?";
        try (PreparedStatement prepSt = connection.prepareStatement(query)) {
            // Columna DATE     -> java.sql.Date      <- LocalDate
            prepSt.setDate(1, Date.valueOf(instalacio));
            // Columna DATETIME -> java.sql.Timestamp <- LocalDateTime
            prepSt.setTimestamp(2, Timestamp.valueOf(neteja));
            prepSt.setString(3, nom);

            int files = prepSt.executeUpdate();
            System.out.println("\nDates actualitzades a " + files + " cuina/es.");
        }
    }

    // ------------------------------------------------------------------
    // 3) LIKE: el comodí % va DINS del paràmetre
    // ------------------------------------------------------------------
    private static void cercaPerText(Connection connection, String text) throws SQLException {
        // MALAMENT:  "... WHERE name LIKE '%?%'"  -> el ? dins de cometes és un caràcter, no un paràmetre
        String query = "SELECT id, name FROM kitchen WHERE name LIKE ? ORDER BY name";
        try (PreparedStatement prepSt = connection.prepareStatement(query)) {
            prepSt.setString(1, "%" + text + "%");
            try (ResultSet rs = prepSt.executeQuery()) {
                mostraResultats(rs);
            }
        }
    }

    // ------------------------------------------------------------------
    // 4) BETWEEN amb dues dates
    // ------------------------------------------------------------------
    private static void cercaPerRangNeteja(Connection connection, LocalDateTime inici, LocalDateTime fi)
            throws SQLException {
        String query = "SELECT id, name, last_cleaning FROM kitchen "
                + "WHERE last_cleaning BETWEEN ? AND ? ORDER BY last_cleaning";
        try (PreparedStatement prepSt = connection.prepareStatement(query)) {
            prepSt.setTimestamp(1, Timestamp.valueOf(inici));
            prepSt.setTimestamp(2, Timestamp.valueOf(fi));
            try (ResultSet rs = prepSt.executeQuery()) {
                while (rs.next()) {
                    // getTimestamp -> toLocalDateTime per tornar a java.time
                    LocalDateTime neteja = rs.getTimestamp("last_cleaning").toLocalDateTime();
                    System.out.println(rs.getInt("id") + " - " + rs.getString("name") + " - netejada: " + neteja);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 5) WHERE dinàmic: cada filtre és opcional (null = no s'aplica)
    // ------------------------------------------------------------------
    private static void cercaAmbFiltres(Connection connection, String text, Double midaMinima, Boolean ambForn)
            throws SQLException {

        // Construïm el SQL amb StringBuilder. El "WHERE 1=1" és un truc perquè tots
        // els filtres es puguin afegir amb "AND" sense haver de mirar si són el primer.
        StringBuilder sql = new StringBuilder("SELECT id, name, size_sq_meters, has_oven FROM kitchen WHERE 1=1");

        // Guardem els valors en una llista, EN EL MATEIX ORDRE que els ? del SQL
        List<Object> parametres = new ArrayList<>();

        if (text != null) {
            sql.append(" AND name LIKE ?");
            parametres.add("%" + text + "%");
        }
        if (midaMinima != null) {
            sql.append(" AND size_sq_meters >= ?");
            parametres.add(midaMinima);
        }
        if (ambForn != null) {
            sql.append(" AND has_oven = ?");
            parametres.add(ambForn);
        }
        sql.append(" ORDER BY name");

        System.out.println("SQL: " + sql);

        try (PreparedStatement prepSt = connection.prepareStatement(sql.toString())) {
            // setObject deixa que el driver triï el tipus (String, Double, Boolean...)
            // Els índexs dels ? comencen a 1, no a 0
            for (int i = 0; i < parametres.size(); i++) {
                prepSt.setObject(i + 1, parametres.get(i));
            }
            try (ResultSet rs = prepSt.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getInt("id") + " - " + rs.getString("name") + " - "
                            + rs.getDouble("size_sq_meters") + " m² - forn: " + rs.getBoolean("has_oven"));
                }
            }
        }
    }

    // Mètode auxiliar reciclat per les consultes que retornen id i name
    private static void mostraResultats(ResultSet rs) throws SQLException {
        int comptador = 0;
        while (rs.next()) {
            System.out.println(rs.getInt("id") + " - " + rs.getString("name"));
            comptador++;
        }
        System.out.println("(" + comptador + " resultats)");
    }

}

