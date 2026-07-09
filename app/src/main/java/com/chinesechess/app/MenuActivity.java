package com.chinesechess.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        findViewById(R.id.btn_single).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDifficultyDialog();
            }
        });

        findViewById(R.id.btn_lan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanDialog();
            }
        });

        findViewById(R.id.btn_two_player).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame(0, "象棋(双人)", true, true);
            }
        });
    }

    private void showDifficultyDialog() {
        final String[] items = {"简单 (2层)", "普通 (3层)", "困难 (4层)", "地狱 (5层)"};
        final int[] depths = {2, 3, 4, 5};
        final String[] titles = {"象棋(简单)", "象棋(普通)", "象棋(困难)", "象棋(地狱)"};

        new AlertDialog.Builder(this)
            .setTitle("选择机器人难度")
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startGame(depths[which], titles[which], true, false);
                }
            })
            .show();
    }

    private void showLanDialog() {
        final String[] items = {"创建对局 (主机)", "加入对局 (客机)"};
        new AlertDialog.Builder(this)
            .setTitle("局域网对战")
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startGame(0, which == 0 ? "象棋(主机)" : "象棋(客机)", false, false);
                }
            })
            .show();
    }

    private void startGame(int botDepth, String title, boolean singlePlayer, boolean localPvP) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("botDepth", botDepth);
        intent.putExtra("singlePlayer", singlePlayer);
        intent.putExtra("localPvP", localPvP);
        startActivity(intent);
    }
}