package druid;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ClassName: DruidUsePart
 * Package: druid 德鲁伊
 * Description: 直接使用代码来设置连接池的参数信息
 * 1.创建一个 druid 连接池对象
 * 2.设置连接池参数
 * 3.获取连接
 * 4.回收连接
 * @Author jieHFUT
 * @Create 2024/11/20 21:54
 * @Version 1.0
 */
public class DruidUsePart {

    public void testDruidHard() throws SQLException {
        //1.创建一个 druid 连接池对象
        DruidDataSource druidDataSource = new DruidDataSource();
        //2.设置连接池参数
        // 必须的参数，连接数据库驱动类的全限定符号：url  user  password
        // 非必须的参数：连接池的数量，连接池的最大数量...
        druidDataSource.setUrl("jdbc:mysql://127.0.0.1:3306/atchery");
        druidDataSource.setUsername("root");
        druidDataSource.setPassword("959452");
        druidDataSource.setDriverClassName("com.mysql.jdbc.Driver");// 帮助我们进行驱动注册和获取连接

        druidDataSource.setInitialSize(5);
        druidDataSource.setMaxActive(10);
        //3.获取连接
        Connection connection = druidDataSource.getConnection();

        // 进行 CURD

        //4.回收连接
        connection.close();
    }







    public void testDruidSoft() throws Exception {
        //1.读取外部的配置文件的内容 properties
        Properties properties = new Properties();
        //以流的形式来获取配置文件的内容
        InputStream resourceAsStream = DruidUsePart.class.getClassLoader().getResourceAsStream("druid.properties");
        properties.load(resourceAsStream);

        //2.使用连接池的工具类的工程模式来创建连接池
        DataSource dataSource = DruidDataSourceFactory.createDataSource(properties);
        //3.获取连接
        Connection connection = dataSource.getConnection();

        // 在这个地方进行对数据库的 CURD 操作

        //4.回收连接
        connection.close();
    }





















}
