package space.iegrsy.h264encodeapp;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class DataSender {
    private final String mHost;
    private final int mPort;

    public DataSender(@NonNull String mHost, int mPort) {
        this.mHost = mHost;
        this.mPort = mPort;
    }

    public void send(@NonNull byte[] bytes) {

    }

    public static class DataSenderUDP {
        private final String host;
        private final int port;

        private DatagramSocket udpSocket = null;
        private DatagramPacket packet = null;

        public DataSenderUDP(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public void sendDataUDP(@NonNull final byte[] data) {
            if (data.length <= 0)
                return;

            if (udpSocket == null) {
                try {
                    udpSocket = new DatagramSocket();
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        packet = new DatagramPacket(data, data.length, InetAddress.getByName(host), port);
                        udpSocket.send(packet);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }
}
