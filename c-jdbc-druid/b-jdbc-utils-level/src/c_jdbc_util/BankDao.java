package c_jdbc_util;

import java.sql.SQLException;

/**
 * ClassName: BankDao
 * Package: jdbc_expand
 * Description:
 *
 * @Author jieHFUT
 * @Create 2024/11/20 20:43
 * @Version 1.0
 */

/**
 * 连接的建立、管理、关闭由业务层进行统一管理
 */
public class BankDao extends BaseDao {


    /**
     * 加钱的方法
     * @param account 需要加钱的账户
     * @param money 加钱的数额
     */
    public void addMoney(String account, int money) throws ClassNotFoundException, SQLException {

        int ret = execute("update t_bank set money = money + ? where account = ?", money, account);

    }


    /**
     * 减钱的方法
     * @param account 需要减钱的账户
     * @param money 减钱的数额
     */
    public void subMoney(String account, int money) throws ClassNotFoundException, SQLException {

        String sql = "update t_bank set money = money - ? where account = ?;";

    }
}
