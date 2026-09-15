package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class dbConn {

	public static void main(String[] args) {
		// Dades de la connexió
        String url = "jdbc:mysql://localhost:3306/";
        String user = "root";
        String password = "super3";
        
     // Intentar establir connexió
        try {
            // Carregar el driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Establir connexió
            Connection connection = DriverManager.getConnection(url, user, password);
            System.out.println("Connexió exitosa!");
            
         // Crear un Statement per executar la consulta SQL
            Statement statement = connection.createStatement();

            // Executar la comanda SHOW DATABASES
            String query = "SHOW DATABASES";
            ResultSet resultSet = statement.executeQuery(query);

            // Mostrar les bases de dades
            System.out.println("Llistat de bases de dades:");
            while (resultSet.next()) {
                System.out.println(resultSet.getString(1)); // Mostrar el nombre de la base de dades
            }

            // Tancar ResultSet, Statement i la connexió
            resultSet.close();
            statement.close();
            connection.close();
            
        } catch (ClassNotFoundException e) {
            System.out.println("No s'ha pogut carregar el driver JDBC");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Error al connectar amb la base de dades");
            e.printStackTrace();
        }



	}

}
