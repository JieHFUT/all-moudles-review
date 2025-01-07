package a_jdbc_base;

import org.junit.Test;

import java.sql.*;
import java.util.*;

/**
 * 利用 PreparedStatement 进行
 * CRUD 的练习
 */
public class DJdbcPreparedStatementCrudPart {


    @Test
    public void testInsert() throws ClassNotFoundException, SQLException {
        System.out.println("input one data");
        Scanner scanner = new Scanner(System.in);
        System.out.println("please input the account that you want to add:");
        String account = scanner.nextLine();
        System.out.println("please input the password that you want to add:");
        String password = scanner.nextLine();
        scanner.close();


        // 1. 注册驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 2.获取连接
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/atchery",
                "root",
                "959452");
        // 3.建立转运工具
        // 4.构建并且发送 sql 语句

        //TODO: 切记, ? 只能代替 值 !!!!!  不能代替关键字 特殊符号 容器名
        String sql = "insert into tuser(account, password) values (?, ?)";

        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, account);
        statement.setString(2, password);

        int result = statement.executeUpdate();
        // 5.解析插入结果
        if (result != 0) {
            System.out.println("insert successful");
        } else {
            System.out.println("insert failed");
        }

        // 6.关闭资源
        statement.close();
        connection.close();
    }



    /**
     * 测试删除一条数据
     * 删除数据 account: xiaomin
     *        password:xiaomin123
     */
    @Test
    public void testDelete() throws ClassNotFoundException, SQLException {
        Scanner sc = new Scanner(System.in);
        System.out.println("please input the account that you want to delete");
        String account = sc.nextLine();
        sc.close();


        // 1.注册驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 2.获取连接
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/atchery",
                "root",
                "959452");
        // 3.构建转运工具
        // 4.构建 sql 语句并且执行
        String sql = "delete from tuser where account = ?";
        PreparedStatement statement = connection.prepareStatement(sql);

        statement.setObject(1, account);

        // 5.解析删除结果
        int result = statement.executeUpdate();
        if (result != 0) {
            System.out.println("delete successful");
        } else {
            System.out.println("delete failed");
        }
    }


    /**
     * 修改一条用户数据!
     * 修改账号: dahua 的用户,将 password 改为 dahua321
     */
    @Test
    public void testUpdate() throws ClassNotFoundException, SQLException {
        System.out.println("update data");
        Scanner scanner = new Scanner(System.in);
        System.out.println("please input the account that you want to update");
        String account = scanner.nextLine();
        System.out.println("please input the password that you want to update");
        String password = scanner.nextLine();
        scanner.close();

        // 1.注册驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 2.获取连接
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/atchery",
                "root",
                "959452");

        // 3.构建转运工具，构建并且执行 sql 语句
        String sql = "update tuser set password = ? where account = ?";
        PreparedStatement statement = connection.prepareStatement(sql);

        statement.setObject(1, password);
        statement.setObject(2, account);
        int result = statement.executeUpdate();

        System.out.println("执行结果为：" + result);
        // 5.对结果进行解析
        if (result != 0) {
            System.out.println("update successful");
        } else {
            System.out.println("update failed");
        }

        // 6.释放资源
        statement.close();
        connection.close();
    }


    /**
     * 查询全部数据!
     *   将数据存到 List<Map> 中
     *   map -> 对应一行数据
     *      map key -> 数据库列名或者别名
     *      map value -> 数据库列的值
     *
     *      第一行  id account password
     *      第二行  id account password
     *      第三行  id account password
     *      第四行  id account password
     *
     * TODO: 思路分析
     *    1.先创建一个List<Map>集合
     *    2.遍历resultSet对象的行数据
     *    3.将每一行数据存储到一个map对象中!
     *    4.将对象存到List<Map>中
     *    5.最终返回
     *
     * TODO:
     *    初体验,结果存储!
     *    学习获取结果表头信息(列名和数量等信息)
     */

    @Test
    public void testQuaryAll() throws ClassNotFoundException, SQLException {

        // 1.注册驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 2.获取连接
        Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/atchery",
                "root",
                "959452");
        // 3.构建转运工具
        // 4.构建 sql 语句，执行 sql 语句
        String sql = "select * from tuser";
        PreparedStatement statement = connection.prepareStatement(sql);

        ResultSet resultSet = statement.executeQuery();

        // 5.对查询结果进行分析
        /**
         * // TODO: map {
         *              key:"id", value:id
         *              key:"account", value:account
         *              key:"password", value:password
         *          }
         */
//        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
//        while (resultSet.next()) {
//            // 此时是查询的一行数据 id account password
//            Map<String, Object> map = new HashMap<String, Object>();
//            map.put("id", resultSet.getInt("id"));
//            map.put("account", resultSet.getString("account"));
//            map.put("password", resultSet.getString("password"));
//            list.add(map);
//        }
//        // 打印结果：
//        System.out.println(list);
//        //  结果：[{password=zhangliang123, id=1, account=zhangliang}, {password=dahua325142351, id=3, account=dahua}]



        List<Map<String, Object>> list = new ArrayList<>();
        // 获取列信息对象
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount(); // 获取有多少列
        while (resultSet.next()) {
            for (int i = 0; i < columnCount; i++) {
                Map<String, Object> map = new HashMap<>();
                map.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                //      获取列的别名(没有别名就用列的名称)    这一行 => 本列的数据
                list.add(map);
            }
        }
        // 打印结果
        System.out.println(list);
    }



}
