package org.sample.httpfs;


import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.Set;
import java.nio.channels.Selector;

import javax.xml.crypto.Data;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;

import java.net.SocketAddress;
import java.net.SocketAddress;

/**
 * Packet represents a simulated network packet.
 * As we don't have unsigned types in Java, we can achieve this by using a larger type.
 */
public class Packet {

    public static final int MIN_LEN = 11;
    public static final int MAX_LEN = 11 + 1024;
    
    private final PacketType type;
    public static enum PacketType {
        SYN, ACK, SYNACK, FIN, DATA
    }
    private final long sequenceNumber;
    private final InetAddress peerAddress;
    private final int peerPort;
    private final byte[] payload;


    public Packet(PacketType type, long sequenceNumber, InetAddress peerAddress, int peerPort, byte[] payload) {
        this.type = type;
        this.sequenceNumber = sequenceNumber;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.payload = payload;
    }

    public PacketType getType() {
        return type;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public InetAddress getPeerAddress() {
        return peerAddress;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * Creates a builder from the current packet.
     * It's used to create another packet by re-using some parts of the current packet.
     */
    public Builder toBuilder(){
        return new Builder()
                .setType(type)
                .setSequenceNumber(sequenceNumber)
                .setPeerAddress(peerAddress)
                .setPortNumber(peerPort)
                .setPayload(payload);
    }

    /**
     * Writes a raw presentation of the packet to byte buffer.
     * The order of the buffer should be set as BigEndian.
     */
    private void write(ByteBuffer buf) {
        buf.put((byte) PacketType.valueOf(type.name()).ordinal());
        buf.putInt((int) sequenceNumber);
        buf.put(peerAddress.getAddress());
        buf.putShort((short) peerPort);
        buf.put(payload);
    }

    /**
     * Create a byte buffer in BigEndian for the packet.
     * The returned buffer is flipped and ready for get operations.
     */
    public ByteBuffer toBuffer() {
        // Calculate the size needed based on actual payload length
        int totalSize = 1 + 4 + peerAddress.getAddress().length + 2 + payload.length;  // Adjusted buffer size
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);
        write(buf);  // This will write all packet attributes including the payload
        buf.flip();
        return buf;
    }

    /**
     * Returns a raw representation of the packet.
     */
    public byte[] toBytes() {
        ByteBuffer buf = toBuffer();
        byte[] raw = new byte[buf.remaining()];
        buf.get(raw);
        return raw;
    }
    

    /**
     * fromBuffer creates a packet from the given ByteBuffer in BigEndian.
     */
    public static Packet fromBuffer(ByteBuffer buf) throws IOException {
        if (buf.limit() < MIN_LEN || buf.limit() > MAX_LEN) {
           return null;
       }
        Builder builder = new Builder();

        builder.setType(PacketType.values()[Byte.toUnsignedInt(buf.get())]);
        builder.setSequenceNumber(Integer.toUnsignedLong(buf.getInt()));

        byte[] host = new byte[]{buf.get(), buf.get(), buf.get(), buf.get()};
        builder.setPeerAddress(Inet4Address.getByAddress(host));
        builder.setPortNumber(Short.toUnsignedInt(buf.getShort()));

        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        builder.setPayload(payload);

        return builder.create();
    }
    public void sendPacket(DatagramChannel channel, SocketAddress router) throws IOException{
            channel.send(this.toBuffer(), router);
    }   
    public static Packet receivePacket(DatagramChannel channel) throws IOException{
        ByteBuffer buf = ByteBuffer.allocate(MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        channel.receive(buf);
        buf.flip();
        return fromBuffer(buf);
    }
    public static Packet waitForResponse(DatagramChannel channel) throws IOException{
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        selector.select(2000);
        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()) {
            return null;
        }else{
             return receivePacket(channel);
        }
    }
    public Packet resendPacket(DatagramChannel channel, SocketAddress router) throws IOException{
        sendPacket(channel, router);
        channel.configureBlocking(false);
        Packet response = waitForResponse(channel);
        if(response == null){
            resendPacket(channel, router);
        }
        return response;
    }

    /**
     * fromBytes creates a packet from the given array of bytes.
     */
    public static Packet fromBytes(byte[] bytes) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(MAX_LEN).order(ByteOrder.BIG_ENDIAN);
        buf.put(bytes);
        buf.flip();
        return fromBuffer(buf);
    }

    public static HashMap<Long, Packet> splitMessage(byte[] data, SocketAddress routerAddr, InetSocketAddress serverAddr, long startingSequenceNumber){
		HashMap<Long, Packet> packetsToBeSent = new HashMap<Long,Packet>();
		byte [] message = data;
		int sent = 0;
        long nextSequenceNumber = startingSequenceNumber; // After syn and synack handshake
		while(sent < message.length) {
			int bytesneeded = 0;
			if((message.length - sent) > Packet.MAX_LEN) {
				bytesneeded = Packet.MAX_LEN;
			}
			else {
				bytesneeded = message.length - sent;
			}
			byte[] requestData = new byte[bytesneeded];
			for (int i = 0; i < bytesneeded; i++) {
			    requestData[i] = message[sent]; 
			    sent++;
			}
			Packet p = new Packet.Builder()
					.setType(Packet.PacketType.DATA)
					.setSequenceNumber(nextSequenceNumber)
					.setPortNumber(serverAddr.getPort())
					.setPeerAddress(serverAddr.getAddress())
					.setPayload(requestData)
					.create();
			packetsToBeSent.put(nextSequenceNumber, p);
			nextSequenceNumber = nextSequenceNumber + 2;
		}
		Packet finPacket = new Packet.Builder()
						.setType(Packet.PacketType.FIN)
						.setSequenceNumber(nextSequenceNumber)
						.setPortNumber(serverAddr.getPort())
						.setPeerAddress(serverAddr.getAddress())
						.setPayload("FIN".getBytes())
						.create();
        packetsToBeSent.put(nextSequenceNumber, finPacket);
        nextSequenceNumber = nextSequenceNumber + 1;
		return packetsToBeSent;
	}

    @Override
    public String toString() {
        return String.format("#%d peer=%s:%d, size=%d", sequenceNumber, peerAddress, peerPort, payload.length);
    }
    

    public static class Builder {
        private PacketType type;
        private long sequenceNumber;
        private InetAddress peerAddress;
        private int portNumber;
        private byte[] payload;

        public Builder setType(PacketType type) {
            this.type = type;
            return this;
        }

        public Builder setSequenceNumber(long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder setPeerAddress(InetAddress peerAddress) {
            this.peerAddress = peerAddress;
            return this;
        }

        public Builder setPortNumber(int portNumber) {
            this.portNumber = portNumber;
            return this;
        }

        public Builder setPayload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        public Packet create() {
            return new Packet(type, sequenceNumber, peerAddress, portNumber, payload);
        }
    }
}
