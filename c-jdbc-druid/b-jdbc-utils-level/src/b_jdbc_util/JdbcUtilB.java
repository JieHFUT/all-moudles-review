package b_jdbc_util;

import com.alibaba.druid.pool.DruidDataSourceFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Description:
 *
 * 利用本地线程变量，存储连接信息！确保一个线程的多个方法使用的是同一个 Connection
 * 在做事务操作的时候 service 和 dao 层属于同一个线程，就不用再传递参数了
 *
 * @Author jieHFUT
 * @Create 2024/11/20 22:34
 * @Version 1.0
 */
public class JdbcUtilB {

    // 连接池对象
    private static DataSource dataSource = null;

    // 本地线程变量的连接
    private static ThreadLocal<Connection> threadLocal = new ThreadLocal<Connection>();

    // 创建单例模式
    static {
        // 初始化连接池
        Properties properties = new Properties();
        InputStream resourceAsStream = JdbcUtilB.class.getClassLoader().getResourceAsStream("druid.properties");
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
        // 先查看线程本地变量中是否存在连接
        Connection connection = threadLocal.get();
        if (connection == null) {
            // 线程本地变量没有 就通过连接池获取
            connection = dataSource.getConnection();
            threadLocal.set(connection);
        }
        return connection;
    }

    /**
     * 对外提供一个归还连接的方法
     * @param
     */
    public static void freeConnection() throws SQLException {
        Connection connection = threadLocal.get();
        if (connection != null) {
            // 清空线程本地变量数据
            threadLocal.remove();
            // 事务状态回归
            connection.setAutoCommit(true);
            connection.close();
        }
    }


}
