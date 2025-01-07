package b_jdbc_expand;

import org.junit.Test;

import java.sql.*;
import java.util.Scanner;

/**
 * 1.主键回显
 * 2.批量插入数据
 */

public class OtherJdbcPart {

    /**
     * 返回插入的主键！
     * 在插入数据以后，主键会自增长，有时候需要得到主键去做其他的操作
     * 主键：数据库帮助维护的自增长的整数主键！
     * 向数据库 tuser 中添加一条数据 (account, password)，得到插入数据自增长的主键
     * @throws Exception
     */
    @Test
    public void returnPrimaryKey() throws Exception {
        System.out.println("get primaryKey");
        Scanner sc = new Scanner(System.in);
        System.out.println("please input the account that you want to insert");
        String account = sc.nextLine();
        System.out.println("please input the password that you want to insert");
        String password = sc.nextLine();
        sc.close();


        // 1.注册驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 2.获取连接
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/atchery",
                "root",
                "959452");
        // 3.构建转运小车
        // 4.构建 sql 语句，执行 sql 语句
        String sql = "insert into tuser(account,password) values(?,?)";
        /**
         * TODO: 第二个参数填入 1 | Statement.RETURN_GENERATED_KEYS
         *       告诉 statement 携带回数据库生成的主键！
         *  插入成功以后  id accoount password
         *  得到返回的结果后，根据指针的指向得到主键
         */
        PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

        statement.setString(1, account);
        statement.setString(2, password);
        // 5.解析返回结果
        int result = statement.executeUpdate();


        /**
         * TODO: 在这个地方去获取主键值
         * statement.getGeneratedKeys()
         */
        ResultSet resultSet = statement.getGeneratedKeys();
        // next 指针指向数据的上面一行，需要向下移动一行，然后取 主键
        resultSet.next();
        int primaryKey = resultSet.getInt(1);
        System.out.println("插入该数据，其对应的主键为：" + primaryKey);

        // 6.关闭资源
        statement.close();
        connection.close();

    }





    /**
     *
     * 批量细节：
     *    1.url?rewriteBatchedStatements=true
     *    2.insert 语句必须使用 values
     *    3.语句后面不能添加分号;
     *    4.语句不能直接执行，每次需要装货  addBatch() 最后 executeBatch();
     *
     * 批量插入优化！
     * @throws Exception
     */
    @Test
    public void  batchInsertYH() throws Exception{

        // 1.注册驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 2.获取连接
        Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/atchery?rewriteBatchedStatements=true",
                "root",
                "959452");
        // 3.构建转运工具
        // 4.构建 sql 语句，并且执行 sql 语句
        String sql = "insert into tuser(account,password) values(?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);

//        /**
//         * 普通的插入 10_000 条数据
//         * 一条一条数据插入，需要 java 持续多次和数据库进行交互
//         * 普通插入一万条数据用时：11748 ms
//         */
//        long startTime = System.currentTimeMillis();
//        for (int i = 1; i <= 10_000; i++) {
//            statement.setObject(1, "account" + i);
//            statement.setObject(2, "password" + i);
//            statement.executeUpdate();
//        }
//        long endTime = System.currentTimeMillis();
//        // 5.对结果进行解析
//        System.out.println("普通插入一万条数据用时：" + (endTime - startTime) + " ms");
//        // 6.关闭资源
//        statement.close();
//        connection.close();

        /**
         * 优化后直接插入 10_000 条数据
         * 将所有数据拼接在查询语句的后面，程序和数据库交互一次
         * 优化插入一万条数据用时：233 ms
         */
        long startTime = System.currentTimeMillis();
        for (int i = 1; i <= 10_000; i++) {
            statement.setString(1, "updateAccount" + i);
            statement.setString(2, "updatePassword" + i);
            // 进行拼接操作
            statement.addBatch();
        }
        // 对拼接后的数据进行批量插入
        statement.executeBatch();
        long endTime = System.currentTimeMillis();
        // 5.对结果进行解析
        System.out.println("优化插入一万条数据用时：" + (endTime - startTime) + " ms");
        // 6.关闭资源
        statement.close();
        connection.close();

    }


}
