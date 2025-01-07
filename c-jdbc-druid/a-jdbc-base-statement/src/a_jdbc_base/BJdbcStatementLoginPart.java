package a_jdbc_base;

import java.sql.*;
import java.util.Scanner;

/**
 * 模拟用户登陆，进行数据库交互
 */
public class BJdbcStatementLoginPart {

    public static void main(String[] args) throws ClassNotFoundException, SQLException {
        // 用户输入账号与密码
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入账号：");
        String account = sc.nextLine();
        System.out.println("请输入密码：");
        String password = sc.nextLine();
        sc.close();

        // 1.注册驱动
        /**
         * 类加载： java文件 -> 编译 -> 【 class字节码文件 -->  类加载 --> jvm虚拟中  --> Class对象】
         * 类加载具体步骤：  加载 【class文件转成对象加载到虚拟机中】->
         *                连接 【验证（检查类文件） -> 准备 (静态变量赋默认值) -> 解析 (调用静态代码块) 】 ->
         *                初始化 -> (赋真实值)
         * 以下7种方式会触发类加载：
         *    1. new关键字
         *    2. 调用静态属性
         *    3. 调用静态方法
         *    4. 接口 包含1.8 新特性 default关键字
         *    5. 反射 【Class.forName() 类名.class】
         *    6. 子类调用会触发父类的静态代码块
         *    7. 触发类的入口方法main
         */
        // DriverManager.registerDriver(new Driver());
        // 其他的更好的注册驱动的方法
        Class.forName("com.mysql.cj.jdbc.Driver");



        // 2.获取连接
        /**
         * 重写： 为了子类扩展父类的方法！父类也间接的规范了子类方法的参数和返回！
         * 重载： 重载一般应用在第三方的工具类上，为了方便用户多种方式传递参数形式！简化形式！
         */
        /**
         * 三个参数：
         *    String URL: 连接数据库地址
         *    String user: 连接数据库用户名
         *    String password: 连接数据库用户对应的密码
         * 数据库URL语法：
         *    JDBC:
         *        ip port
         *        jdbc:mysql | jdbc:oracle :// 127.0.0.1 | localhost : 3306 / 数据库名
         *        jdbc:mysql://localhost:3306/day01
         *        192.168.33.45
         *        jdbc:mysql://192.168.33.45/3306/day01
         *        当前电脑的省略写法！ 注意：本机和端口3306一起省略
         *        jdbc:mysql://localhost:3306/day01 在本机时可以缩写为 jdbc:mysql:///day01
         *
         * 两个参数：
         *     String URL : 写法还是jdbc的路径写法！
         *     Properties : 就是一个参数封装容器！至少要包含 user / password 两个key!  存储连接账号信息！
         *
         *     Properties info = new Properties();
         *     info.put("user", account);
         *     info.put("password", password);
         *     DriverManager.getConnection("jdbc:mysql:///atguigu", info);
         *
         * 一个参数：
         *    String URL: URl可以携带目标地址，可以通过?分割，在后面key=value&key=value形式传递参数
         *                jdbc:mysql:///day01?user=root&password=123456
         * 扩展路径参数(了解):
         *    serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&useSSL=true
         *             识别时区
         *
         */
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/atchery",
                "root",
                "959452");

        // 3.创建预编译工具
        Statement statement = connection.createStatement();
        // 4.准备 sql 语句
        String sql = "select * from tuser where account = '" + account + "' and password = '" + password + "'";
        System.out.println("sql = " + sql); // sql = select * from tuser where account = 'zhangsan' and password = '123456'
        // TODO: 容易产生注入攻击的问题
        // 输入密码为: ' or '1' = '1
        // select * from tuser where account = 'zhagsan' and password = '' or '1' = '1' ;

        /**
         *  DQL语句 => 查询语句
         *  ResultSet 结果集对象 = executeQuery (DQL语句 => 查询语句)
         *  int       响应行数  = executeUpdate(非DQL语句 => 增删改语句)
         */
        // 5.执行 sql 语句
        ResultSet resultSet = statement.executeQuery(sql);


        /**
         *
         * TODO:1.需要理解 ResultSet 的数据结构和小海豚查询出来的是一样，需要在脑子里构建结果表！
         * TODO:2.有一个光标指向的操作数据行，默认指向第一行的上边！我们需要移动光标，指向行，在获取行即可！
         *        boolean = next()
         *              false: 没有数据，也不移动了！
         *              true:  有更多行，并且移动到下一行！
         *       推荐：推荐使用 if 或者 while 循环，嵌套next方法，循环和判断体内获取数据！
         *       if(next()){获取列的数据！} ||  while(next()){获取列的数据！}
         *
         *TODO：3.获取当前行列的数据！
         *         get类型(int columnIndex | String columnLabel)
         *        列名获取  //lable 如果没有别名，等于列名， 有别名 label 就是别名，他就是查询结果的标识！
         *        列的角标  //从左到右 从1开始！ 数据库全是从1开始！
         */
        // 5.解析返回结果
        if (resultSet.next()) {
            // 只要向下移动就说明有符合的数据
            int quaryId = resultSet.getInt("id");
            String quaryaccount = resultSet.getString("account");
            String quaryPassword = resultSet.getString("password");
            String quaryRet = "id = " + quaryId + ", account = " + quaryaccount + ", password = " + quaryPassword;
            System.out.println(quaryRet);


            // 遍历的第二种方法
            int quaryId2 = resultSet.getInt(1);
            String quaryaccount2 = resultSet.getString(2);
            String quaryPassword2 = resultSet.getString(3);
            String quaryRet2 = "id = " + quaryId2 + ", account = " + quaryaccount2 + ", password = " + quaryPassword2;
            System.out.println(quaryRet2);
        }

        // 6.关闭资源
        resultSet.close();
        statement.close();
        connection.close();

    }
}
