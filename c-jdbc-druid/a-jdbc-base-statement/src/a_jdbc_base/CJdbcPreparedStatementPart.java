package a_jdbc_base;

import java.sql.*;
import java.util.Scanner;

/**
 * 使用预编译Statement解决注入攻击问题
 * PreparedStatement
 */
public class CJdbcPreparedStatementPart {

    public static void main(String[] args) throws ClassNotFoundException, SQLException {

        Scanner sc = new Scanner(System.in);
        System.out.println("please input your account");
        String account = sc.nextLine();
        System.out.println("please input your password");
        String password = sc.nextLine();
        sc.close();


        // 1.注册驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 2.创建连接
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/atchery",
                "root",
                "959452");

        // 3.构建预编译工具
        // 4.构造 sql 语句，然后发送

        // 创建preparedStatement
        // connection.createStatement();
        // TODO 需要传入SQL语句结构
        // TODO 要的是SQL语句结构，动态值的部分使用 ? ,  占位符！
        // TODO ?  不能加 '?'  ? 只能替代值，不能替代关键字和容器名
        String sql = "select * from tuser where account = ? and password = ?";
        PreparedStatement statement = connection.prepareStatement(sql);

        //占位符赋值
        //给占位符赋值！ 从左到右，从1开始！
        /**
         *  int 占位符的下角标
         *  object 占位符的值
         */
        statement.setObject(1, account);
        statement.setObject(2, password);


        // 这哥们内部完成SQL语句拼接！
        // 5.执行SQL语句即可
        ResultSet resultSet = statement.executeQuery();

        // 5.处理查询结果
        // 如果 next 指针往下移动一行有数据，说明查询到了
        if (resultSet.next()) {
            int id = resultSet.getInt(1);
            String quaryAccount = resultSet.getString(2);
            String quaryPassword = resultSet.getString(3);
            String quaryRet = "id = " + id + ", quaryAccount = " + quaryAccount + ", quaryPassword = " + quaryPassword;
            System.out.println(quaryRet);

            System.out.println("login succesed");
        } else {
            System.out.println("login failed");
        }

        // 6.关闭资源
        resultSet.close();
        statement.close();
        connection.close();

    }
}
