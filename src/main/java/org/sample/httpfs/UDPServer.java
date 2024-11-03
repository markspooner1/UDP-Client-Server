package org.sample.httpfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;

public class UDPServer {

    private static final int MAX_LEN = Packet.MAX_LEN;
    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    private static final String FIN = "FIN";
    private static final String SYN_ACK_PAYLOAD = "SYN-ACK";
    private static final String ACK_PAYLOAD = "ACK";
    private HashMap<Long, Packet> packets = new HashMap<>();
    private HashMap<Long, Packet> packetsToBeSent = new HashMap<>();
    private ByteBuffer buffer =   ByteBuffer.allocate(MAX_LEN).order(BYTE_ORDER);
    private Packet lastSentPacket = null;
    private httpfs fileServer;
    private int port;
    protected static boolean fin = false;
	public UDPServer(httpfs fileServer, int port) {
		this.fileServer = fileServer;
		this.port = port;
		
	}
    
    public void serve() {
        try (DatagramChannel channel = DatagramChannel.open()) {
            InetSocketAddress serverAddress = new InetSocketAddress("localhost", port);
            channel.bind(serverAddress);
           
            listenForPackets(channel, buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenForPackets(DatagramChannel channel, ByteBuffer buffer) throws IOException {
        while (true) {
            buffer.clear();
            SocketAddress router = channel.receive(buffer);
            if (router == null) continue;

            buffer.flip();
            Packet packet = Packet.fromBuffer(buffer);

            if (packets.containsKey(packet.getSequenceNumber())) {
                System.out.println("Already received packet, ignoring packet: " + packet.getSequenceNumber());
                continue;
            }

            InetSocketAddress clientAddress = new InetSocketAddress(packet.getPeerAddress(), packet.getPeerPort());
            handlePacket(channel, router, packet, clientAddress);
        }
    }

    private void handlePacket(DatagramChannel channel, SocketAddress router, Packet packet, InetSocketAddress client) throws IOException {
    	switch (packet.getType()) {
            case SYN:
                handleSynPacket(channel, router, packet);
                break;
            case ACK:
                handleAckPacket(packet);
                break;
            case DATA:
                handleDataPacket(channel, router, packet, client);
                break;
            case FIN:
                handleFinPacket(channel, router, packet, client);
                break;
            default:
                System.out.println("Invalid packet type");
        }
    }

    private void handleSynPacket(DatagramChannel channel, SocketAddress router, Packet packet) throws IOException {
        packets.put(packet.getSequenceNumber(), packet);

        Packet response = new Packet.Builder()
            .setType(Packet.PacketType.SYNACK)
            .setSequenceNumber(packet.getSequenceNumber() + 1)
            .setPeerAddress(packet.getPeerAddress())
            .setPortNumber(packet.getPeerPort())
            .setPayload(SYN_ACK_PAYLOAD.getBytes())
            .create();
        
        sendPacketWithRetry(channel, router, response);
        
    }

    private void handleAckPacket(Packet packet) {
    }

    private void handleDataPacket(DatagramChannel channel, SocketAddress router, Packet packet, InetSocketAddress client) throws IOException {
        packets.put(packet.getSequenceNumber(), packet);
        
        Packet ackPacket = new Packet.Builder()
            .setType(Packet.PacketType.ACK)
            .setSequenceNumber(packet.getSequenceNumber() + 1)
            .setPeerAddress(packet.getPeerAddress())
            .setPortNumber(packet.getPeerPort())
            .setPayload(ACK_PAYLOAD.getBytes())
            .create();

        sendPacketWithRetry(channel, router, ackPacket);
    }

    private void handleFinPacket(DatagramChannel channel, SocketAddress router, Packet packet, InetSocketAddress client) throws IOException {
    	System.out.println("Received FIN packet");
    	packets.put(packet.getSequenceNumber(), packet);
        
        String request = formRequest(packets);
        byte[] message = fileServer.run(request).getBytes();

        packetsToBeSent = Packet.splitMessage(message, router, client, packet.getSequenceNumber() + 1);
        sendPackets(channel, router, packetsToBeSent);

        packetsToBeSent.clear();
        packets.clear();
    }

    private void sendPacketWithRetry(DatagramChannel channel, SocketAddress router, Packet packet) throws IOException {
        int retryCount = 0;
        final int maxRetries = 9999999;
        Packet response = null;

        while ((retryCount < maxRetries) && (response == null || response.getSequenceNumber() != packet.getSequenceNumber() + 1)) {
        	if(packet.getType() == Packet.PacketType.FIN) {
        		fin = true;
        	}
            packet.sendPacket(channel, router);
            response = Packet.waitForResponse(channel);
            System.out.println(packet.getSequenceNumber() + " "  + Collections.max(packets.keySet()));
            if ((response != null && response.getSequenceNumber() == packet.getSequenceNumber() + 1)) {
                break;
            }

            retryCount++;
            System.out.println("Retrying... Attempt " + retryCount);
        }

        if (retryCount == maxRetries) {
            System.out.println("Max retries reached. Giving up on packet " + packet.getSequenceNumber());
        } else if (response != null) {
            handlePacket(channel, router, response, new InetSocketAddress(response.getPeerAddress(), response.getPeerPort()));
        }
    }


    private void sendPackets(DatagramChannel channel, SocketAddress router, HashMap<Long, Packet> packetsToBeSent) throws IOException {
        for (Entry<Long, Packet> entry : packetsToBeSent.entrySet()) {
            Packet dataPacket = entry.getValue();
            sendPacketWithRetry(channel, router, dataPacket);
           
        }
    }
  
    private String formRequest(HashMap<Long, Packet> packets) {
        StringBuilder responseBuilder = new StringBuilder();
        for (Entry<Long, Packet> entry : packets.entrySet()) {
            if (entry.getValue().getType() == Packet.PacketType.FIN) continue;
            responseBuilder.append(new String(entry.getValue().getPayload(), UTF_8));
        }
        return responseBuilder.toString();
    }

    
}
