package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class dbInsert {

	public static void main(String[] args) {
		// Dades de la connexió
		// Especifiquem la base de dades house (creada prèviament)
		String url = "jdbc:mysql://localhost:3306/house";
		String user = "root";
		String password = "super3";

		try {
			// Carregar el driver JDBC
			Class.forName("com.mysql.cj.jdbc.Driver");

			// Establir la connexió
			Connection connection = DriverManager.getConnection(url, user, password);
			System.out.println("Connexió exitosa!");

			// Crear un Statement per executar les consultes SQL
			Statement statement = connection.createStatement();

			// Crear la taula kitchen si no existeix
			String createTableSQL = "CREATE TABLE IF NOT EXISTS kitchen (" + "id INT AUTO_INCREMENT PRIMARY KEY, "
					+ "name VARCHAR(50), " + "size_sq_meters DECIMAL(5,2), " + "has_oven BOOLEAN, "
					+ "number_of_cabinets INT, " + "floor_material VARCHAR(50)" + ")";
			statement.executeUpdate(createTableSQL);
			System.out.println("Taula 'kitchen' creada exitosament o ja existent.");

			// Insertar un parell de registres a la taula
			String insertDataSQL1 = "INSERT INTO kitchen (name, size_sq_meters, has_oven, number_of_cabinets, floor_material) "
					+ "VALUES ('Cuina Principal', 20.50, true, 12, 'Ceràmica')";
			String insertDataSQL2 = "INSERT INTO kitchen (name, size_sq_meters, has_oven, number_of_cabinets, floor_material) "
					+ "VALUES ('Cuina Secundària', 15.00, false, 8, 'Fusta')";
			int registres = statement.executeUpdate(insertDataSQL1);
			registres += statement.executeUpdate(insertDataSQL2);
			System.out.println("Registres inserits amb èxit: "+registres);

			// Realitzar un SELECT per recuperar les dades inserides
			String selectSQL = "SELECT * FROM kitchen";
			ResultSet resultSet = statement.executeQuery(selectSQL);

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
			statement.close();
			connection.close();

		} catch (ClassNotFoundException e) {
			System.out.println("No es pot carregar el driver JDBC");
			e.printStackTrace();
		} catch (SQLException e) {
			System.out.println("Error al connectar amb la base de dades o executar consultes");
			e.printStackTrace();
		}

	}

}
