package a_jdbc_base;

import com.mysql.cj.jdbc.Driver;

import java.sql.*;

/**
 * 通过 jdbc 连接数据库
 * 1.注册驱动
 * 2.获取连接
 * 3.创建 statement
 * 4.构造 sql 语句
 * 5.执行 sql 语句，返回结果集
 * 6.结果解析
 * 7.关闭连接
 *
 *
 */
public class AJdbcStatementQueryPart {

    public static void main(String[] args) throws SQLException {
        // 1.注册驱动 8.0以上使用com.mysql.cj.jdbc.Driver
        DriverManager.registerDriver(new Driver());
        // 2.获取连接
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/atchery",
                                                            "root",
                                                            "959452");
        // 3.创建预编译
        Statement statement = connection.createStatement();
        // 4.构造 sql 语句
        String sql = "select * from tuser";
        // 5.执行 sql 语句
        ResultSet resultSet = statement.executeQuery(sql);
        // 6.对执行结果进行分析
        while (resultSet.next()) {
            int id = resultSet.getInt("id");
            String account = resultSet.getString("account");
            String password = resultSet.getString("password");

            System.out.println("id = " + id + "; account = " + account + "; password = " + password);
        }

        // 7.关闭资源
        resultSet.close();
        statement.close();
        connection.close();
    }
}
