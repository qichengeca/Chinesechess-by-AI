package com.chinesechess.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 中国象棋棋盘视图
 */
public class BoardView extends View {

    // 棋子中文名称
    private static final String[] RED_NAMES = {"", "帅", "仕", "相", "馬", "車", "炮", "兵"};
    private static final String[] BLACK_NAMES = {"", "将", "士", "象", "马", "车", "炮", "卒"};

    // 颜色
    private static final int COLOR_BOARD_BG = 0xFFDEB887;       // 木色
    private static final int COLOR_LINE = 0xFF4A3728;           // 深棕线条
    private static final int COLOR_RED_PIECE = 0xFFD32F2F;      // 红方棋子
    private static final int COLOR_BLACK_PIECE = 0xFF212121;    // 黑方棋子
    private static final int COLOR_PIECE_BG = 0xFFF5DEB3;       // 棋子底色
    private static final int COLOR_SELECTED = 0x8800FF00;       // 选中高亮
    private static final int COLOR_LAST_MOVE = 0x88FFEB3B;      // 上一步高亮
    private static final int COLOR_VALID_MOVE = 0x440000FF;      // 合法走法标记

    private ChessGame game;
    private ChessBot bot;
    private Handler handler;

    // 棋盘几何
    private float boardLeft, boardTop, boardRight, boardBottom;
    private float cellWidth, cellHeight;
    private float pieceRadius;
    private float padding;

    // 选中状态
    private int selectedRow = -1, selectedCol = -1;
    private List<ChessGame.Move> validMoves;

    // 游戏状态
    private boolean gameOver = false;
    private boolean isBotThinking = false;
    private String statusText = "红方走棋";

    // 多人模式
    private boolean isLanMode = false;
    private boolean isHost = false;
    private int myColor = ChessGame.RED;

    // 本地双人模式
    private boolean isLocalPvP = false;

    // 随机数(噪声/延迟)
    private final Random random = new Random();

    // 回调
    private OnGameListener listener;

    public interface OnGameListener {
        void onStatusChanged(String status);
        void onGameOver(String result);
        void onSendMove(ChessGame.Move move);
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        game = new ChessGame();
        bot = new ChessBot(game, ChessGame.BLACK);
        handler = new Handler(Looper.getMainLooper());
        setFocusable(true);
    }

    public ChessGame getGame() { return game; }

    public void setOnGameListener(OnGameListener l) { this.listener = l; }

    /** 重新开始 */
    public void newGame() {
        game.reset();
        selectedRow = -1;
        selectedCol = -1;
        validMoves = null;
        gameOver = false;
        isBotThinking = false;
        statusText = "红方走棋";
        if (listener != null) listener.onStatusChanged(statusText);
        invalidate();
    }

    /** 设置AI搜索深度 */
    public void setBotDifficulty(int depth) {
        bot.setSearchDepth(depth);
    }

    /** 初始化单人模式 — 设置难度 + 噪声因子 */
    public void initSinglePlayer(int botDepth) {
        this.isLanMode = false;
        this.myColor = ChessGame.RED;
        bot.setSearchDepth(botDepth);
        // 噪声因子: 深度越浅越随机 → 更像人
        float nf;
        if (botDepth <= 2) nf = 0.7f;
        else if (botDepth == 3) nf = 0.3f;
        else if (botDepth == 4) nf = 0.1f;
        else nf = 0.0f;
        bot.setNoiseFactor(nf);
        newGame();
    }

    /** 初始化局域网模式 */
    public void initLanMode(boolean isHost) {
        this.isLanMode = true;
        this.isHost = isHost;
        this.myColor = isHost ? ChessGame.RED : ChessGame.BLACK;
        bot = null; // 局域网模式不用Bot
        newGame();
    }

    /** 初始化本地双人对战模式 */
    public void initLocalPvP() {
        this.isLocalPvP = true;
        this.isLanMode = false;
        this.bot = null; // 双人模式不用Bot
        newGame();
        statusText = "红方走棋";
        if (listener != null) listener.onStatusChanged(statusText);
    }

