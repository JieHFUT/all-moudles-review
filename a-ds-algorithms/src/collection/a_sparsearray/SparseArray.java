package collection.a_sparsearray;


import java.util.Arrays;

/**
 * 复习稀疏数组
 * （L, W,  N）
 * （值、行、列）
 *
 *
 */
public class SparseArray {

    public static void main(String[] args) {
        /**
         * 设置一个棋盘，棋盘为二维数组
         * 0 代表该位置没有数组
         * 1 代表该位置为黑子
         * 2 代表该位置是白子
         */

        // 1.设置原始的二维数组（棋盘）
        int[][] chessBoard = new int[11][11];
        chessBoard[1][4] = 1;
        chessBoard[2][9] = 2;
        chessBoard[8][6] = 1;
        chessBoard[4][7] = 2;
        chessBoard[4][3] = 3;
        // 2.输出原始棋盘
        for (int i = 0; i < chessBoard.length; i++) {
            System.out.println(Arrays.toString(chessBoard[i]));
        }
        // 3.将二维数组转化为稀疏数组
        /**
         * 转换思想是
         * 每一个数字的（值、行、列）
         * [2, 4, 7]
         * [1, 2, 7]
         * [2, 8, 6]
         * [1, 3, 9]
         * [2, 5, 7]
         */
        int number = 0;
        for (int i = 0; i < chessBoard.length; i++) {
            for (int j = 0; j < chessBoard[i].length; j++) {
                // 对每一个非零的值都记录
                if (chessBoard[i][j] != 0) {
                    number++;
                }
            }
        }
        // 创建稀疏数组（n + 1 行 3 列）
        int row = 1;
        int[][] sparsearray = new int[number + 1][3];
        // 稀疏数组首行赋值
        sparsearray[0][0] = chessBoard.length;
        sparsearray[0][1] = chessBoard[0].length;
        sparsearray[0][2] = number;

        // 向稀疏数组中填入数值（值、行、列）
        for (int i = 0; i < chessBoard.length; i++) {
            for (int j = 0; j < chessBoard[i].length; j++) {
                if (chessBoard[i][j] != 0) {
                    sparsearray[row][0] = chessBoard[i][j];
                    sparsearray[row][1] = i;
                    sparsearray[row][2] = j;
                    row++;
                }
            }
        }
        // 4.输出稀疏数组
        for (int i = 0; i < sparsearray.length; i++) {
            System.out.println(Arrays.toString(sparsearray[i]));
        }


        // 5.稀疏数组恢复为原始的二维数组
        // 读取稀疏数组第一列的参数，创建原始数组
        int rows = sparsearray[0][0];
        int cols = sparsearray[0][1];
        int[][] original = new int[rows][cols];
        for (int i = 1; i < sparsearray.length; i++) {
            for (int j = 0; j < sparsearray[i].length; j++) {
                // 值 行 列
                original[sparsearray[i][1]][sparsearray[i][2]] = sparsearray[i][0];
            }
        }
        // 输出原始数组
        for (int[] everyRow : original) {
            System.out.println(Arrays.toString(everyRow));
        }

    }
}
