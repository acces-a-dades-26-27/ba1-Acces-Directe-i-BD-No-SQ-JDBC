package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;

/**
 * ATENCIÓ: aquest exemple NO es pot executar dues vegades seguides tal qual.
 * Per tornar-lo a executar, abans cal fer a MySQL:
 *   ALTER TABLE kitchen DROP INDEX uq_kitchen_name;
 *   ALTER TABLE kitchen DROP COLUMN installed_at, DROP COLUMN last_cleaning;
 */
public class AlterTableErrors {

    public static void main(String[] args) {
        // Dades de la connexió
        String url = "jdbc:mysql://localhost:3306/house";
        String user = "root";
        String password = "super3";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {

            System.out.println("Connexió exitosa!");

            // 1) ALTER TABLE: afegim dues columnes noves (nullables, sense valor per defecte)
            // executeUpdate serveix també per a DDL; per a CREATE/ALTER retorna 0
            String alterColumnes = "ALTER TABLE kitchen "
                    + "ADD COLUMN installed_at DATE NULL, "
                    + "ADD COLUMN last_cleaning DATETIME NULL";
            statement.executeUpdate(alterColumnes);
            System.out.println("Columnes installed_at i last_cleaning afegides.");

            // 2) ALTER TABLE: restricció UNIQUE sobre el nom de la cuina
            String alterUnique = "ALTER TABLE kitchen ADD CONSTRAINT uq_kitchen_name UNIQUE (name)";
            statement.executeUpdate(alterUnique);
            System.out.println("Restricció UNIQUE sobre name afegida.");

            // 3) Provoquem l'error: inserim un nom que ja existeix ('Cuina Principal' la va crear dbInsert)
            String insertDuplicat = "INSERT INTO kitchen (name, size_sq_meters, has_oven, number_of_cabinets, floor_material) "
                    + "VALUES ('Cuina Principal', 9.99, false, 1, 'Formigó')";

            // Try-catch propi només per a aquesta inserció: volem que el programa continuï
            try {
                statement.executeUpdate(insertDuplicat);
                System.out.println("Inserit (no hauria de passar).");

            } catch (SQLIntegrityConstraintViolationException e) {
                // Subclasse de SQLException: s'ha de capturar ABANS que la genèrica
                // Salta per claus duplicades (UNIQUE / PRIMARY KEY) i per claus foranes que no existeixen
                System.out.println("No s'ha pogut inserir: ja existeix una cuina amb aquest nom.");
                System.out.println("  Codi MySQL: " + e.getErrorCode() + " (1062 = Duplicate entry)");
                System.out.println("  Missatge: " + e.getMessage());
            }

            // 4) Comprovem que la connexió segueix viva després de l'error controlat
            System.out.println("Cuines a la taula: " + comptaCuines(statement));

        } catch (SQLException e) {
            // Qualsevol altre error (connexió, sintaxi SQL, taula inexistent...)
            System.out.println("Error de base de dades. Codi: " + e.getErrorCode());
            e.printStackTrace();
        }
    }

    // Mètode auxiliar: exemple de reciclar el Statement que ja tenim obert
    private static int comptaCuines(Statement statement) throws SQLException {
        try (java.sql.ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS total FROM kitchen")) {
            rs.next();
            return rs.getInt("total"); // l'àlies 'total' és el que ens permet fer getInt per nom
        }
    }

}

