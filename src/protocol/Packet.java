package protocol;

import java.util.Arrays;

public class Packet {
    public enum STATUS {
        SENT,
        ACKNOWLEDGED
    }

    private STATUS status;
    private int sequenceNumber;
    private Integer[] data;
    private long timeSent;

    public Packet(Integer[] data, int sequenceNumber) {
        this.data = data;
        this.sequenceNumber = sequenceNumber;
        this.timeSent = System.currentTimeMillis();
        this.status = STATUS.SENT;
    }


    public STATUS getStatus() {
        return status;
    }

    public void setStatus(STATUS status) {
        this.status = status;
    }

    public void updateTimeSent() {
        this.timeSent = System.currentTimeMillis();
    }

    public long getTimeSent() {
        return this.timeSent;
    }

    public int getSequenceNumber (){
        return this.sequenceNumber;
    }

    public Integer[] getPacket() {
        Integer[] packet = Arrays.copyOf((new Integer[] {sequenceNumber}), this.data.length + 1);

        System.arraycopy(this.data, 0, packet, 1, this.data.length);

        return packet;
    }
}
