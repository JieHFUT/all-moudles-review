package c_jdbc_util;

/**
 * ClassName: BankTest
 * Package: jdbc_expand
 * Description:
 *
 * @Author jieHFUT
 * @Create 2024/11/20 20:43
 * @Version 1.0
 */
public class BankTest {
    public static void main(String[] args) {

        BankService bankService = new BankService();
        try {
            bankService.transfer("ergouzi", "lvdandna", 500);
        } catch (Exception e) {
            System.out.println(e.getMessage() + "转账业务出现异常，冻结账号！");
        }
    }
}