    /** 接收来自网络的走法并执行 */
    public void receiveRemoteMove(ChessGame.Move move) {
        if (!isLanMode) return;
        game.makeMove(move);
        selectedRow = -1;
        selectedCol = -1;
        validMoves = null;
        isBotThinking = false;
        invalidate();

        if (game.isGameOver()) {
            gameOver = true;
            String result = game.getCurrentPlayer() == ChessGame.RED ? "黑方胜!" : "红方胜!";
            statusText = result;
            if (listener != null) {
                listener.onStatusChanged(statusText);
                listener.onGameOver(result);
            }
            showToast(result);
        } else {
            statusText = (game.getCurrentPlayer() == ChessGame.RED) ? "红方走棋" : "黑方走棋";
            if (listener != null) listener.onStatusChanged(statusText);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        // 确保正方形棋盘 + 边距
        int boardSize = Math.min(width, height);
        padding = boardSize * 0.04f;
        boardSize = (int)(boardSize - padding * 2);

        // 棋盘 8:9 宽高比 (8个格子宽, 9个格子高)
        cellWidth = boardSize / 8f;
        cellHeight = boardSize / 9f;
        pieceRadius = Math.min(cellWidth, cellHeight) * 0.42f;

        // 棋盘水平居中
        boardLeft = (width - cellWidth * 8) / 2f;
        // 棋盘垂直居中偏下 (为顶部标题栏留空间)
        boardTop = (height - cellHeight * 9) / 2f + cellHeight * 0.5f;
        boardRight = boardLeft + cellWidth * 8;
        boardBottom = boardTop + cellHeight * 9;

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellWidth <= 0 || cellHeight <= 0) return;

        drawBoard(canvas);
        drawPieces(canvas);
        drawHighlights(canvas);
    }

    private void drawBoard(Canvas canvas) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // 背景
        paint.setColor(COLOR_BOARD_BG);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(boardLeft - padding, boardTop - padding,
                boardRight + padding, boardBottom + padding, paint);

        // 外框
        paint.setColor(COLOR_LINE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawRect(boardLeft, boardTop, boardRight, boardBottom, paint);

        // 网格线
        paint.setStrokeWidth(1.5f);

        // 横线
        for (int i = 0; i < ChessGame.ROWS; i++) {
            float y = boardTop + i * cellHeight;
            canvas.drawLine(boardLeft, y, boardRight, y, paint);
        }

        // 竖线 - 河界区域内部竖线断开
        for (int i = 0; i < ChessGame.COLS; i++) {
            float x = boardLeft + i * cellWidth;
            if (i == 0 || i == ChessGame.COLS - 1) {
                // 边线连在一起
                canvas.drawLine(x, boardTop, x, boardBottom, paint);
            } else {
                // 上半部分
                canvas.drawLine(x, boardTop, x, boardTop + ChessGame.RIVER_TOP * cellHeight, paint);
                // 下半部分
                canvas.drawLine(x, boardTop + ChessGame.RIVER_BOTTOM * cellHeight, x, boardBottom, paint);
            }
        }

        // 九宫格斜线
        paint.setStrokeWidth(1f);
        // 上方九宫(黑方)
        canvas.drawLine(boardLeft + 3 * cellWidth, boardTop,
                boardLeft + 5 * cellWidth, boardTop + 2 * cellHeight, paint);
        canvas.drawLine(boardLeft + 5 * cellWidth, boardTop,
                boardLeft + 3 * cellWidth, boardTop + 2 * cellHeight, paint);
        // 下方九宫(红方)
        canvas.drawLine(boardLeft + 3 * cellWidth, boardTop + 7 * cellHeight,
                boardLeft + 5 * cellWidth, boardTop + 9 * cellHeight, paint);
        canvas.drawLine(boardLeft + 5 * cellWidth, boardTop + 7 * cellHeight,
                boardLeft + 3 * cellWidth, boardTop + 9 * cellHeight, paint);

        // 楚河汉界文字
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(COLOR_LINE);
        textPaint.setTextSize(cellHeight * 0.5f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float riverY = boardTop + 4.5f * cellHeight;
        float riverLeft = boardLeft + cellWidth;
        float riverRight = boardLeft + 7 * cellWidth;
        canvas.drawText("楚  河          汉  界",
                (riverLeft + riverRight) / 2, riverY + cellHeight * 0.15f, textPaint);
    }

    private void drawPieces(Canvas canvas) {
        Paint bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setStyle(Paint.Style.FILL);

        Paint borderPaint = new Paint();
        borderPaint.setAntiAlias(true);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.5f);

        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(pieceRadius * 1.1f);

        int[][] board = game.getBoard();
        for (int r = 0; r < ChessGame.ROWS; r++) {
            for (int c = 0; c < ChessGame.COLS; c++) {
                int piece = board[r][c];
                if (piece == ChessGame.EMPTY) continue;

                float cx = boardLeft + c * cellWidth;
                float cy = boardTop + r * cellHeight;

                boolean isRed = ChessGame.colorOf(piece) == ChessGame.RED;

                // 棋子阴影
                Paint shadowPaint = new Paint();
                shadowPaint.setAntiAlias(true);
                shadowPaint.setColor(0x40000000);
                canvas.drawCircle(cx + 2f, cy + 2f, pieceRadius, shadowPaint);

                // 棋子底色
                bgPaint.setColor(COLOR_PIECE_BG);
                canvas.drawCircle(cx, cy, pieceRadius, bgPaint);

                // 棋子边框
                borderPaint.setColor(isRed ? COLOR_RED_PIECE : COLOR_BLACK_PIECE);
                canvas.drawCircle(cx, cy, pieceRadius, borderPaint);

                // 内圈
                borderPaint.setStrokeWidth(1f);
                canvas.drawCircle(cx, cy, pieceRadius * 0.85f, borderPaint);

                // 棋子文字
                textPaint.setColor(isRed ? COLOR_RED_PIECE : COLOR_BLACK_PIECE);
                String name = isRed ? RED_NAMES[ChessGame.typeOf(piece)]
                                    : BLACK_NAMES[ChessGame.typeOf(piece)];
                float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2;
                canvas.drawText(name, cx, textY, textPaint);
            }
        }
    }

