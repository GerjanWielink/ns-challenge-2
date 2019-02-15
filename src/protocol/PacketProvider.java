package protocol;

import java.util.Arrays;

public class PacketProvider {
    private Integer[] data;
    private int dataPointer;
    private int packetSize;
    private int lastSequenceNumber;
    private int maxSequenceNumber;
    private boolean closingPacketSent = false;

    public PacketProvider(Integer[] data, Integer packetSize, Integer windowSize) {
        this.data = data;
        this.dataPointer = 0;
        this.packetSize = packetSize;
        this.lastSequenceNumber = -1;
        this.maxSequenceNumber = 2 * windowSize + 3;
    }

    public Packet next() {
        if (dataPointer > data.length && !this.closingPacketSent) {
            return closingPacket();
        }

        Integer[] nextData = Arrays.copyOfRange(data, dataPointer, Math.min(dataPointer + packetSize - 1, data.length));
        lastSequenceNumber = (lastSequenceNumber + 1) % this.maxSequenceNumber;
        dataPointer += packetSize - 1;

        return new Packet(nextData, lastSequenceNumber);
    }

    public Packet closingPacket() {
        lastSequenceNumber = (lastSequenceNumber + 1) % this.maxSequenceNumber;
        this.closingPacketSent = true;
        return new Packet(new Integer[]{}, lastSequenceNumber);
    }



    public boolean hasNext() {
        return dataPointer < data.length || !closingPacketSent;
    }
}
