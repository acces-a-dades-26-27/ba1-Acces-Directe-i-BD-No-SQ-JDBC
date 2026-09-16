package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class BatchSimple {

    public static void main(String[] args) {
        // Dades de la connexió
        // Especifiquem la base de dades house (creada prèviament)
        String url = "jdbc:mysql://localhost:3306/house";
        String user = "root";
        String password = "super3";

        // Podem passar la connexió com a paràmetres
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // Comprovem
            System.out.println(
                    "Connectat a " + connection.getMetaData().getURL() + ", " + connection.getMetaData().getUserName());

            // Fem l'esquelet de la consulta
            String insertKitchen = "INSERT INTO kitchen (name, size_sq_meters, has_oven, number_of_cabinets, floor_material)"
                    + "VALUES (?,?,?,?,?)";

            // Creem statement preparat amb la query de l'esquelet de la consulta
            PreparedStatement prepStatement = connection.prepareStatement(insertKitchen);

            // Dades per introduïr
            Object[][] cuines = {
                    {"b1", 12.22, false, 2, "Linoli"},
                    {"b2", 2.52, true, 4, "Parquet"},
                    {"b3", 14.02, false, 3, "Alumini"},
                    {"b4", 11.99, true, 9, "Pedra"},
            };

            // Afegim operacions al batch
            for(Object[] cuina : cuines) {
                prepStatement.setString(1, (String) cuina[0]);
                prepStatement.setDouble(2, (Double) cuina[1]);
                prepStatement.setBoolean(3, (boolean) cuina[2]);
                prepStatement.setInt(4, (Integer) cuina[3]);
                prepStatement.setString(5, (String) cuina[4]);

                prepStatement.addBatch();
            }

            // Executem la inserció de tot el batch
            long tempsInici = System.currentTimeMillis();
            int[] resultats = prepStatement.executeBatch();
            long tempsFinal = System.currentTimeMillis();

            // Veiem què retorna:
            System.out.printf("Batch executat en %d ms%n", tempsFinal - tempsInici);
            System.out.printf("Captures inserides: %d de %d%n", java.util.Arrays.stream(resultats).sum(), resultats.length);


            // Realitzar un SELECT per recuperar les dades inserides
            String selectSQL = "SELECT * FROM kitchen";
            ResultSet resultSet = prepStatement.executeQuery(selectSQL);

            // Mostrar els resultats
            System.out.println("Dades de la taula 'kitchen':");
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                double size = resultSet.getDouble("size_sq_meters");
                boolean hasOven = resultSet.getBoolean("has_oven");
                int cabinets = resultSet.getInt("number_of_cabinets");
                String floor = resultSet.getString("floor_material");

                System.out.println("ID: " + id + ", Nom: " + name + ", Mida: " + size + " m², Forn?: " + hasOven
                        + ", Nombre d'armaris: " + cabinets + ", Material del terra: " + floor);
            }

            // Tancar ResultSet, Statement i la connexió
            resultSet.close();
            prepStatement.close();

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

}

