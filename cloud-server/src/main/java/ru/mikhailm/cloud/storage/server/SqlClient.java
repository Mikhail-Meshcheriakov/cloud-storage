package ru.mikhailm.cloud.storage.server;

import java.sql.*;

public class SqlClient {
    private static Connection connection;
    private static Statement statement;

    synchronized static public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:cloud-server/users.db");
            statement = connection.createStatement();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    synchronized static public void disconnect() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    synchronized static public String getUserLogin(String login, String password) {
        String query = String.format("SELECT login FROM users_tbl WHERE login='%s' AND password='%s'", login, password);
        try {
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                return resultSet.getString(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    synchronized static public String userRegistration(String login, String password) {
        if (getUserLogin(login, password) != null) {
            return null;
        }
        String query = String.format("INSERT INTO users_tbl (login, password) VALUES ('%s', '%s')", login, password);
        try {
            statement.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return login;
    }
}
