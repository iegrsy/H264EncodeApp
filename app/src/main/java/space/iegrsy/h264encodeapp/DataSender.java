package space.iegrsy.h264encodeapp;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import vms.Nvr;
import vms.NvrServiceGrpc;

public class DataSender {
    public static class DataSenderGRPC {
        private static final String TAG = DataSenderGRPC.class.getSimpleName();

        private final Object locker = new Object();

        private String mHost = "";
        private int mPort = -1;

        private boolean isReady = false;
        private ManagedChannel channel;
        private StreamObserver<Nvr.StreamFrame> streamObserver;
        private ArrayList<byte[]> framesBuffer = new ArrayList<>();

        private Thread thread;

        public DataSenderGRPC init(String host, int port) {
            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            NvrServiceGrpc.NvrServiceStub stub = NvrServiceGrpc.newStub(channel);
            streamObserver = stub.pushCameraStream(dummyStreamObserver);

            mHost = host;
            mPort = port;
            isReady = true;
            Log.v(TAG, "Crated GRPC sender => " + mHost + ":" + mPort);

            thread = new Thread(runnableSender);
            thread.start();

            return this;
        }

        public void release() {
            isReady = false;
        }

        private Runnable runnableSender = new Runnable() {
            @Override
            public void run() {
                while (isReady) {
                    while (framesBuffer.size() <= 0 && isReady) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            isReady = false;
                        }
                    }

                    if (isReady) {
                        synchronized (locker) {
                            streamObserver.onNext(
                                    Nvr.StreamFrame.newBuilder().addData(
                                            Nvr.StreamBuffer.newBuilder()
                                                    .setData(ByteString.copyFrom(framesBuffer.get(0)))
                                                    .setTs(System.currentTimeMillis())
                                                    .build()
                                    ).build()
                            );
                            framesBuffer.remove(0);
                        }
                    }
                }
            }
        };

        public void send(final byte[] data) {
            if (!isReady || data == null || data.length <= 0)
                return;
            synchronized (locker) {
                framesBuffer.add(data);
            }
        }

        private StreamObserver<Nvr.Dummy> dummyStreamObserver = new StreamObserver<Nvr.Dummy>() {
            @Override
            public void onNext(Nvr.Dummy value) {
                Log.i(TAG, "onNext: " + value.getVal());
            }

            @Override
            public void onError(Throwable t) {
                Status status = Status.fromThrowable(t);
                Log.e(TAG, "onError: " + status.toString());
                isReady = false;
            }

            @Override
            public void onCompleted() {
                Log.w(TAG, "onCompleted");
                isReady = false;
            }
        };
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