    private void drawHighlights(Canvas canvas) {
        // 上一步走法高亮
        ChessGame.Move lastMove = game.getLastMove();
        if (lastMove != null) {
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(COLOR_LAST_MOVE);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(100);
            float cx1 = boardLeft + lastMove.fromCol * cellWidth;
            float cy1 = boardTop + lastMove.fromRow * cellHeight;
            float cx2 = boardLeft + lastMove.toCol * cellWidth;
            float cy2 = boardTop + lastMove.toRow * cellHeight;
            canvas.drawCircle(cx1, cy1, pieceRadius * 0.8f, p);
            canvas.drawCircle(cx2, cy2, pieceRadius * 0.8f, p);
        }

        // 选中棋子高亮
        if (selectedRow >= 0 && selectedCol >= 0) {
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(COLOR_SELECTED);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(120);
            float cx = boardLeft + selectedCol * cellWidth;
            float cy = boardTop + selectedRow * cellHeight;
            canvas.drawCircle(cx, cy, pieceRadius + 4, p);
        }

        // 合法走法标记
        if (validMoves != null) {
            for (ChessGame.Move m : validMoves) {
                float cx = boardLeft + m.toCol * cellWidth;
                float cy = boardTop + m.toRow * cellHeight;
                int targetPiece = game.getPiece(m.toRow, m.toCol);

                Paint p = new Paint();
                p.setAntiAlias(true);
                if (targetPiece != ChessGame.EMPTY) {
                    // 吃子标记: 红色圆圈
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(4f);
                    p.setColor(0xCCFF0000);
                    canvas.drawCircle(cx, cy, pieceRadius + 4, p);
                } else {
                    // 移动标记: 小圆点
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(COLOR_VALID_MOVE);
                    canvas.drawCircle(cx, cy, pieceRadius * 0.25f, p);
                }
            }
        }
    }

@Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
        if (gameOver || isBotThinking) return true;
        // 局域网模式: 只能操作己方回合
        if (isLanMode && game.getCurrentPlayer() != myColor) return true;

        float x = event.getX();
        float y = event.getY();

