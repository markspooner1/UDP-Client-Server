package org.sample.httpfs;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

public class Router {
    private static final AtomicInteger queueSize = new AtomicInteger(0);
    private static final int MIN_LEN = 11;
    private static final int MAX_LEN = 1024;

    private static Logger logger;
    private static double dropRate;
    private static long maxDelay;
    private static long seed;
    private static int port;

    static class Packet {
        byte type;
        int seqNum;
        InetSocketAddress toAddr;
        InetSocketAddress fromAddr;
        byte[] payload;

        byte[] toBytes() {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            try {
                dos.writeByte(type);
                dos.writeInt(seqNum);
                dos.write(fromAddr.getAddress().getAddress());
                dos.writeShort(fromAddr.getPort());
                dos.write(payload);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return bos.toByteArray();
        }

        @Override
        public String toString() {
            return String.format("#%d, %s -> %s, sz=%d", seqNum, fromAddr, toAddr, payload.toString().length());
        }
    }

    private static Packet parsePacket(InetSocketAddress fromAddr, byte[] data) throws IOException {
    
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

        Packet p = new Packet();
      
        p.type = buffer.get();
        System.out.println("type " + p.type);
        p.seqNum = buffer.getInt();

        byte[] ipBytes = new byte[4];
        buffer.get(ipBytes);
        int port = buffer.getShort() & 0xFFFF;

        InetAddress toIp = InetAddress.getByAddress(ipBytes);
        if (toIp.isLoopbackAddress()) {
            toIp = fromAddr.getAddress();
        }
        p.toAddr = new InetSocketAddress(toIp, port);
        p.fromAddr = fromAddr;

        p.payload = new byte[buffer.remaining()];
        buffer.get(p.payload);

        return p;
    }

    private static void send(DatagramSocket socket, Packet p) {
        queueSize.decrementAndGet();

        try {
            DatagramPacket packet = new DatagramPacket(p.toBytes(), p.toBytes().length, p.toAddr);
            socket.send(packet);
            logger.info(String.format("[queue=%d] packet %s is delivered", queueSize.get(), p));
			if (p.type == 3 && UDPServer.fin) {
				socket.close();
			}
        } catch (IOException e) {
            logger.warning(String.format("failed to deliver %s: %s", p, e));
        }
    }

    private static void process(DatagramSocket socket, Packet p, Random rand) {
        if (rand.nextDouble() < dropRate) {
            logger.info(String.format("[queue=%d] packet %s is dropped", queueSize.get(), p));
            return;
        }

        queueSize.incrementAndGet();
        if (maxDelay <= 0) {
            send(socket, p);
        } else {
            long delay = rand.nextInt(100) * maxDelay / 100;
            logger.info(String.format("[queue=%d] packet %s is delayed for %d ms", queueSize.get(), p, delay));

            new Thread(() -> {
                try {
                    Thread.sleep(delay);
                    send(socket, p);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    private static void initLogger(JavaFXApp App) {
    	try {
            Handler uiLogHandler = new UILogHandler(App); 
            logger = Logger.getLogger("Router");
            logger.addHandler(uiLogHandler);
            logger.setUseParentHandlers(false);  
        } catch (Exception e) {
            System.err.println("Failed to initialize logger: " + e);
            System.exit(1);
        }
    }

    public static void main(String[] args, JavaFXApp app) {
        dropRate = Double.parseDouble(args[0]);
        maxDelay = Long.parseLong(args[1]);
        seed = System.currentTimeMillis();
        port = Integer.parseInt(args[2]);

        initLogger(app);

    
        Random rand = new Random(seed);
        logger.info(String.format("config: drop-rate=%.2f, max-delay=%d ms, seed=%d", dropRate, maxDelay, seed));

        try (DatagramSocket socket = new DatagramSocket(port)) {
            logger.info("router is listening at port " + port);

            while (true) {
                byte[] buffer = new byte[2048];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetSocketAddress fromAddr = new InetSocketAddress(packet.getAddress(), packet.getPort());
                Packet p = parsePacket(fromAddr, packet.getData());

                process(socket, p, rand);
            }
        } catch (IOException e) {
            logger.severe("Failed to run router: " + e);
        }
    }

    private static long parseDuration(String duration) {
        if (duration.endsWith("ms")) {
            return Long.parseLong(duration.replace("ms", ""));
        } else if (duration.endsWith("s")) {
            return Long.parseLong(duration.replace("s", "")) * 1000;
        } else if (duration.endsWith("m")) {
            return Long.parseLong(duration.replace("m", "")) * 60000;
        } else {
            throw new IllegalArgumentException("Invalid duration format: " + duration);
        }
    }
}
