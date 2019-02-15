package protocol;

import java.util.Arrays;

public class PacketProvider {
    private Integer[] data;
    private int dataPointer;
    private int packetSize;
    private int lastSequenceNumber;
    private int maxSequenceNumber;

    public PacketProvider(Integer[] data, Integer packetSize, Integer windowSize) {
        this.data = data;
        this.dataPointer = 0;
        this.packetSize = packetSize;
        this.lastSequenceNumber = 0;
        this.maxSequenceNumber = 2 * windowSize;
    }

    public Packet next() {
        if (dataPointer > data.length) {
            return null;
        }

        Integer[] nextData = Arrays.copyOfRange(data, dataPointer, dataPointer + packetSize - 1);
        lastSequenceNumber = (lastSequenceNumber + 1) % this.maxSequenceNumber;

        return new Packet(nextData, lastSequenceNumber);
    }

    public boolean hasNext() {
        return dataPointer < data.length;
    }
}
