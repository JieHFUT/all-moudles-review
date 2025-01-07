package c_jdbc_util;

import b_jdbc_util.JdbcUtilB;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ClassName: BaseDao
 * Package: utils2
 * Description:
 * TODO: 封装两个方法，一个是简化 DQL，即查询语句
 *                一个是简化 非DQL,即非查询语句
 *
 * @Author jieHFUT
 * @Create 2024/11/20 23:01
 * @Version 1.0
 */
public abstract class BaseDao {

    /**
     * 封装简化 非 DQL 语句，即非查询语句
     * @param sql 传入的 sql 语句
     * @param params 可变参数，即传递占位符的数据
     * @return
     */
    public int execute(String sql, Object... params) throws SQLException {
        //1.获取一个连接
        Connection connection = JdbcUtilB.getConnection();
        //2.构建传送工具
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        //3.占位符赋值
        for (int i = 1; i <= params.length; i++) {
            preparedStatement.setObject(i , params[i]);
        }
        //4.执行 sql 语句
        int ret = preparedStatement.executeUpdate();
        //5.是否需要回收连接需要考虑是不是事务
        if (connection.getAutoCommit()) {
            //没有开启事务，正常回收连接 (也就是没有两个动作绑在一个事务上)
            JdbcUtilB.freeConnection();
        }
        // 开启事务，就由业务层自己去处理
        return ret;
    }


    /**
     * 查询的返回结果应该是一个实体类的对象的集合
     * 表中 => 一行 => java类的一个对象 => 多行 => List<java实体类> list
     *
         * DQL
         * <T> : 声明一个泛型，不确定类型
         * 1.确定泛型 User.class T = User
         * 2.使用反射技术属性赋值
         * public <T> List<T> executeQuery(Class<T> clazz, String sql, Object... params);
     *
     */

    /**
     * 将查询结果封装到一个实体类中
     * @param clazz
     * @param sql
     * @param params
     * @return
     * @param <T>
     * @throws SQLException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     */
    public <T> List<T> executeQuery(Class<T> clazz, String sql, Object... params) throws SQLException, InstantiationException, IllegalAccessException, NoSuchFieldException {
        //1.获取连接
        Connection connection = JdbcUtilB.getConnection();
        //2.创建 statement
        PreparedStatement preparedStatement = connection.prepareStatement(sql);

        //3.占位符进行赋值
        if (params != null && params.length != 0) {
            for (int i = 1; i <= params.length; i++) {
                preparedStatement.setObject(i , params[i - 1]);
            }
        }

        //4.执行
        ResultSet resultSet = preparedStatement.executeQuery();
        //5.
        List<T> list = new ArrayList<>();
        // 获取列信息对象
        // TODO: metaData => 装的是当前结果集 列的信息对象（可以获取列的名称根据下角标，获取列的数量）
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (resultSet.next()) {
            // 调用类的无参构造函数实例化对象
            T t = clazz.newInstance();

            // 自动遍历列
            for (int i = 1; i <= columnCount; i++) {
                // 对象的属性值
                Object value = resultSet.getObject(i);
                // 获取指定列的名称（有别名就是别名）
                String columnName = metaData.getColumnLabel(i);

                // 反射给对象的属性值赋值
                Field field = clazz.getDeclaredField(columnName);
                field.setAccessible(true);// 属性可以设置，打破属性私有的限制
                /**
                 * 参数1: 要赋值的对象
                 * 参数2: 属性的具体值
                 */
                field.set(t, value);
            }
            // 一行数据存储再 list 中
            list.add(t);
        }
        // 6.关闭资源
        resultSet.close();
        preparedStatement.close();

        if (connection.getAutoCommit()) {
            // 没有事务，可以直接关闭
            JdbcUtilB.freeConnection();
        }
        return list;
    }













}
