package com.chinesechess.app;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋游戏逻辑核心
 */
public class ChessGame {

    private static final String TAG = "ChessGame";

    // 棋盘尺寸
    public static final int ROWS = 10;
    public static final int COLS = 9;

    // 河流行号
    public static final int RIVER_TOP = 4;
    public static final int RIVER_BOTTOM = 5;

    // 棋子类型常量
    public static final int EMPTY = 0;
    public static final int KING = 1;      // 帅/将
    public static final int ADVISOR = 2;   // 仕/士
    public static final int ELEPHANT = 3;  // 相/象
    public static final int HORSE = 4;     // 馬/马
    public static final int ROOK = 5;      // 車/车
    public static final int CANNON = 6;    // 炮
    public static final int PAWN = 7;      // 兵/卒

    // 颜色: 正数=红方(玩家), 负数=黑方(Bot)
    public static final int RED = 1;
    public static final int BLACK = -1;

    private int[][] board;
    private int currentPlayer;
    private Move lastMove;

    public ChessGame() {
        board = new int[ROWS][COLS];
        reset();
    }

    /** 重置到初始布局 */
    public void reset() {
        // 黑方 (上方, row 0-4)
        board[0] = new int[]{-ROOK, -HORSE, -ELEPHANT, -ADVISOR, -KING, -ADVISOR, -ELEPHANT, -HORSE, -ROOK};
        board[1] = new int[]{EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY};
        board[2] = new int[]{EMPTY, -CANNON, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, -CANNON, EMPTY};
        board[3] = new int[]{-PAWN, EMPTY, -PAWN, EMPTY, -PAWN, EMPTY, -PAWN, EMPTY, -PAWN};
        board[4] = new int[]{EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY};
        // 楚河汉界
        board[5] = new int[]{EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY};
        // 红方 (下方, row 5-9)
        board[6] = new int[]{PAWN, EMPTY, PAWN, EMPTY, PAWN, EMPTY, PAWN, EMPTY, PAWN};
        board[7] = new int[]{EMPTY, CANNON, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, CANNON, EMPTY};
        board[8] = new int[]{EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY};
        board[9] = new int[]{ROOK, HORSE, ELEPHANT, ADVISOR, KING, ADVISOR, ELEPHANT, HORSE, ROOK};

        currentPlayer = RED;
        lastMove = null;
    }

    // ============ 基本访问 ============

    /** 返回棋盘副本——防止外部篡改 */
    public int[][] getBoard() { return copyBoard(); }

    /** 获取棋子，带边界检查 */
    public int getPiece(int row, int col) {
        if (!inBoard(row, col)) {
            Log.w(TAG, "getPiece out of bounds: (" + row + "," + col + ")");
            return EMPTY;
        }
        return board[row][col];
    }

    public int getCurrentPlayer() { return currentPlayer; }
    public Move getLastMove() { return lastMove; }

    /** 判断某棋子颜色 */
    public static int colorOf(int piece) {
        if (piece > 0) return RED;
        if (piece < 0) return BLACK;
        return 0;
    }

    /** 棋子类型(绝对值) */
    public static int typeOf(int piece) {
        return Math.abs(piece);
    }

    // ============ 着法生成 ============

    /** 获取某位置所有合法走法 */
    public List<Move> getValidMoves(int row, int col) {
        List<Move> moves = new ArrayList<>();
        int piece = getPiece(row, col);
        if (piece == EMPTY) return moves;
        if (colorOf(piece) != currentPlayer) return moves;

        int type = typeOf(piece);
        int color = colorOf(piece);

        switch (type) {
            case KING:     addKingMoves(moves, row, col, color); break;
            case ADVISOR:  addAdvisorMoves(moves, row, col, color); break;
            case ELEPHANT: addElephantMoves(moves, row, col, color); break;
            case HORSE:    addHorseMoves(moves, row, col, color); break;
            case ROOK:     addRookMoves(moves, row, col, color); break;
            case CANNON:   addCannonMoves(moves, row, col, color); break;
            case PAWN:     addPawnMoves(moves, row, col, color); break;
        }

        // 过滤掉会导致自己被将的走法
        List<Move> legalMoves = new ArrayList<>();
        for (Move m : moves) {
            if (isMoveSafe(m)) {
                legalMoves.add(m);
            }
        }
        return legalMoves;
    }

