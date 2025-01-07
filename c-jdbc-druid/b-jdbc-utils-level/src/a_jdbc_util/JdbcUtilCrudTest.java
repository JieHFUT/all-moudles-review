package a_jdbc_util;

import java.sql.*;

public class JdbcUtilCrudTest {
    public void testInsert() throws SQLException {
        // 从连接池获取连接对象
        Connection connection = JdbcUtilA.getConnection();

        // 数据库的 CRUD 操作 ...
        String sql = "insert into tuser(account,password) values(?,?)";
        PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, "util-1");
        statement.setString(2, "123456");

        int i = statement.executeUpdate();
        ResultSet generatedKeys = statement.getGeneratedKeys();
        generatedKeys.next();
        int id = generatedKeys.getInt(1);
        System.out.println("primary id = " + id);

        // 释放连接对象
        JdbcUtilA.freeConnection(connection);
    }
}
