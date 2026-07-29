package com.chinesechess.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity
        implements NetworkManager.NetworkListener {

    private BoardView boardView;
    private TextView tvStatus, tvTitle, tvTimer, btnRematch, btnSwapSide, btnSurrender;
    private NetworkManager netMan;
    private boolean singlePlayer, localPvP, isLanMode;
    private int playerColor, botDepth;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        SoundManager.init(this);

        tvTitle = (TextView) findViewById(R.id.tv_title);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvTimer = (TextView) findViewById(R.id.tv_timer);
        boardView = (BoardView) findViewById(R.id.board_view);
        btnRematch = (TextView) findViewById(R.id.btn_rematch);
        btnSwapSide = (TextView) findViewById(R.id.btn_swap_side);
        btnSurrender = (TextView) findViewById(R.id.btn_surrender);

        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        botDepth = intent.getIntExtra("botDepth", 3);
        singlePlayer = intent.getBooleanExtra("singlePlayer", true);
        localPvP = intent.getBooleanExtra("localPvP", false);
        isLanMode = !singlePlayer && !localPvP;
        playerColor = intent.getIntExtra("playerColor", ChessGame.RED);
        String hostIp = intent.getStringExtra("hostIp");
        tvTitle.setText(title != null ? title : "象棋");

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (netMan != null) netMan.close();
                finish();
            }
        });

        if (singlePlayer || isLanMode) {
            btnSwapSide.setVisibility(View.VISIBLE);
            btnSwapSide.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { swapSide(); }
            });
        }

        btnSurrender.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("认输").setMessage("确定认输吗？")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { boardView.surrender(); }
                    })
                    .setNegativeButton("取消", null).show();
            }
        });

        btnRematch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isLanMode && netMan != null) {
                    netMan.requestRematch();
                    Toast.makeText(MainActivity.this, "已发送再来一局请求", Toast.LENGTH_SHORT).show();
                } else {
                    boardView.restartGame();
                    btnRematch.setVisibility(View.GONE);
                }
            }
        });

        boardView.setOnGameListener(new BoardView.OnGameListener() {
            @Override public void onStatusChanged(String s) { tvStatus.setText(s); }
            @Override public void onGameOver(String r) {
                tvStatus.setText(r);
                btnRematch.setVisibility(View.VISIBLE);
            }
            @Override public void onSendMove(ChessGame.Move m) {
                if (netMan != null) netMan.sendMove(m);
            }
            @Override public void onRematchRequested() {}
        });

        startGame();
        startTimer();
    }

    private void startGame() {
        stopTimer();
        btnRematch.setVisibility(View.GONE);
        if (localPvP) {
            boardView.initLocalPvP();
        } else if (singlePlayer) {
            boardView.initSinglePlayer(botDepth, playerColor);
        } else {
            boolean isHost = "象棋(主机)".equals(getIntent().getStringExtra("title"));
            boardView.initLanMode(isHost, playerColor);
            if (netMan != null) netMan.close();
            netMan = new NetworkManager(isHost, boardView, this);
            String ip = getIntent().getStringExtra("hostIp");
            if (ip != null && !ip.isEmpty()) netMan.setHostIp(ip);
            netMan.start();
        }
        startTimer();
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override public void run() {
                int sec = boardView.getElapsedSeconds();
                tvTimer.setText(String.format("%02d:%02d", sec / 60, sec % 60));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
    }

    private void swapSide() {
        if (isLanMode) {
            Toast.makeText(this, "请返回重新创建对局来换边", Toast.LENGTH_SHORT).show();
            return;
        }
        playerColor = -playerColor;
        startGame();
    }

    @Override
    public void onRematchRequest() {
        new AlertDialog.Builder(this)
            .setTitle("再来一局")
            .setMessage("对方请求再来一局，是否同意？")
            .setPositiveButton("同意", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    netMan.acceptRematch();
                    boardView.restartGame();
                    btnRematch.setVisibility(View.GONE);
                }
            })
            .setNegativeButton("拒绝", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { netMan.declineRematch(); }
            })
            .setCancelable(false).show();
    }

    @Override
    public void onRematchAccepted() {
        Toast.makeText(this, "对方同意了，重新开始！", Toast.LENGTH_SHORT).show();
        btnRematch.setVisibility(View.GONE);
    }

    @Override
    public void onRematchDeclined() {
        Toast.makeText(this, "对方拒绝了再来一局", Toast.LENGTH_SHORT).show();
    }

    @Override public void onToast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        stopTimer();
        if (netMan != null) netMan.close();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        super.onDestroy();
    }
}