        int col = Math.round((x - boardLeft) / cellWidth);
        int row = Math.round((y - boardTop) / cellHeight);

        // 容错: 检查是否在棋盘内
        float nearestX = boardLeft + col * cellWidth;
        float nearestY = boardTop + row * cellHeight;
        float distX = Math.abs(x - nearestX);
        float distY = Math.abs(y - nearestY);

        if (distX > cellWidth * 0.55f || distY > cellHeight * 0.55f) {
            return true;
        }

        if (row < 0 || row >= ChessGame.ROWS || col < 0 || col >= ChessGame.COLS) {
            return true;
        }

        handleClick(row, col);
        return true;
    }

    private void handleClick(int row, int col) {
        int piece = game.getPiece(row, col);
        int currentPlayer = game.getCurrentPlayer();

        if (selectedRow >= 0 && selectedCol >= 0) {
            // 已有选中, 尝试走棋
            // 如果点击己方棋子, 切换选择
            if (piece != ChessGame.EMPTY && ChessGame.colorOf(piece) == currentPlayer) {
                selectPiece(row, col);
                return;
            }

            // 尝试移动
            if (validMoves != null) {
                for (ChessGame.Move m : validMoves) {
                    if (m.toRow == row && m.toCol == col) {
                        executeMove(m);
                        return;
                    }
                }
            }

            // 无效点击, 取消选中
            selectedRow = -1;
            selectedCol = -1;
            validMoves = null;
            invalidate();
        } else {
            // 无选中, 选择己方棋子
            if (piece != ChessGame.EMPTY && ChessGame.colorOf(piece) == currentPlayer) {
                selectPiece(row, col);
            }
        }
    }

    private void selectPiece(int row, int col) {
        selectedRow = row;
        selectedCol = col;
        validMoves = game.getValidMoves(row, col);
        invalidate();
    }

    private void executeMove(ChessGame.Move move) {
        game.makeMove(move);
        selectedRow = -1;
        selectedCol = -1;
        validMoves = null;
        invalidate();

        // 局域网模式: 发送走法
        if (isLanMode && listener != null) {
            listener.onSendMove(move);
        }

        // 检查游戏是否结束
        if (game.isGameOver()) {
            gameOver = true;
            String result = game.getCurrentPlayer() == ChessGame.RED ? "黑方胜!" : "红方胜!";
            statusText = result;
            if (listener != null) {
                listener.onStatusChanged(statusText);
                listener.onGameOver(result);
            }
            showToast(result);
            return;
        }

        // 单人模式: Bot走棋 (随机延迟模拟人味)
        if (!isLanMode && !isLocalPvP && game.getCurrentPlayer() == ChessGame.BLACK) {
            statusText = "黑方思考中...";
            if (listener != null) listener.onStatusChanged(statusText);
            isBotThinking = true;
            invalidate();

            int delay = 300 + random.nextInt(900); // 300-1200ms
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    botMove();
                }
            }, delay);
        }

        // 局域网/双人模式: 更新状态文字
        if (isLanMode || isLocalPvP) {
            statusText = (game.getCurrentPlayer() == ChessGame.RED) ? "红方走棋" : "黑方走棋";
            if (listener != null) listener.onStatusChanged(statusText);
        }
    }

    private void botMove() {
        ChessGame.Move move = bot.findBestMove();
        if (move == null) {
            // Bot 无合法走法——被将死，玩家胜
            gameOver = true;
            isBotThinking = false;
            statusText = "红方胜!";
            if (listener != null) {
                listener.onStatusChanged(statusText);
                listener.onGameOver(statusText);
            }
            showToast(statusText);
            invalidate();
            return;
        }
        game.makeMove(move);
        isBotThinking = false;
        invalidate();

        if (game.isGameOver()) {
            gameOver = true;
            String result = game.getCurrentPlayer() == ChessGame.RED ? "黑方胜!" : "红方胜!";
            statusText = result;
            if (listener != null) {
                listener.onStatusChanged(statusText);
                listener.onGameOver(result);
            }
            showToast(result);
        } else {
            statusText = "红方走棋";
            if (listener != null) listener.onStatusChanged(statusText);
        }
    }

    private void showToast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
