package com.easychat.test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class socketserver {
    public static void main(String[] args) {
        Map<String, Socket> mp = new HashMap<>();
        ServerSocket server = null;
        try {
            server = new ServerSocket(1024);
            System.out.println("服务已经启动");

            while (true) {
                Socket socket = server.accept();
                String ip = socket.getInetAddress().getHostAddress();
                System.out.println("有客户端连接" + ip + " port: " + socket.getPort());
                InputStream inputStream = socket.getInputStream();

                mp.put(ip + socket.getPort(), socket);

                new Thread(() -> {
                    while (true) {
                        try {
                            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                            BufferedReader reader = new BufferedReader(inputStreamReader);
                            String message = reader.readLine();
                            System.out.println("收到客户端消息->" + reader.readLine());

                            mp.forEach((k, v) -> {
                                if (!Objects.equals(k, ip + socket.getPort())) {
                                    OutputStream outputStream = null;
                                    try {
                                        outputStream = v.getOutputStream();
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    PrintWriter printer = new PrintWriter(outputStream);
                                    printer.println("从客户端" + ip + socket.getPort() + "发送来的消息:" + message);
                                    printer.flush();
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
