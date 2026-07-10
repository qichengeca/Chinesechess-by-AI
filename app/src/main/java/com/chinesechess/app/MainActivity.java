package com.chinesechess.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity
        implements NetworkManager.NetworkListener {

    private BoardView boardView;
    private TextView tvStatus;
    private TextView tvTitle;
    private TextView btnRematch;
    private TextView btnSwapSide;
    private NetworkManager netMan;
    private boolean singlePlayer;
    private boolean localPvP;
    private boolean isLanMode;
    private int playerColor;
    private int botDepth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        SoundManager.init(this);

        tvTitle = (TextView) findViewById(R.id.tv_title);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        boardView = (BoardView) findViewById(R.id.board_view);
        btnRematch = (TextView) findViewById(R.id.btn_rematch);
        btnSwapSide = (TextView) findViewById(R.id.btn_swap_side);

        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        botDepth = intent.getIntExtra("botDepth", 3);
        singlePlayer = intent.getBooleanExtra("singlePlayer", true);
        localPvP = intent.getBooleanExtra("localPvP", false);
        isLanMode = !singlePlayer && !localPvP;
        playerColor = intent.getIntExtra("playerColor", ChessGame.RED);
        String hostIp = intent.getStringExtra("hostIp");

        tvTitle.setText(title != null ? title : "象棋");

        // 返回键
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (netMan != null) netMan.close();
                finish();
            }
        });

        // 换边按钮 (仅AI和LAN模式显示)
        if (singlePlayer || isLanMode) {
            btnSwapSide.setVisibility(View.VISIBLE);
            btnSwapSide.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    swapSide();
                }
            });
        }

        // 再来一局按钮
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
            @Override public void onStatusChanged(String status) { tvStatus.setText(status); }
            @Override public void onGameOver(String result) {
                tvStatus.setText(result);
                btnRematch.setVisibility(View.VISIBLE);
            }
            @Override public void onSendMove(ChessGame.Move move) {
                if (netMan != null) netMan.sendMove(move);
            }
            @Override public void onRematchRequested() {}
        });

        startGame();
    }

    private void startGame() {
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
            String hostIp = getIntent().getStringExtra("hostIp");
            if (hostIp != null && !hostIp.isEmpty()) netMan.setHostIp(hostIp);
            netMan.start();
        }
    }

    private void swapSide() {
        if (isLanMode && netMan != null) {
            Toast.makeText(this, "请先返回重新创建对局来换边", Toast.LENGTH_SHORT).show();
            return;
        }
        playerColor = -playerColor;
        startGame();
    }

    // ==================== NetworkListener ====================

    @Override
    public void onRematchRequest() {
        new AlertDialog.Builder(this)
            .setTitle("再来一局")
            .setMessage("对方请求再来一局，是否同意？")
            .setPositiveButton("同意", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    netMan.acceptRematch();
                    boardView.restartGame();
                    btnRematch.setVisibility(View.GONE);
                }
            })
            .setNegativeButton("拒绝", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    netMan.declineRematch();
                }
            })
            .setCancelable(false)
            .show();
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

    @Override
    public void onToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ==================== 生命周期 ====================

    @Override
    public void onBackPressed() {
        if (netMan != null) netMan.close();
        super.onBackPressed();
    }
}