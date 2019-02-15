package protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileBuilder {
    private int nextSequenceNumber;
    private List<Integer> file;
    private int maxSequenceNumber;
    private int windowSize;

    public FileBuilder(int windowSize) {
        this.nextSequenceNumber = 0;
        this.file = new ArrayList<>();
        this.maxSequenceNumber = windowSize * 2 + 3;
        this.windowSize = windowSize;
    }

    public void appendFrame(Packet nextPacket) {
        file.addAll(Arrays.asList(nextPacket.getData()));
        nextSequenceNumber = (nextSequenceNumber + 1) % maxSequenceNumber;
    }

    public int largestAcceptable () {
        return (nextSequenceNumber + windowSize) % this.maxSequenceNumber;
    }

    public int lastReceived () {
        return nextSequenceNumber - 1;
    }

    public int getNextSequenceNumber() {
        return nextSequenceNumber;
    }

    public Integer[] getFile() {
        Integer[] fileArray = new Integer[this.file.size()];
        fileArray = this.file.toArray(fileArray);
        return fileArray;
    }


}
