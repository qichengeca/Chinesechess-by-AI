package com.chinesechess.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 局域网对战网络管理器
 * - TCP(12345): 走法传输 + 再来一局协商
 * - UDP(12346): 房间发现(主机响应广播)
 * 协议:
 *   MOVE: "fromRow,fromCol,toRow,toCol"
 *   REMATCH_REQUEST / REMATCH_ACCEPT / REMATCH_DECLINE
 *   CHESS_DISCOVER → CHESS_ROOM|name|players|ip
 */
public class NetworkManager {

    private static final String TAG = "NetworkManager";
    private static final int TCP_PORT = 12345;
    private static final int UDP_PORT = 12346;

    private final boolean isHost;
    private final BoardView boardView;
    private final Handler handler;
    private final NetworkListener listener;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private DatagramSocket udpSocket;
    private Thread udpThread;
    private int playerCount = 1; // 1=等待中, 2=已满

    private String myRoomName;
    private String connectHostIp;

    public interface NetworkListener {
        void onRematchRequest();
        void onRematchAccepted();
        void onRematchDeclined();
        void onToast(String msg);
    }

    public NetworkManager(boolean isHost, BoardView boardView, NetworkListener listener) {
        this.isHost = isHost;
        this.boardView = boardView;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        this.myRoomName = "棋手" + (isHost ? "A" : "B");
    }

    /** 设置连接目标IP (客机使用) */
    public void setHostIp(String ip) {
        this.connectHostIp = ip;
    }

    /** 启动 */
    public void start() {
        running = true;
        if (isHost) {
            startUdpResponder();
            startTcpServer();
        } else {
            startTcpClient();
        }
    }

    // ==================== UDP 房间响应 ====================

    private void startUdpResponder() {
        udpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    udpSocket = new DatagramSocket(UDP_PORT);
                    udpSocket.setBroadcast(true);
                    udpSocket.setSoTimeout(3000);
                    byte[] buf = new byte[256];
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);

                    while (running) {
                        try {
                            udpSocket.receive(pkt);
                            String msg = new String(pkt.getData(), 0, pkt.getLength(), "UTF-8");
                            if ("CHESS_DISCOVER".equals(msg.trim())) {
                                String localIp = getLocalIp();
                                if (localIp == null) localIp = "127.0.0.1";
                                String resp = "CHESS_ROOM|" + myRoomName + "|" + playerCount + "|" + localIp;
                                byte[] respData = resp.getBytes("UTF-8");
                                DatagramPacket respPkt = new DatagramPacket(
                                        respData, respData.length,
                                        pkt.getAddress(), pkt.getPort());
                                udpSocket.send(respPkt);
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            // 超时继续
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "UDP responder error: " + e.getMessage());
                } finally {
                    if (udpSocket != null) udpSocket.close();
                }
            }
        }, "UdpResponder");
        udpThread.setDaemon(true);
        udpThread.start();
    }

    // ==================== TCP 主机 ====================

    private void startTcpServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(TCP_PORT);
                    serverSocket.setReuseAddress(true);
                    serverSocket.setSoTimeout(3000);
                    Log.i(TAG, "TCP Server waiting on " + TCP_PORT);

                    while (running && playerCount < 2) {
                        try {
                            socket = serverSocket.accept();
                            playerCount = 2;
                            Log.i(TAG, "Client connected: " + socket.getInetAddress());
                            setupStreams();
                            showToast("客机已连接！");
                            readLoop();
                        } catch (java.net.SocketTimeoutException ignored) {}
                    }
                } catch (IOException e) {
                    Log.e(TAG, "TCP Server error: " + e.getMessage());
                    showToast("网络服务失败");
                }
            }
        }, "TcpServer").start();
    }

    // ==================== TCP 客机 ====================

    private void startTcpClient() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String ip = connectHostIp != null ? connectHostIp : "127.0.0.1";
                    Log.i(TAG, "Client connecting to " + ip);
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, TCP_PORT), 15000);
                    Log.i(TAG, "Client connected!");
                    setupStreams();
                    showToast("已连接到主机！");
                    readLoop();
                } catch (IOException e) {
                    Log.e(TAG, "Client error: " + e.getMessage());
                    showToast("连接失败: " + e.getMessage());
                }
            }
        }, "TcpClient").start();
    }

    // ==================== 流与读取 ====================

    private void setupStreams() throws IOException {
        writer = new PrintWriter(socket.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                final String msg = line.trim();
                if (msg.equals("REMATCH_REQUEST")) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            if (listener != null) listener.onRematchRequest();
                        }
                    });
                } else if (msg.equals("REMATCH_ACCEPT")) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            boardView.restartGame();
                            if (listener != null) listener.onRematchAccepted();
                        }
                    });
                } else if (msg.equals("REMATCH_DECLINE")) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            if (listener != null) listener.onRematchDeclined();
                        }
                    });
                } else {
                    final ChessGame.Move move = parseMove(msg);
                    if (move != null) {
                        handler.post(new Runnable() {
                            @Override public void run() {
                                boardView.receiveRemoteMove(move);
                            }
                        });
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                Log.w(TAG, "Connection lost: " + e.getMessage());
                showToast("连接断开");
            }
        }
    }

    // ==================== 发送 ====================

    public void sendMove(ChessGame.Move move) {
        sendLine(move.fromRow + "," + move.fromCol + "," + move.toRow + "," + move.toCol);
    }

    public void requestRematch() {
        sendLine("REMATCH_REQUEST");
    }

    public void acceptRematch() {
        sendLine("REMATCH_ACCEPT");
    }

    public void declineRematch() {
        sendLine("REMATCH_DECLINE");
    }

    private void sendLine(final String msg) {
        if (writer == null) return;
        new Thread(new Runnable() {
            @Override public void run() {
                writer.println(msg);
            }
        }, "NetSend").start();
    }

    // ==================== 解析 ====================

    private ChessGame.Move parseMove(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length == 4) {
                return new ChessGame.Move(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    // ==================== 工具 ====================

    private String getLocalIp() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en =
                 java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                java.net.NetworkInterface intf = en.nextElement();
                for (java.util.Enumeration<InetAddress> eip =
                     intf.getInetAddresses(); eip.hasMoreElements();) {
                    InetAddress addr = eip.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void close() {
        running = false;
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        try { if (udpSocket != null) udpSocket.close(); } catch (Exception ignored) {}
        writer = null;
        reader = null;
        socket = null;
        serverSocket = null;
        udpSocket = null;
    }

    private void showToast(final String msg) {
        handler.post(new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onToast(msg);
            }
        });
    }
}