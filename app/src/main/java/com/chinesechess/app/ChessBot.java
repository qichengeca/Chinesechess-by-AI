package com.chinesechess.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 中国象棋AI - 使用Minimax + Alpha-Beta剪枝
 */
public class ChessBot {

    private final ChessGame game;
    private final int color; // Bot的颜色 (BLACK)
    private final Random random;
    private int searchDepth;
    private float noiseFactor = 0f; // 0=最优, 越大越随机

    // 棋子基础价值
    private static final int[] PIECE_VALUE = {
        0,      // EMPTY
        10000,  // KING
        200,    // ADVISOR
        200,    // ELEPHANT
        400,    // HORSE
        900,    // ROOK
        450,    // CANNON
        100     // PAWN (基础, 过河加成)
    };

    // 位置价值表 (10x9, 从黑方视角)
    // 兵位置价值(黑方视角, 行0=己方底线)
    private static final int[][] PAWN_POS = {
        {0,  0,  0,  0,  0,  0,  0,  0,  0},
        {0,  0,  0,  0,  0,  0,  0,  0,  0},
        {0,  0,  0,  0,  0,  0,  0,  0,  0},
        {0,  0,  0,  0,  0,  0,  0,  0,  0},
        {10, 15, 20, 25, 30, 25, 20, 15, 10},
        {20, 30, 40, 50, 60, 50, 40, 30, 20},
        {30, 40, 50, 60, 70, 60, 50, 40, 30},
        {40, 50, 60, 70, 80, 70, 60, 50, 40},
        {50, 60, 70, 80, 90, 80, 70, 60, 50},
        {0,  0,  0,  0,  0,  0,  0,  0,  0}
    };

    // 马位置价值
    private static final int[][] HORSE_POS = {
        {0,  0,  10, 20, 20, 20, 10, 0,  0},
        {0,  10, 30, 40, 40, 40, 30, 10, 0},
        {10, 20, 50, 60, 70, 60, 50, 20, 10},
        {20, 30, 60, 80, 90, 80, 60, 30, 20},
        {20, 30, 60, 80, 90, 80, 60, 30, 20},
        {10, 20, 50, 60, 70, 60, 50, 20, 10},
        {0,  10, 30, 40, 40, 40, 30, 10, 0},
        {0,  0,  10, 20, 20, 20, 10, 0,  0},
        {0,  0,  0,  0,  0,  0,  0,  0,  0},
        {0,  0,  0,  0,  0,  0,  0,  0,  0}
    };

    // 炮位置价值
    private static final int[][] CANNON_POS = {
        {0,  0,  10, 20, 20, 20, 10, 0,  0},
        {0,  10, 20, 30, 30, 30, 20, 10, 0},
        {10, 20, 30, 50, 50, 50, 30, 20, 10},
        {20, 30, 50, 70, 70, 70, 50, 30, 20},
        {20, 30, 50, 70, 70, 70, 50, 30, 20},
        {10, 20, 30, 50, 50, 50, 30, 20, 10},
        {0,  10, 20, 30, 30, 30, 20, 10, 0},
        {0,  0,  10, 20, 20, 20, 10, 0,  0},
        {0,  0,  10, 15, 15, 15, 10, 0,  0},
        {0,  0,  0,  0,  0,  0,  0,  0,  0}
    };

    // 车位置价值
    private static final int[][] ROOK_POS = {
        {20, 30, 40, 50, 60, 50, 40, 30, 20},
        {20, 30, 40, 50, 60, 50, 40, 30, 20},
        {20, 30, 40, 50, 60, 50, 40, 30, 20},
        {30, 40, 50, 60, 70, 60, 50, 40, 30},
        {30, 40, 50, 60, 70, 60, 50, 40, 30},
        {20, 30, 40, 50, 60, 50, 40, 30, 20},
        {20, 30, 40, 50, 60, 50, 40, 30, 20},
        {20, 30, 40, 50, 60, 50, 40, 30, 20},
        {30, 40, 50, 60, 70, 60, 50, 40, 30},
        {30, 40, 50, 60, 70, 60, 50, 40, 30}
    };

    public ChessBot(ChessGame game, int color) {
        this.game = game;
        this.color = color;
        this.random = new Random();
        this.searchDepth = 4; // 搜索深度
    }

    public void setSearchDepth(int depth) {
        this.searchDepth = depth;
    }

    /** 设置噪声因子: 0=最优走法, 值越大越随机 */
    public void setNoiseFactor(float factor) {
        this.noiseFactor = factor;
    }

