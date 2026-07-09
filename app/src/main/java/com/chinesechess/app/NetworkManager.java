package com.chinesechess.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 局域网对战网络管理器
 * 主机(红方)启动ServerSocket等待连接；客机(黑方)主动连接主机。
 * 协议: "fromRow,fromCol,toRow,toCol"
 */
public class NetworkManager {

    private static final String TAG = "NetworkManager";
    private static final int PORT = 12345;
    private static final int TIMEOUT = 30000; // 30秒连接超时

    private final boolean isHost;
    private final BoardView boardView;
    private final Handler handler;

    private volatile boolean running = false;
    private Thread serverThread;
    private Thread clientThread;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    public NetworkManager(boolean isHost, BoardView boardView) {
        this.isHost = isHost;
        this.boardView = boardView;
        this.handler = new Handler(Looper.getMainLooper());
    }

    /** 启动网络服务 */
    public void start() {
        running = true;
        if (isHost) {
            serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    startServer();
                }
            }, "NetServer");
            serverThread.setDaemon(true);
            serverThread.start();
        } else {
            clientThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    startClient();
                }
            }, "NetClient");
            clientThread.setDaemon(true);
            clientThread.start();
        }
    }

    /** 主机: 启动ServerSocket等待客机连接 */
    private void startServer() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            serverSocket.setReuseAddress(true);
            // 设置accept超时以便能被取消
            serverSocket.setSoTimeout(3000);
            Log.i(TAG, "Server: waiting for connection on port " + PORT);

            while (running) {
                try {
                    socket = serverSocket.accept();
                    Log.i(TAG, "Server: client connected from " + socket.getInetAddress());
                    setupStreams();
                    readLoop();
                    break; // 连接后退出等待循环
                } catch (java.net.SocketTimeoutException e) {
                    // 超时继续等待
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Server error: " + e.getMessage());
            if (running) {
                showToast("网络服务启动失败: " + e.getMessage());
            }
        } finally {
            if (serverSocket != null) {
                try { serverSocket.close(); } catch (IOException ignored) {}
            }
        }
    }

    /** 客机: 连接主机 */
    private void startClient() {
        try {
            // 尝试通过局域网广播地址发现主机（简化：扫描常见网关）
            String hostIp = findHost();
            if (hostIp == null) {
                showToast("未找到主机，请确保在同一WiFi下");
                return;
            }

            Log.i(TAG, "Client: connecting to " + hostIp);
            socket = new Socket();
            socket.connect(new InetSocketAddress(hostIp, PORT), TIMEOUT);
            Log.i(TAG, "Client: connected!");

            setupStreams();
            readLoop();
        } catch (IOException e) {
            Log.e(TAG, "Client error: " + e.getMessage());
            if (running) {
                showToast("连接失败: " + e.getMessage());
            }
        }
    }

    /** 自动发现主机IP (扫描常见私有网段) */
    private String findHost() {
        // 尝试本机IP所在网段的 .1 地址（通常是网关/主机）
        try {
            String localIp = getLocalIp();
            if (localIp != null && localIp.startsWith("192.168.")) {
                String prefix = localIp.substring(0, localIp.lastIndexOf('.') + 1);
                // 尝试 .1 (路由器) 和 .2-.5 (常见主机)
                for (int i = 1; i <= 5; i++) {
                    String ip = prefix + i;
                    if (ip.equals(localIp)) continue;
                    try {
                        Socket test = new Socket();
                        test.connect(new InetSocketAddress(ip, PORT), 800);
                        test.close();
                        return ip; // 找到主机
                    } catch (IOException ignored) {}
                }
                // 全扫描 1-254 (简化: 只扫几个常见段)
                for (int i = 100; i <= 200; i++) {
                    String ip = prefix + i;
                    try {
                        Socket test = new Socket();
                        test.connect(new InetSocketAddress(ip, PORT), 400);
                        test.close();
                        return ip;
                    } catch (IOException ignored) {}
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "findHost failed: " + e.getMessage());
        }
        // 回退到 localhost (同一设备测试)
        return "127.0.0.1";
    }

    /** 获取本机局域网IP */
    private String getLocalIp() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = 
                 java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                java.net.NetworkInterface intf = en.nextElement();
                for (java.util.Enumeration<InetAddress> enumIp = 
                     intf.getInetAddresses(); enumIp.hasMoreElements();) {
                    InetAddress addr = enumIp.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getLocalIp: " + e.getMessage());
        }
        return null;
    }

    /** 建立流 */
    private void setupStreams() throws IOException {
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        showToast(isHost ? "客机已连接！" : "已连接到主机！");
    }

    /** 读取循环 */
    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                final ChessGame.Move move = parseMove(line);
                if (move != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            boardView.receiveRemoteMove(move);
                        }
                    });
                }
            }
        } catch (IOException e) {
            if (running) {
                Log.w(TAG, "Connection lost: " + e.getMessage());
                showToast("连接断开");
            }
        }
    }

    /** 发送走法 */
    public void sendMove(ChessGame.Move move) {
        if (writer == null) return;
        final String msg = move.fromRow + "," + move.fromCol + ","
                         + move.toRow + "," + move.toCol;
        new Thread(new Runnable() {
            @Override
            public void run() {
                writer.println(msg);
            }
        }, "NetSend").start();
    }

    /** 解析走法 */
    private ChessGame.Move parseMove(String line) {
        try {
            String[] parts = line.trim().split(",");
            if (parts.length == 4) {
                int fr = Integer.parseInt(parts[0]);
                int fc = Integer.parseInt(parts[1]);
                int tr = Integer.parseInt(parts[2]);
                int tc = Integer.parseInt(parts[3]);
                return new ChessGame.Move(fr, fc, tr, tc);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid move: " + line);
        }
        return null;
    }

    /** 关闭所有连接 */
    public void close() {
        running = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        writer = null;
        reader = null;
        socket = null;
    }

    private void showToast(final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                android.widget.Toast.makeText(boardView.getContext(), 
                    msg, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }
}
