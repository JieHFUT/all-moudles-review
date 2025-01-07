package c_jdbc_bank.affairsproblemsolved;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 连接的建立、管理、关闭由业务层进行统一管理
 */
public class BankDao {


    /**
     * 加钱的方法
     * @param account 需要加钱的账户
     * @param money 加钱的数额
     */
    public void addMoney(String account, int money, Connection connection) throws ClassNotFoundException, SQLException {


        //3.构建转运工具
        //4.构建 sql 语句，执行 sql 语句
        String sql = "update t_bank set money = money + ? where account = ?;";
        PreparedStatement statement = connection.prepareStatement(sql);

        statement.setInt(1, money);
        statement.setString(2, account);
        int ret = statement.executeUpdate();
        //5.对结果进行解析
        if (ret != 0) {
            System.out.println("账户 "+ account +" 到账" + money + "元");
        } else {
            System.out.println("收钱方转账失败");
        }
        //6.关闭资源
        statement.close();
        connection.close();
    }


    /**
     * 减钱的方法
     * @param account 需要减钱的账户
     * @param money 减钱的数额
     */
    public void subMoney(String account, int money, Connection connection) throws ClassNotFoundException, SQLException {


        //3.构建转运工具
        //4.构建 sql 语句，执行 sql 语句
        String sql = "update t_bank set money = money - ? where account = ?;";
        PreparedStatement statement = connection.prepareStatement(sql);

        statement.setInt(1, money);
        statement.setString(2, account);
        int ret = statement.executeUpdate();
        //5.对结果进行解析
        if (ret != 0) {
            System.out.println("账户 "+ account +" 转出" + money + "元");
        } else {
            System.out.println("发钱方转账失败");
        }
        //6.关闭资源
        statement.close();
        connection.close();
    }
}
