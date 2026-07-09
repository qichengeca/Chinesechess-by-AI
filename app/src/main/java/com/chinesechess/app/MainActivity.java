package com.chinesechess.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private BoardView boardView;
    private TextView tvStatus;
    private TextView tvTitle;
    private NetworkManager netMan;
    private boolean singlePlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        tvTitle = (TextView) findViewById(R.id.tv_title);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        boardView = (BoardView) findViewById(R.id.board_view);

        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        int botDepth = intent.getIntExtra("botDepth", 3);
        singlePlayer = intent.getBooleanExtra("singlePlayer", true);
        boolean localPvP = intent.getBooleanExtra("localPvP", false);

        tvTitle.setText(title != null ? title : "象棋");

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (netMan != null) netMan.close();
                finish();
            }
        });

        boardView.setOnGameListener(new BoardView.OnGameListener() {
            @Override
            public void onStatusChanged(String status) { tvStatus.setText(status); }
            @Override
            public void onGameOver(String result) { tvStatus.setText(result); }
            @Override
            public void onSendMove(ChessGame.Move move) {
                if (netMan != null) netMan.sendMove(move);
            }
        });

        if (localPvP) {
            boardView.initLocalPvP();
        } else if (singlePlayer) {
            boardView.initSinglePlayer(botDepth);
        } else {
            boolean isHost = "象棋(主机)".equals(title);
            boardView.initLanMode(isHost);
            netMan = new NetworkManager(isHost, boardView);
            netMan.start();
        }
    }

    @Override
    public void onBackPressed() {
        if (netMan != null) netMan.close();
        super.onBackPressed();
    }
}