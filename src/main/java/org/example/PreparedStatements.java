package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class PreparedStatements {

	public static void main(String[] args) {
		// Dades de la connexió
		// Especifiquem la base de dades house (creada prèviament)
		String url = "jdbc:mysql://localhost:3306/house";
		String user = "root";
		String password = "super3";

		// Podem passar la connexió com a paràmetres
		try (Connection connection = DriverManager.getConnection(url, user, password)){
			// Comprovem
			System.out.println("Connectat a " + connection.getMetaData().getURL() + ", " + connection.getMetaData().getUserName());

			// Fem l'esquelet de la consulta
			String insertKitchen = "INSERT INTO kitchen (name, size_sq_meters, has_oven, number_of_cabinets, floor_material)"+
			"VALUES (?,?,?,?,?)";
			
			// Obrim scanner per recollir les dades
			Scanner sc = new Scanner(System.in);
			
			// Creem statement preparat amb la query de l'esquelet de la consulta
			PreparedStatement prepStatement = connection.prepareStatement(insertKitchen);

			// Omplim les dades amb SET
			prepStatement.setString(1, sc.nextLine());
			prepStatement.setFloat(2, sc.nextFloat());
			prepStatement.setBoolean(3, sc.nextBoolean());
			prepStatement.setInt(4, sc.nextInt());
			sc.nextLine();
			prepStatement.setString(5, sc.nextLine());
			
			// Executem la inserció
			prepStatement.execute();

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

			// Tancar ResultSet, Statement. La connexió es tanca automàticament.
			resultSet.close();
			prepStatement.close();


		} catch (Exception e) {
			
			e.printStackTrace();
		}

	}

}
