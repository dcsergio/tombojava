package it.sdc.tombojava.tombola;

import java.util.Arrays;

public final class TombolaCard {

    private final int[][] grid;

    public TombolaCard(int[][] grid) {
        if (grid.length != 3) {
            throw new IllegalArgumentException("A tombola card must have 3 rows");
        }
        this.grid = new int[3][9];
        for (int row = 0; row < 3; row++) {
            if (grid[row].length != 9) {
                throw new IllegalArgumentException("A tombola card row must have 9 columns");
            }
            System.arraycopy(grid[row], 0, this.grid[row], 0, 9);
        }
    }

    public int get(int row, int col) {
        return grid[row][col];
    }

    public int[][] toMatrixCopy() {
        int[][] copy = new int[3][9];
        for (int row = 0; row < 3; row++) {
            System.arraycopy(grid[row], 0, copy[row], 0, 9);
        }
        return copy;
    }

    @Override
    public String toString() {
        return Arrays.deepToString(grid);
    }
}

