package c_jdbc_bank.affairsproblem;

import java.sql.SQLException;

public class BankService {
    /**
     * 这是一个模仿银行转账的业务
     * @param addAccount 需要加钱的账户
     * @param subAccount 需要减钱的账户
     * @param money 转账的数值
     */
    public void transfer(String addAccount, String subAccount, int money) throws SQLException, ClassNotFoundException {
        // 显示转账双方的数据
        System.out.println("addAccount = " + addAccount + "; subAccount = " + subAccount + "; money = " + money);
        BankDao bankDao = new BankDao();
        // 对向外转钱的一方进行扣钱
        bankDao.subMoney(subAccount, money);

        // 对到账的一方进行加钱
        bankDao.addMoney(addAccount, money);



        /**
         * 出现的问题：
         * 由于账户设置的不能为负数
         * 所以当减钱的一方钱归零的时候，就无法再进行减钱
         * 但是由于加钱的一方可以继续
         * 所以就会出现转账时候，一方不扣钱，一方还加钱的现象
         */
    }

}
