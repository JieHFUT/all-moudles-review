package a_jdbc_util;

import com.alibaba.druid.pool.DruidDataSourceFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ClassName: JdbcUtils
 * Package: utils
 * Description:
 *
 * todo: 1.0的工具类  => Druid 链接池对象
 * 用来创建一个 Druid 链接池对象，将其封装起来，向外提供两个接口： 获得和回收的接口
 * 工具类的方法推荐写成静态的
 *
 * 实现
 *      属性：连接池对象，并且其只会实例化一次
 *           单例模式：静态代码快
 *      方法：对外提供连接的方法，回收外部传入的方法
 * @Author jieHFUT
 * @Create 2024/11/20 22:16
 * @Version 1.0
 */
public class JdbcUtilA {

    // 连接池对象
    private static DataSource dataSource = null;

    // 创建单例模式
    static {
        // 初始化连接池
        Properties properties = new Properties();
        InputStream resourceAsStream = JdbcUtilA.class.getClassLoader().getResourceAsStream("druid.properties");
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            dataSource = DruidDataSourceFactory.createDataSource(properties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    /**
     * 对外提供一个获取连接池的方法
     * 注意下面方法中调用 dataSource.getConnection()并不是调用自己
     * 两个 getConnection() 是不同的方法
     * @return
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 对外提供一个归还连接的方法
     * @param connection
     */
    public static void freeConnection(Connection connection) throws SQLException {
        //连接池的连接，调用 close() 就是回收
        connection.close();
    }


}