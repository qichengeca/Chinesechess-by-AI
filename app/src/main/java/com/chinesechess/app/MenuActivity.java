package com.chinesechess.app;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MenuActivity extends AppCompatActivity {

    private static final int DISCOVERY_PORT = 12346;
    private static final int GAME_PORT = 12345;
    private static final String ROOM_PREFIX = "CHESS_ROOM|";
    private static final String DISCOVER_MSG = "CHESS_DISCOVER";

    private List<LanRoom> discoveredRooms = new ArrayList<>();
    private Dialog lanDialog;
    private TextView lanScanningText;
    private LinearLayout lanRoomList;
    private volatile boolean isScanning = false;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        handler = new Handler(Looper.getMainLooper());

        findViewById(R.id.btn_single).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showDifficultyDialog(); }
        });

        findViewById(R.id.btn_lan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showLanLobbyDialog(); }
        });

        findViewById(R.id.btn_two_player).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame(0, "象棋(双人)", true, true, ChessGame.RED);
            }
        });
    }

    // ==================== 难度选择弹窗 ====================

    private void showDifficultyDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_difficulty);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        final int[] depths = {2, 3, 4, 5};
        final String[] titles = {"象棋(简单)", "象棋(普通)", "象棋(困难)", "象棋(地狱)"};
        final int[] btnIds = {R.id.dlg_btn_easy, R.id.dlg_btn_normal, R.id.dlg_btn_hard, R.id.dlg_btn_hell};

        // X 关闭按钮
        dialog.findViewById(R.id.dialog_btn_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        for (int i = 0; i < btnIds.length; i++) {
            final int idx = i;
            dialog.findViewById(btnIds[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (depths[idx] == 5) {
                        dialog.dismiss();
                        showHellWarning();
                    } else {
                        dialog.dismiss();
                        showSideDialog(depths[idx], titles[idx]);
                    }
                }
            });
        }

        dialog.show();
    }

    /** 地狱难度警告弹窗 */
    private void showHellWarning() {
        final Dialog warn = new Dialog(this);
        warn.requestWindowFeature(Window.FEATURE_NO_TITLE);
        warn.setContentView(R.layout.dialog_warning);
        warn.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // X 关闭 = 放弃
        warn.findViewById(R.id.warn_btn_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { warn.dismiss(); }
        });

        // 确定 = 进入地狱模式
        warn.findViewById(R.id.warn_btn_confirm).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                warn.dismiss();
                showSideDialog(5, "象棋(地狱)");
            }
        });

        warn.show();
    }

    /** 选边弹窗 */
    private void showSideDialog(final int botDepth, final String title) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择棋子颜色")
            .setItems(new String[]{"红 棋 (先手)", "黑 棋 (后手)"}, 
                new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    int color = (w == 0) ? ChessGame.RED : ChessGame.BLACK;
                    startGame(botDepth, title, true, false, color);
                }
            })
            .show();
    }

    // ==================== 局域网大厅弹窗 ====================

    private void showLanLobbyDialog() {
        lanDialog = new Dialog(this);
        lanDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        lanDialog.setContentView(R.layout.dialog_lan_lobby);
        lanDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        lanScanningText = (TextView) lanDialog.findViewById(R.id.lan_tv_scanning);
        lanRoomList = (LinearLayout) lanDialog.findViewById(R.id.lan_room_list);

        // 返回
        lanDialog.findViewById(R.id.lan_btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopScan(); lanDialog.dismiss(); }
        });

        // 创建房间
        lanDialog.findViewById(R.id.lan_btn_create).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScan();
                lanDialog.dismiss();
                showLanSideDialog();
            }
        });

        discoveredRooms.clear();
        lanRoomList.removeAllViews();
        lanScanningText.setVisibility(View.VISIBLE);
        lanScanningText.setText("正在扫描房间...");

        lanDialog.show();
        startDiscovery();
    }

    private void startDiscovery() {
        isScanning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket ds = null;
                try {
                    ds = new DatagramSocket(DISCOVERY_PORT);
                    ds.setBroadcast(true);
                    ds.setSoTimeout(2000);

                    // 发送广播
                    byte[] sendData = DISCOVER_MSG.getBytes("UTF-8");
                    DatagramPacket sendPkt = new DatagramPacket(sendData, sendData.length,
                            InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);

                    // 多轮扫描
                    for (int round = 0; round < 3 && isScanning; round++) {
                        ds.send(sendPkt);

                        // 收回复
                        long startTime = System.currentTimeMillis();
                        while (System.currentTimeMillis() - startTime < 1000 && isScanning) {
                            try {
                                byte[] buf = new byte[256];
                                DatagramPacket recvPkt = new DatagramPacket(buf, buf.length);
                                ds.receive(recvPkt);
                                String msg = new String(recvPkt.getData(), 0, recvPkt.getLength(), "UTF-8");
                                if (msg.startsWith(ROOM_PREFIX)) {
                                    parseRoom(msg, recvPkt.getAddress().getHostAddress());
                                }
                            } catch (java.net.SocketTimeoutException e) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    if (isScanning) {
                        handler.post(new Runnable() {
                            @Override public void run() {
                                if (lanScanningText != null)
                                    lanScanningText.setText("扫描失败，请检查WiFi");
                            }
                        });
                    }
                } finally {
                    if (ds != null) ds.close();
                    isScanning = false;
                    handler.post(new Runnable() {
                        @Override public void run() { updateRoomListUI(); }
                    });
                }
            }
        }, "LanDiscovery").start();
    }

    private void parseRoom(String msg, String ip) {
        // 格式: CHESS_ROOM|roomName|playerCount|hostIp
        String[] parts = msg.split("\\|");
        if (parts.length >= 4) {
            String name = parts[1];
            int players = 1;
            try { players = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
            synchronized (discoveredRooms) {
                // 去重
                for (LanRoom r : discoveredRooms) {
                    if (r.ip.equals(ip)) return;
                }
                discoveredRooms.add(new LanRoom(name, ip, players));
            }
        }
    }

    private void updateRoomListUI() {
        if (lanScanningText == null || lanRoomList == null) return;
        if (discoveredRooms.isEmpty()) {
            lanScanningText.setText("暂无可用房间\n请先由一方创建房间");
            lanScanningText.setVisibility(View.VISIBLE);
        } else {
            lanScanningText.setVisibility(View.GONE);
            lanRoomList.removeAllViews();
            synchronized (discoveredRooms) {
                for (final LanRoom room : discoveredRooms) {
                    TextView item = (TextView) getLayoutInflater().inflate(
                            android.R.layout.simple_list_item_2, lanRoomList, false);
                    item.setText(room.name);
                    item.setTextSize(16);
                    item.setTextColor(getResources().getColor(R.color.textDark));
                    item.setPadding(24, 16, 24, 16);
                    item.setBackgroundResource(R.drawable.btn_menu_bg);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, 0, 0, 12);
                    item.setLayoutParams(lp);

                    if (room.players >= 2) {
                        item.setText(room.name + "  [已满]");
                        item.setAlpha(0.5f);
                        item.setClickable(false);
                    } else {
                        item.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                stopScan();
                                lanDialog.dismiss();
                                joinRoom(room);
                            }
                        });
                    }

                    lanRoomList.addView(item);
                }
            }
        }
    }

    private void joinRoom(LanRoom room) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("title", "象棋(客机)");
        intent.putExtra("botDepth", 0);
        intent.putExtra("singlePlayer", false);
        intent.putExtra("localPvP", false);
        intent.putExtra("playerColor", ChessGame.BLACK);
        intent.putExtra("hostIp", room.ip);
        startActivity(intent);
    }

    private void showLanSideDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择棋子颜色")
            .setItems(new String[]{"红 棋 (先手)", "黑 棋 (后手)"}, 
                new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    int color = (w == 0) ? ChessGame.RED : ChessGame.BLACK;
                    startGame(0, "象棋(主机)", false, false, color);
                }
            })
            .show();
    }

    private void stopScan() { isScanning = false; }

    private void startGame(int botDepth, String title, boolean singlePlayer, 
                           boolean localPvP, int playerColor) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("botDepth", botDepth);
        intent.putExtra("singlePlayer", singlePlayer);
        intent.putExtra("localPvP", localPvP);
        intent.putExtra("playerColor", playerColor);
        startActivity(intent);
    }

    // ==================== 房间数据结构 ====================

    private static class LanRoom {
        String name;
        String ip;
        int players;
        LanRoom(String name, String ip, int players) {
            this.name = name; this.ip = ip; this.players = players;
        }
    }
}