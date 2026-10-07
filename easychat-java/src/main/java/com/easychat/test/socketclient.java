    package com.easychat.test;

    import java.io.*;
    import java.net.Socket;
    import java.util.Scanner;

    public class socketclient {
        public static void main(String[] args) {
            Socket socket = null;
            try {
                socket = new Socket("127.0.0.1", 1024);

                OutputStream outputStream = socket.getOutputStream();
                PrintWriter printer = new PrintWriter(outputStream);
                System.out.println("输入文本");

                new Thread(() -> {
                    while (true) {
                        Scanner sc = new Scanner(System.in);
                        String text = sc.nextLine();
                        printer.println(text);
                        printer.flush();
                    }
                }).start();

                InputStream inputstream = socket.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputstream);
                BufferedReader reader = new BufferedReader(inputStreamReader);

                new Thread(() -> {
                    while (true) {
                        try {
                            String s = reader.readLine();
                            System.out.println("收到ACK : " + s);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
