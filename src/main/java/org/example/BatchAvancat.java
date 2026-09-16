package org.example;

import java.sql.*;
import java.util.Arrays;
import java.util.Random;

public class BatchAvancat {
// Mida del bloc: cada MIDA_BLOC files fem un viatge al servidor
    private static final int MIDA_BLOC = 50;

    public static void main(String[] args) {
        // Dades de la connexió
        String url = "jdbc:mysql://localhost:3306/house";
        String user = "root";
        String password = "super3";

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connexió exitosa!");

            // 1) Inserció massiva en blocs: 120 cuines generades -> 3 viatges (50 + 50 + 20)
            insercioEnBlocs(connection, 120);

            // 2) Un UPDATE diferent per a cada material del terra, tot en un sol batch
            ajustaMidesPerMaterial(connection);

            // 3) Batch amb una fila que falla
            batchAmbError(connection);

        } catch (SQLException e) {
            System.out.println("Error al connectar amb la base de dades");
            e.printStackTrace();
        }
    }
    // ------------------------------------------------------------------
    // 1) PreparedStatement + blocs
    // ------------------------------------------------------------------
    private static void insercioEnBlocs(Connection connection, int quantes) throws SQLException {
        String insertKitchen = "INSERT INTO kitchen (name, size_sq_meters, has_oven, number_of_cabinets, floor_material) "
                + "VALUES (?,?,?,?,?)";
        String[] materials = { "Linoli", "Parquet", "Ceràmica", "Pedra" };
        Random rnd = new Random();

        int inserides = 0;
        int pendents = 0; // files afegides al batch que encara no s'han executat

        long tempsInici = System.currentTimeMillis();
        try (PreparedStatement prepSt = connection.prepareStatement(insertKitchen)) {

            for (int i = 1; i <= quantes; i++) {
                // Generació en memòria d'una fila (mateix esquema que farem servir a la pràctica)
                prepSt.setString(1, "Cuina generada " + System.currentTimeMillis() + "-" + i); // nom únic
                prepSt.setDouble(2, Math.round((5 + rnd.nextDouble() * 20) * 100.0) / 100.0); // 5-25 m², 2 decimals
                prepSt.setBoolean(3, rnd.nextBoolean());
                prepSt.setInt(4, rnd.nextInt(12) + 1);
                prepSt.setString(5, materials[rnd.nextInt(materials.length)]);

                prepSt.addBatch();
                pendents++;

                // Quan el bloc és ple, l'executem i buidem el comptador
                if (pendents == MIDA_BLOC) {
                    int[] resultats = prepSt.executeBatch();
                    inserides += Arrays.stream(resultats).sum();
                    System.out.println("  Bloc executat: " + resultats.length + " files");
                    pendents = 0;
                }
            }

            // BLOC RESIDUAL: les que queden quan el bucle acaba (aquí, 20). És el que tothom s'oblida.
            if (pendents > 0) {
                int[] resultats = prepSt.executeBatch();
                inserides += Arrays.stream(resultats).sum();
                System.out.println("  Bloc final: " + resultats.length + " files");
            }
        }
        long tempsFinal = System.currentTimeMillis();

        System.out.printf("Inserides %d de %d cuines en %d ms%n", inserides, quantes, tempsFinal - tempsInici);
    }

    // ------------------------------------------------------------------
    // 2) Statement + sentències diferents
    // ------------------------------------------------------------------
    private static void ajustaMidesPerMaterial(Connection connection) throws SQLException {
        // Amb Statement, cada addBatch porta el seu propi SQL complet.
        // Serveix quan les sentències no comparteixen esquelet (aquí, una operació diferent per material).
        try (Statement statement = connection.createStatement()) {
            statement.addBatch("UPDATE kitchen SET size_sq_meters = size_sq_meters + 0.5 WHERE floor_material = 'Linoli'");
            statement.addBatch("UPDATE kitchen SET size_sq_meters = size_sq_meters - 0.5 WHERE floor_material = 'Parquet'");
            statement.addBatch("UPDATE kitchen SET size_sq_meters = size_sq_meters * 1.10 WHERE floor_material = 'Pedra'");
            // La resta de materials: sense canvis

            int[] resultats = statement.executeBatch();

            // L'int[] té una posició per sentència, en el mateix ordre que els addBatch
            String[] etiquetes = { "Linoli", "Parquet", "Pedra" };
            System.out.println("\nAjust de mides per material:");
            for (int i = 0; i < resultats.length; i++) {
                System.out.println("  " + etiquetes[i] + ": " + resultats[i] + " cuines modificades");
            }
        }
    }

    // ------------------------------------------------------------------
    // 3) Quan una fila falla
    // ------------------------------------------------------------------
    private static void batchAmbError(Connection connection) {
        // La 2a inserció té un nom repetit (UNIQUE que vam posar a AlterTableIErrors), les altres són correctes
        String insertKitchen = "INSERT INTO kitchen (name, size_sq_meters, has_oven, number_of_cabinets, floor_material) "
                + "VALUES (?,?,?,?,?)";
        String[] noms = { "Cuina batch OK 1", "Cuina Principal", "Cuina batch OK 2" };

        System.out.println("\nBatch amb una fila incorrecta:");
        try (PreparedStatement prepSt = connection.prepareStatement(insertKitchen)) {
            for (String nom : noms) {
                prepSt.setString(1, nom);
                prepSt.setDouble(2, 10.0);
                prepSt.setBoolean(3, true);
                prepSt.setInt(4, 4);
                prepSt.setString(5, "Fusta");
                prepSt.addBatch();
            }
            int[] resultats = prepSt.executeBatch();
            System.out.println("  Totes correctes: " + Arrays.stream(resultats).sum() + " inserides");

        } catch (BatchUpdateException e) {
            // Subclasse de SQLException específica del batch.
            // getUpdateCounts() diu què ha passat amb cada fila:
            //   >= 0             -> files afectades per aquella sentència
            //   EXECUTE_FAILED   -> aquella sentència ha fallat
            // Amb autocommit actiu (el valor per defecte), les files correctes ja estan gravades:
            // el batch NO és "tot o res".
            int[] comptadors = e.getUpdateCounts();
            System.out.println("  Ha fallat alguna fila del batch: " + e.getMessage());
            for (int i = 0; i < comptadors.length; i++) {
                String estat = comptadors[i] == Statement.EXECUTE_FAILED ? "ERROR" : comptadors[i] + " fila/es";
                System.out.println("  Fila " + (i + 1) + " (" + noms[i] + "): " + estat);
            }

        } catch (SQLException e) {
            System.out.println("  Error inesperat");
            e.printStackTrace();
        }
    }

}

