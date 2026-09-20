package solGameverse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Scanner;

public class GameverseManager {

    private Connection connection;

    private String url = "jdbc:mysql://localhost:3306/gameverse_examen";
    private String user = "root";
    private String password = "super3";

    // Creem mètode public per instanciar la connexió i poder-la utilitzar en tots els mètodes
    public GameverseManager() {
        try {
            this.connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            System.out.println("No s'ha pogut dur a terme la connexió: " + e.getMessage());
        }
    }

    // Booleà per comprovar si la connexió està oberta
    public boolean estaConnectat() {
        return connection != null;
    }

    // Mètode per tancar la connexió
    public void tancaConnexio() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            System.out.println("Error tancant la connexió: " + e.getMessage());
        }
    }

    // Fem aquí el main però es podria fer a una classe a part
    public static void main(String[] args) {
        GameverseManager manager = new GameverseManager();
        if (!manager.estaConnectat()) {
            return; // sense connexió no té sentit continuar
        }
        Scanner sc = new Scanner(System.in);

        // EXERCICI 1
        // EXEMPLE D'ENTRADA PER CONSOLA:
        // Mario Party
        // Puzzle
        // 52,99
        System.out.println("Videojoc inserit? "
                + manager.afegirVideojoc(sc.nextLine(), sc.nextLine(), sc.nextDouble()));
        sc.nextLine(); // consumir el salt de línia que deixa nextDouble()

        // EXERCICI 2
        // EXEMPLE D'ENTRADA PER CONSOLA:
        // Simulació
        System.out.println("Videojocs esborrats: " + manager.eliminarVideojocsPerGenere(sc.nextLine()));

        // EXERCICI 3
        // EXEMPLE D'ENTRADA PER CONSOLA:
        // 4,50
        // 0,25
        System.out.println("Gèneres modificats: " + manager.modificarDificultat(sc.nextDouble(), sc.nextDouble()));

        // EXERCICI 4
        manager.estadistiquesGeneres();

        // EXERCICI 5
        manager.videojocTopGamer();

        sc.close();
        manager.tancaConnexio();
    }

    // ---------------------------------------------------------------
    // 1. Afegir videojoc (2p) - PreparedStatement
    // ---------------------------------------------------------------
    public boolean afegirVideojoc(String nom, String nom_genere, double preu) {
        // L'ID del gènere s'obté amb una subquery dins la mateixa sentència
        String query = "INSERT INTO videojocs (nom, genere_id, preu) "
                + "VALUES (?, (SELECT id FROM generes WHERE nom = ?), ?)";

        try (PreparedStatement prepSt = connection.prepareStatement(query)) {
            prepSt.setString(1, nom);
            prepSt.setString(2, nom_genere);
            prepSt.setDouble(3, preu);
            return prepSt.executeUpdate() > 0;
        } catch (SQLIntegrityConstraintViolationException e) {
            // Si el gènere no existeix, la subquery torna NULL i genere_id és NOT NULL
            System.out.println("ERROR: El gènere '" + nom_genere + "' no existeix.");
            return false;
        } catch (SQLException e) {
            System.out.println("ERROR: No s'ha pogut inserir el videojoc: " + e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------
    // 2. Eliminar videojocs per gènere (2p) - Statement
    // ---------------------------------------------------------------
    public int eliminarVideojocsPerGenere(String nom_genere) {
        // ATENCIÓ: l'enunciat obliga a fer servir Statement, per això es concatena.
        // Aquesta consulta és vulnerable a injecció SQL i un nom amb apòstrof la trencaria.
        // En un cas real s'hauria de fer amb PreparedStatement.
        String query = "DELETE FROM videojocs "
                + "WHERE genere_id = (SELECT id FROM generes WHERE nom = '" + nom_genere + "')";

        try (Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate(query);
        } catch (SQLException e) {
            System.out.println("ERROR: No s'han pogut esborrar els videojocs del gènere "
                    + nom_genere + ": " + e.getMessage());
            return 0;
        }
    }

    // ---------------------------------------------------------------
    // 3. Modificar dificultat del gènere (2p) - batch processing
    // ---------------------------------------------------------------
    public int modificarDificultat(double valor, double percent) {
        // Mitjana de valoracions dels jocs de cada gènere.
        // Un gènere sense jocs té mitjana NULL: no compleix cap de les dues condicions
        // i, per tant, no es modifica.
        String mitjana = "(SELECT AVG(v.valoracio) FROM videojocs v WHERE v.genere_id = g.id)";

        // Concatenar un double a Java fa servir sempre el punt decimal, sigui quin sigui el locale
        String rebaixa = "UPDATE generes g SET dificultat_mitjana = dificultat_mitjana * " + (1 - percent)
                + " WHERE " + mitjana + " < " + valor;
        String incrementa = "UPDATE generes g SET dificultat_mitjana = dificultat_mitjana * " + (1 + percent)
                + " WHERE " + mitjana + " >= " + valor;

        try (Statement stmt = connection.createStatement()) {
            stmt.addBatch(rebaixa);
            stmt.addBatch(incrementa);

            int total = 0;
            for (int r : stmt.executeBatch()) {
                // Alguns drivers retornen SUCCESS_NO_INFO (-2) en lloc del nombre de files
                total += (r >= 0) ? r : 1;
            }
            return total;
        } catch (SQLException e) {
            System.out.println("ERROR: No s'ha pogut dur a terme la modificació de dificultat: "
                    + e.getMessage());
            return 0;
        }
    }

    // ---------------------------------------------------------------
    // 4. Estadístiques per gèneres (2p)
    // ---------------------------------------------------------------
    public void estadistiquesGeneres() {
        // LEFT JOIN perquè també surtin els gèneres actius sense cap videojoc (amb 0)
        String query = "SELECT g.nom, "
                + "COUNT(v.id) AS nombre_videojocs, "
                + "ROUND(AVG(v.preu), 2) AS preu_mitja, "
                + "ROUND(AVG(v.valoracio), 2) AS valoracio_mitjana, "
                + "COALESCE(SUM(v.preu * v.vendes), 0) AS recaptacio_total "
                + "FROM generes g "
                + "LEFT JOIN videojocs v ON v.genere_id = g.id "
                + "WHERE g.actiu = TRUE "
                + "GROUP BY g.id, g.nom "
                + "ORDER BY nombre_videojocs DESC, g.nom";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            System.out.println("\nEstadístiques dels gèneres:");
            while (rs.next()) {
                // printf evita la notació científica que faria println amb valors >= 10^7
                System.out.printf("NOM: %s, NUM VIDEOJOCS: %d, PREU MITJÀ: %.2f, "
                                + "VALORACIÓ MITJANA: %.2f, RECAPTACIÓ TOTAL: %.2f%n",
                        rs.getString("nom"),
                        rs.getInt("nombre_videojocs"),
                        rs.getDouble("preu_mitja"),
                        rs.getDouble("valoracio_mitjana"),
                        rs.getDouble("recaptacio_total"));
            }
        } catch (SQLException e) {
            System.out.println("ERROR: No s'han pogut obtenir les estadístiques: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // 5. Videojoc Top Gamer (2p)
    // ---------------------------------------------------------------
    public void videojocTopGamer() {
        // Videojoc i gènere actius; valoració més alta i, en cas d'empat, més vendes
        String query = "SELECT v.nom AS nom_v, g.nom AS nom_g, v.preu, v.vendes, "
                + "v.valoracio, g.dificultat_mitjana "
                + "FROM videojocs v JOIN generes g ON g.id = v.genere_id "
                + "WHERE v.actiu = TRUE AND g.actiu = TRUE "
                + "ORDER BY v.valoracio DESC, v.vendes DESC "
                + "LIMIT 1";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            System.out.println("\nVIDEOJOC TOP GAMER:");
            if (rs.next()) {
                System.out.println("\tNOM: " + rs.getString("nom_v"));
                System.out.println("\tGÈNERE: " + rs.getString("nom_g"));
                System.out.printf("\tPREU: %.2f%n", rs.getDouble("preu"));
                System.out.println("\tVENDES: " + rs.getInt("vendes"));
                System.out.printf("\tVALORACIÓ: %.1f%n", rs.getDouble("valoracio"));
                System.out.printf("\tDIFICULTAT: %.2f%n", rs.getDouble("dificultat_mitjana"));
            } else {
                System.out.println("\tNo hi ha cap videojoc que compleixi els requisits.");
            }
        } catch (SQLException e) {
            System.out.println("ERROR: No s'ha pogut obtenir el videojoc top gamer: " + e.getMessage());
        }
    }
}