    /**
     * 计算并返回最佳走法
     */
    public ChessGame.Move findBestMove() {
        List<ChessGame.Move> moves = game.getAllValidMoves();
        if (moves.isEmpty()) return null;
        if (moves.size() == 1) return moves.get(0);

        int[][] savedBoard = game.copyBoard();
        int savedPlayer = game.getCurrentPlayer();

        int bestScore = Integer.MIN_VALUE;
        List<ChessGame.Move> bestMoves = new ArrayList<>();

        for (ChessGame.Move move : moves) {
            game.makeMove(move);
            int score = -alphaBeta(searchDepth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE, -color);
            game.undoMove(move);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }

        game.loadBoard(savedBoard, savedPlayer);

        // 噪声机制: noiseFactor越大, 越倾向于从非最优走法中随机选
        if (noiseFactor > 0.01f && moves.size() > 2) {
            // 收集评分靠前的走法 (前 noiseFactor * moves.size() 个)
            int poolSize = Math.max(2, (int)(moves.size() * noiseFactor * 0.5f + 2));
            if (poolSize >= moves.size()) poolSize = moves.size();
            
            // 重新评估取前poolSize个 (已在bestMoves中，再收集次优)
            if (bestMoves.size() < poolSize) {
                // 简单随机从原列表中补足
                for (int i = 0; i < moves.size() && bestMoves.size() < poolSize; i++) {
                    ChessGame.Move m = moves.get(i);
                    boolean alreadyIn = false;
                    for (ChessGame.Move bm : bestMoves) {
                        if (bm.fromRow == m.fromRow && bm.fromCol == m.fromCol 
                            && bm.toRow == m.toRow && bm.toCol == m.toCol) {
                            alreadyIn = true;
                            break;
                        }
                    }
                    if (!alreadyIn) bestMoves.add(m);
                }
            }
            return bestMoves.get(random.nextInt(bestMoves.size()));
        }

        return bestMoves.get(random.nextInt(bestMoves.size()));
    }

    /**
     * Alpha-Beta剪枝搜索
     */
    private int alphaBeta(int depth, int alpha, int beta, int player) {
        // 检查终止条件
        if (depth <= 0) {
            return evaluate(player);
        }

        List<ChessGame.Move> moves = game.getAllValidMoves();
        if (moves.isEmpty()) {
            // 被将死
            return -99999 + (searchDepth - depth);
        }

        // 着法排序(粗略: 吃子走法优先)
        sortMoves(moves);

        for (ChessGame.Move move : moves) {
            game.makeMove(move);
            int score = -alphaBeta(depth - 1, -beta, -alpha, -player);
            game.undoMove(move);

            if (score >= beta) {
                return beta;
            }
            if (score > alpha) {
                alpha = score;
            }
        }

        return alpha;
    }

    /**
     * 评估函数 - 从Bot视角
     */
    private int evaluate(int player) {
        int score = 0;

        for (int r = 0; r < ChessGame.ROWS; r++) {
            for (int c = 0; c < ChessGame.COLS; c++) {
                int piece = game.getPiece(r, c);
                if (piece == ChessGame.EMPTY) continue;

                int type = ChessGame.typeOf(piece);
                int pieceColor = ChessGame.colorOf(piece);
                int baseValue = PIECE_VALUE[type];
                int posValue = getPositionValue(type, r, c, pieceColor);

                int totalValue = baseValue + posValue;

                // 从我方视角: 我方子为正, 对方子为负
                if (pieceColor == player) {
                    score += totalValue;
                } else {
                    score -= totalValue;
                }
            }
        }

        return score;
    }

    /**
     * 获取位置附加值
     */
    private int getPositionValue(int type, int row, int col, int pieceColor) {
        // 位置表是以黑方视角(顶行=0), 红方需要翻转行
        int r = (pieceColor == ChessGame.BLACK) ? row : (ChessGame.ROWS - 1 - row);

        switch (type) {
            case ChessGame.PAWN:
                // 过河兵加分
                int basePawn = PAWN_POS[r][col];
                boolean crossed = (pieceColor == ChessGame.RED) ? row <= ChessGame.RIVER_TOP : row >= ChessGame.RIVER_BOTTOM;
                return basePawn + (crossed ? 80 : 0);
            case ChessGame.HORSE:
                return HORSE_POS[r][col];
            case ChessGame.CANNON:
                return CANNON_POS[r][col];
            case ChessGame.ROOK:
                return ROOK_POS[r][col];
            default:
                return 0;
        }
    }

    /**
     * 简单着法排序: 吃子走法优先
     */
    private void sortMoves(List<ChessGame.Move> moves) {
        // 冒泡排序，吃子走法排前面
        int n = moves.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                int cap1 = Math.abs(moves.get(j).captured);
                int cap2 = Math.abs(moves.get(j + 1).captured);
                // 优先吃高价值子
                int v1 = cap1 == 0 ? 0 : PIECE_VALUE[cap1];
                int v2 = cap2 == 0 ? 0 : PIECE_VALUE[cap2];
                if (v1 < v2) {
                    ChessGame.Move temp = moves.get(j);
                    moves.set(j, moves.get(j + 1));
                    moves.set(j + 1, temp);
                }
            }
        }
    }
}