    /** 获取当前玩家所有合法走法 */
    public List<Move> getAllValidMoves() {
        List<Move> all = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] != EMPTY && colorOf(board[r][c]) == currentPlayer) {
                    all.addAll(getValidMoves(r, c));
                }
            }
        }
        return all;
    }

    /** 执行走法 */
    public void makeMove(Move move) {
        if (!inBoard(move.fromRow, move.fromCol) || !inBoard(move.toRow, move.toCol)) {
            Log.e(TAG, "makeMove out of bounds: " + move);
            return;
        }
        move.captured = board[move.toRow][move.toCol];
        board[move.toRow][move.toCol] = board[move.fromRow][move.fromCol];
        board[move.fromRow][move.fromCol] = EMPTY;
        lastMove = move;
        currentPlayer = -currentPlayer;
    }

    /** 撤销走法 */
    public void undoMove(Move move) {
        board[move.fromRow][move.fromCol] = board[move.toRow][move.toCol];
        board[move.toRow][move.toCol] = move.captured;
        currentPlayer = -currentPlayer;
    }

    // ============ 胜负判断 ============

    /** 当前玩家是否被将军 */
    public boolean isInCheck() {
        return isKingThreatened(currentPlayer);
    }

    /** 当前玩家是否被将死(无合法走法) */
    public boolean isCheckmate() {
        return getAllValidMoves().isEmpty();
    }

    /** 判断游戏是否结束 */
    public boolean isGameOver() {
        return isCheckmate();
    }

    // ============ 内部辅助 ============

    private boolean isMoveSafe(Move move) {
        int captured = board[move.toRow][move.toCol];
        board[move.toRow][move.toCol] = board[move.fromRow][move.fromCol];
        board[move.fromRow][move.fromCol] = EMPTY;

        boolean safe = !isKingThreatened(colorOf(board[move.toRow][move.toCol]));

        board[move.fromRow][move.fromCol] = board[move.toRow][move.toCol];
        board[move.toRow][move.toCol] = captured;

        return safe;
    }

    /** 某颜色的将是否受威胁 */
    private boolean isKingThreatened(int color) {
        int kingRow = -1, kingCol = -1;
        int kingPiece = color == RED ? KING : -KING;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == kingPiece) {
                    kingRow = r;
                    kingCol = c;
                    break;
                }
            }
            if (kingRow >= 0) break;
        }
        if (kingRow < 0) return true;

        int oppColor = -color;

        // 车/将直线攻击(含飞将)
        if (checkStraightAttack(kingRow, kingCol, oppColor)) return true;

        // 马攻击
        if (checkHorseAttack(kingRow, kingCol, oppColor)) return true;

        // 炮攻击
        if (checkCannonAttack(kingRow, kingCol, oppColor)) return true;

        // 兵/卒攻击
        if (checkPawnAttack(kingRow, kingCol, oppColor, color)) return true;

        return false;
    }

    private boolean checkStraightAttack(int kingRow, int kingCol, int oppColor) {
        // 四个方向
        for (int d = 0; d < 4; d++) {
            int dr = (d == 0 ? -1 : d == 1 ? 1 : 0);
            int dc = (d == 2 ? -1 : d == 3 ? 1 : 0);
            int r = kingRow + dr, c = kingCol + dc;
            while (inBoard(r, c)) {
                int p = board[r][c];
                if (p != EMPTY) {
                    if (colorOf(p) == oppColor && (typeOf(p) == ROOK || typeOf(p) == KING)) return true;
                    break;
                }
                r += dr;
                c += dc;
            }
        }
        return false;
    }

    private boolean checkHorseAttack(int kingRow, int kingCol, int oppColor) {
        int[][] horseOffsets = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        int[][] horseLegs = {{-1,0},{-1,0},{0,-1},{0,1},{0,-1},{0,1},{1,0},{1,0}};
        for (int i = 0; i < 8; i++) {
            int r = kingRow + horseOffsets[i][0];
            int c = kingCol + horseOffsets[i][1];
            int lr = kingRow + horseLegs[i][0];
            int lc = kingCol + horseLegs[i][1];
            if (inBoard(r, c) && inBoard(lr, lc) && board[lr][lc] == EMPTY) {
                int p = board[r][c];
                if (p != EMPTY && colorOf(p) == oppColor && typeOf(p) == HORSE) return true;
            }
        }
        return false;
    }

    private boolean checkCannonAttack(int kingRow, int kingCol, int oppColor) {
        for (int d = 0; d < 4; d++) {
            int dr = (d == 0 ? -1 : d == 1 ? 1 : 0);
            int dc = (d == 2 ? -1 : d == 3 ? 1 : 0);
            boolean foundScreen = false;
            int r = kingRow + dr, c = kingCol + dc;
            while (inBoard(r, c)) {
                int p = board[r][c];
                if (!foundScreen) {
                    if (p != EMPTY) foundScreen = true;
                } else {
                    if (p != EMPTY) {
                        if (colorOf(p) == oppColor && typeOf(p) == CANNON) return true;
                        break;
                    }
                }
                r += dr;
                c += dc;
            }
        }
        return false;
    }

    private boolean checkPawnAttack(int kingRow, int kingCol, int oppColor, int myColor) {
        int oppForward = (oppColor == RED ? -1 : 1); // 对方兵的前进方向
        // 对方兵必须在 kingRow - oppForward 处才能一步走到 kingRow
        int pawnRow = kingRow - oppForward;
        if (inBoard(pawnRow, kingCol)) {
            int p = board[pawnRow][kingCol];
            if (p != EMPTY && colorOf(p) == oppColor && typeOf(p) == PAWN) return true;
        }
        // 左右(对方兵过河后才能横走威胁将)
        for (int nc : new int[]{kingCol - 1, kingCol + 1}) {
            if (inBoard(kingRow, nc)) {
                int p = board[kingRow][nc];
                if (p != EMPTY && colorOf(p) == oppColor && typeOf(p) == PAWN) {
                    if (hasCrossedRiver(kingRow, oppColor)) return true;
                }
            }
        }
        return false;
    }

    /** 判断某位置上的兵是否已过河 */
    private boolean hasCrossedRiver(int row, int color) {
        if (color == RED) return row <= RIVER_TOP;
        else return row >= RIVER_BOTTOM;
    }

    // ============ 各棋子走法生成 ============

    private void addKingMoves(List<Move> moves, int row, int col, int color) {
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr = row + d[0], nc = col + d[1];
            if (inPalace(nr, nc, color)) {
                int target = board[nr][nc];
                if (target == EMPTY || colorOf(target) != color) {
                    moves.add(new Move(row, col, nr, nc, target));
                }
            }
        }
    }

    private void addAdvisorMoves(List<Move> moves, int row, int col, int color) {
        int[][] dirs = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] d : dirs) {
            int nr = row + d[0], nc = col + d[1];
            if (inPalace(nr, nc, color)) {
                int target = board[nr][nc];
                if (target == EMPTY || colorOf(target) != color) {
                    moves.add(new Move(row, col, nr, nc, target));
                }
            }
        }
    }

    private void addElephantMoves(List<Move> moves, int row, int col, int color) {
        int[][] dirs = {{-2,-2},{-2,2},{2,-2},{2,2}};
        int[][] eyes = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int i = 0; i < 4; i++) {
            int nr = row + dirs[i][0], nc = col + dirs[i][1];
            int er = row + eyes[i][0], ec = col + eyes[i][1];
            if (inBoard(nr, nc) && inOwnHalf(nr, color)) {
                if (board[er][ec] == EMPTY) {
                    int target = board[nr][nc];
                    if (target == EMPTY || colorOf(target) != color) {
                        moves.add(new Move(row, col, nr, nc, target));
                    }
                }
            }
        }
    }

    private void addHorseMoves(List<Move> moves, int row, int col, int color) {
        int[][] offsets = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        int[][] legs = {{-1,0},{-1,0},{0,-1},{0,1},{0,-1},{0,1},{1,0},{1,0}};
        for (int i = 0; i < 8; i++) {
            int nr = row + offsets[i][0], nc = col + offsets[i][1];
            int lr = row + legs[i][0], lc = col + legs[i][1];
            if (inBoard(nr, nc) && board[lr][lc] == EMPTY) {
                int target = board[nr][nc];
                if (target == EMPTY || colorOf(target) != color) {
                    moves.add(new Move(row, col, nr, nc, target));
                }
            }
        }
    }

    private void addRookMoves(List<Move> moves, int row, int col, int color) {
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr = row + d[0], nc = col + d[1];
            while (inBoard(nr, nc)) {
                int target = board[nr][nc];
                if (target == EMPTY) {
                    moves.add(new Move(row, col, nr, nc, EMPTY));
                } else {
                    if (colorOf(target) != color) {
                        moves.add(new Move(row, col, nr, nc, target));
                    }
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
    }

    private void addCannonMoves(List<Move> moves, int row, int col, int color) {
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : dirs) {
            int nr = row + d[0], nc = col + d[1];
            while (inBoard(nr, nc)) {
                if (board[nr][nc] == EMPTY) {
                    moves.add(new Move(row, col, nr, nc, EMPTY));
                } else {
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
            nr += d[0]; nc += d[1];
            while (inBoard(nr, nc)) {
                if (board[nr][nc] != EMPTY) {
                    if (colorOf(board[nr][nc]) != color) {
                        moves.add(new Move(row, col, nr, nc, board[nr][nc]));
                    }
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
    }

    private void addPawnMoves(List<Move> moves, int row, int col, int color) {
        int forward = (color == RED ? -1 : 1);
        boolean crossed = hasCrossedRiver(row, color);

        int nr = row + forward;
        if (inBoard(nr, col)) {
            int target = board[nr][col];
            if (target == EMPTY || colorOf(target) != color) {
                moves.add(new Move(row, col, nr, col, target));
            }
        }
        if (crossed) {
            for (int nc : new int[]{col - 1, col + 1}) {
                if (inBoard(row, nc)) {
                    int target = board[row][nc];
                    if (target == EMPTY || colorOf(target) != color) {
                        moves.add(new Move(row, col, row, nc, target));
                    }
                }
            }
        }
    }

    // ============ 位置判断 ============

    private boolean inBoard(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }

    private boolean inPalace(int r, int c, int color) {
        if (c < 3 || c > 5) return false;
        if (color == RED) return r >= 7 && r <= 9;
        else return r >= 0 && r <= 2;
    }

    private boolean inOwnHalf(int r, int color) {
        if (color == RED) return r >= RIVER_BOTTOM;
        else return r <= RIVER_TOP;
    }

    /** 深拷贝当前棋盘 */
    public int[][] copyBoard() {
        int[][] copy = new int[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, COLS);
        }
        return copy;
    }

    /** 加载棋盘(用于AI搜索) */
    public void loadBoard(int[][] savedBoard, int savedPlayer) {
        for (int i = 0; i < ROWS; i++) {
            System.arraycopy(savedBoard[i], 0, board[i], 0, COLS);
        }
        currentPlayer = savedPlayer;
    }

    // ============ Move 类 ============

    public static class Move {
        public int fromRow, fromCol, toRow, toCol;
        public int captured;

        public Move(int fromRow, int fromCol, int toRow, int toCol) {
            this(fromRow, fromCol, toRow, toCol, 0);
        }

        public Move(int fromRow, int fromCol, int toRow, int toCol, int captured) {
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
            this.captured = captured;
        }

        @Override
        public String toString() {
            return String.format("(%d,%d)->(%d,%d)", fromRow, fromCol, toRow, toCol);
        }
    }
}
