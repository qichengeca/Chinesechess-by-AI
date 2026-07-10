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

    // ============ 自学习系统 ============
    private int gamesPlayed = 0;
    // 玩家走子热度图 (记录玩家走子目标位置)
    private final int[][] playerHeatMap = new int[ChessGame.ROWS][ChessGame.COLS];
    // 玩家各类棋子使用频率
    private final int[] playerPieceUse = new int[8];
    // 当前游戏记录 (在boardView中累加)
    private final List<ChessGame.Move> currentGamePlayerMoves = new ArrayList<>();
    private boolean learningEnabled = false;

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

    // 开局库: 位置哈希→推荐走法
    private static final java.util.Map<Long, int[]> OPENING_BOOK = new java.util.HashMap<>();
    static {
        // 红炮二平五(7,1→7,4) → 黑马8进7
        OPENING_BOOK.put(hash(7,1,7,4, 0,0,0,0), new int[]{1,7,2,7});
        // 红马二进三(7,7→6,7) → 黑车9进1
        OPENING_BOOK.put(hash(7,7,6,7, 0,0,0,0), new int[]{1,7,2,5});
        // 红车一平二(9,1→8,1) → 黑车9平8
        OPENING_BOOK.put(hash(9,1,8,1, 0,0,0,0), new int[]{2,5,2,7});
        // 红车二进六 → 黑卒7进1
        OPENING_BOOK.put(hash(8,7,4,7, 0,0,0,0), new int[]{3,6,4,6});
        // 红炮打中卒 → 黑士4进5
        OPENING_BOOK.put(hash(7,4,4,4, 0,0,0,0), new int[]{0,3,1,4});
        // 红马八进七 → 黑象3进5
        OPENING_BOOK.put(hash(7,1,5,2, 0,0,0,0), new int[]{0,2,2,4});
    }

    private static long hash(int... vals) {
        long h = 0;
        for (int v : vals) h = h * 31 + v;
        return h;
    }

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

    /** 启用/禁用自学习 (仅普通及以上难度) */
    public void setLearningEnabled(boolean enabled) {
        this.learningEnabled = enabled;
    }

    /** 获取已进行的学习局数 */
    public int getGamesPlayed() { return gamesPlayed; }

    /** 记录玩家的一步棋 */
    public void recordPlayerMove(ChessGame.Move move) {
        if (!learningEnabled) return;
        currentGamePlayerMoves.add(new ChessGame.Move(
                move.fromRow, move.fromCol, move.toRow, move.toCol, move.captured));
    }

    /** 一局结束，处理学习数据 */
    public void endGame(boolean playerWon) {
        if (!learningEnabled) return;
        gamesPlayed++;

        // 衰减旧热度 (保留65%，学习更快)
        for (int r = 0; r < ChessGame.ROWS; r++) {
            for (int c = 0; c < ChessGame.COLS; c++) {
                playerHeatMap[r][c] = (int)(playerHeatMap[r][c] * 0.65f);
            }
        }

        // 分析本局玩家走法
        for (ChessGame.Move m : currentGamePlayerMoves) {
            int piece = Math.abs(m.captured);
            if (piece > 0) {
                playerHeatMap[m.toRow][m.toCol] += 20; // 吃子位热度
                playerHeatMap[m.fromRow][m.fromCol] += 8;  // 出发位也记
            } else {
                playerHeatMap[m.toRow][m.toCol] += 7;
            }
        }

        // 玩家赢了 → AI学得更猛
        if (playerWon) {
            for (ChessGame.Move m : currentGamePlayerMoves) {
                playerHeatMap[m.toRow][m.toCol] += 15;
                playerHeatMap[m.fromRow][m.fromCol] += 6;
            }
        }

        currentGamePlayerMoves.clear();
    }

    /**
     * 计算并返回最佳走法
     */
    public ChessGame.Move findBestMove() {
        List<ChessGame.Move> moves = game.getAllValidMoves();
        if (moves.isEmpty()) return null;
        if (moves.size() == 1) return moves.get(0);

        // 开局库查询 (前10回合)
        if (gamesPlayed <= 2 && searchDepth >= 3) {
            ChessGame.Move last = game.getLastMove();
            if (last != null) {
                long key = hash(last.fromRow, last.fromCol, last.toRow, last.toCol, 0,0,0,0);
                int[] book = OPENING_BOOK.get(key);
                if (book != null) {
                    for (ChessGame.Move m : moves) {
                        if (m.fromRow == book[0] && m.fromCol == book[1]
                            && m.toRow == book[2] && m.toCol == book[3]) {
                            // 开局库命中，80%概率直接使用
                            if (random.nextFloat() < 0.8f) return m;
                        }
                    }
                }
            }
        }

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
     * Alpha-Beta剪枝搜索 (含检查延伸 + 静态搜索)
     */
    private int alphaBeta(int depth, int alpha, int beta, int player) {
        if (depth <= 0) {
            return quiescenceSearch(alpha, beta, player);
        }

        List<ChessGame.Move> moves = game.getAllValidMoves();
        if (moves.isEmpty()) {
            return -99999 + (searchDepth - depth);
        }

        sortMoves(moves);

        for (ChessGame.Move move : moves) {
            game.makeMove(move);
            int score = -alphaBeta(depth - 1, -beta, -alpha, -player);
            game.undoMove(move);

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }

        return alpha;
    }

    /**
     * 静态搜索: 仅搜吃子，最大深度4层防栈溢出
     */
    private int quiescenceSearch(int alpha, int beta, int player) {
        return quiescenceSearch(alpha, beta, player, 0);
    }

    private int quiescenceSearch(int alpha, int beta, int player, int qDepth) {
        int standPat = evaluate(player);
        if (standPat >= beta) return beta;
        if (standPat > alpha) alpha = standPat;
        if (qDepth >= 4) return alpha; // 深度上限

        List<ChessGame.Move> captures = new ArrayList<>();
        for (ChessGame.Move m : game.getAllValidMoves()) {
            if (Math.abs(m.captured) > 0) captures.add(m);
        }
        if (captures.isEmpty()) return alpha;

        sortMoves(captures);

        for (ChessGame.Move move : captures) {
            game.makeMove(move);
            int score = -quiescenceSearch(-beta, -alpha, -player, qDepth + 1);
            game.undoMove(move);

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }

        return alpha;
    }

    /**
     * 评估函数 - 从Bot视角 (含王安全 + 机动性 + 学习)
     */
    private int evaluate(int player) {
        int score = 0;

        // 找双方王位置
        int myKingR = -1, myKingC = -1, oppKingR = -1, oppKingC = -1;
        for (int r = 0; r < ChessGame.ROWS; r++) {
            for (int c = 0; c < ChessGame.COLS; c++) {
                int p = game.getPiece(r, c);
                if (ChessGame.typeOf(p) == ChessGame.KING) {
                    if (ChessGame.colorOf(p) == player) { myKingR = r; myKingC = c; }
                    else { oppKingR = r; oppKingC = c; }
                }
            }
        }

        // 学习强度随局数增长 (最多3倍)
        int learnMul = learningEnabled ? Math.min(gamesPlayed + 1, 4) : 1;

        for (int r = 0; r < ChessGame.ROWS; r++) {
            for (int c = 0; c < ChessGame.COLS; c++) {
                int piece = game.getPiece(r, c);
                if (piece == ChessGame.EMPTY) continue;

                int type = ChessGame.typeOf(piece);
                int pieceColor = ChessGame.colorOf(piece);
                int baseValue = PIECE_VALUE[type];
                int posValue = getPositionValue(type, r, c, pieceColor);
                int totalValue = baseValue + posValue;

                if (pieceColor == player) {
                    score += totalValue;
                    // 王安全: 我方子距离我方王越近越有防守价值
                    if (type != ChessGame.KING && myKingR >= 0) {
                        int dist = Math.abs(r - myKingR) + Math.abs(c - myKingC);
                        if (dist <= 2) score += 15 * (3 - dist);
                    }
                } else {
                    score -= totalValue;
                    // 学习: 对方棋子在我方热区 → AI警惕
                    if (learningEnabled && gamesPlayed > 0) {
                        int heat = playerHeatMap[r][c];
                        if (heat > 5) {
                            score -= heat * learnMul * 3;
                        }
                    }
                    // 对方子靠近我方王 → 威胁
                    if (myKingR >= 0) {
                        int dist = Math.abs(r - myKingR) + Math.abs(c - myKingC);
                        if (dist <= 3) score -= (4 - dist) * 20;
                    }
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
     * MVV-LVA着法排序: 优先考虑高价值被吃子 + 吃子走法优先
     */
    private void sortMoves(List<ChessGame.Move> moves) {
        int n = moves.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                int cap1 = Math.abs(moves.get(j).captured);
                int cap2 = Math.abs(moves.get(j + 1).captured);
                // MVV: 优先吃高价值子
                int v1 = cap1 == 0 ? 0 : PIECE_VALUE[Math.min(cap1, 7)];
                int v2 = cap2 == 0 ? 0 : PIECE_VALUE[Math.min(cap2, 7)];
                if (v1 < v2) {
                    ChessGame.Move temp = moves.get(j);
                    moves.set(j, moves.get(j + 1));
                    moves.set(j + 1, temp);
                }
            }
        }
    }
}
