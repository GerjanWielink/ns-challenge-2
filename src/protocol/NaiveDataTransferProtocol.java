package protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import client.*;

public class NaiveDataTransferProtocol extends IRDTProtocol {

    // change the following as you wish:
    private static final int HEADER_SIZE = 1;   // number of header bytes in each packet
    private static final int PACKET_SIZE = 128;   // max. number of user data bytes in each packet
    private static final int WINDOW_SIZE = 10;
    private static final int TIMEOUT_DURATION = 10000;
    private List<Packet> sendingWindow;
    private PacketProvider packetProvider;


    @Override
    public void sender() {
        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());

        int numPackets = fileContents.length / PACKET_SIZE;

        // keep track of where we are in the data
        int filePointer = 0;

        this.packetProvider = new PacketProvider(fileContents, PACKET_SIZE, WINDOW_SIZE);
        this.sendingWindow = new ArrayList<>();

        updateSendingWindow();

        // and loop and sleep; you may use this loop to check for incoming acks...
        boolean stop = false;
        while (!stop) {
            try {
                Integer[] packet = getNetworkLayer().receivePacket();
                if (packet == null) {
                    Thread.sleep(10);
                    continue;
                }
                Packet acknowledgedPacket = getPacketBySequenceNumber(packet[0]);

                if (acknowledgedPacket != null) {
                    acknowledgedPacket.setStatus(Packet.STATUS.ACKNOWLEDGED);
                }

            } catch (InterruptedException e) {
                stop = true;
            }
        }
    }

    private Packet getPacketBySequenceNumber (int sequenceNumber) {
        return this.sendingWindow
                .stream()
                .filter(packet -> packet.getSequenceNumber() == sequenceNumber)
                .findAny()
                .orElse(null);
    }

    private void updateSendingWindow() {
        sendingWindow.sort(Comparator.comparingInt(Packet::getSequenceNumber));

        if (sendingWindow.size() > 0) {
            while (sendingWindow.get(0).getStatus() == Packet.STATUS.ACKNOWLEDGED) {
                sendingWindow.remove(0);
            }
        }

        while (sendingWindow.size() < WINDOW_SIZE) {
            if (!packetProvider.hasNext()) {
                // TODO: stop!
                break;
            }

            Packet nextPacket = this.packetProvider.next();
            getNetworkLayer().sendPacket(nextPacket.getPacket());
            System.out.println("Sent one packet with header=" + nextPacket.getSequenceNumber()); // TODO: getHeaders
            client.Utils.Timeout.SetTimeout(TIMEOUT_DURATION, this, nextPacket.getSequenceNumber());
            sendingWindow.add(nextPacket);
        }
    }

    private void sendPacket(Packet packet) {
        getNetworkLayer().sendPacket(packet.getPacket());
    }

    @Override
    public void TimeoutElapsed(Object tag) {
        int sequenceNumber = (Integer)tag;

        Packet elapsedPacket = getPacketBySequenceNumber(sequenceNumber);
        if (elapsedPacket != null && elapsedPacket.getStatus() != Packet.STATUS.ACKNOWLEDGED) {
            sendPacket(elapsedPacket);
            elapsedPacket.updateTimeSent();
            client.Utils.Timeout.SetTimeout(TIMEOUT_DURATION, this, elapsedPacket.getSequenceNumber());
        }
        // handle expiration of the timeout:
        System.out.println("Timer expired with sequenceNumber=" + sequenceNumber);
    }

    @Override
    public void receiver() {
        System.out.println("Receiving...");

        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most efficient)
        //   is to reallocate the array every time we find out there's more data
        Integer[] fileContents = new Integer[0];

        // loop until we are done receiving the file
        boolean stop = false;
        while (!stop) {

            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();

            // if we indeed received a packet
            if (packet != null) {

                // tell the user
                System.out.println("Received packet, length=" + packet.length + "  first byte=" + packet[0] );



                // append the packet's data part (excluding the header) to the fileContents array, first making it larger
                int oldLength = fileContents.length;
                int dataLength = packet.length - HEADER_SIZE;
                fileContents = Arrays.copyOf(fileContents, oldLength + dataLength);
                System.arraycopy(packet, HEADER_SIZE, fileContents, oldLength, dataLength);

                // and let's just hope the file is now complete
                stop = true;

            } else {
                // wait ~10ms (or however long the OS makes us wait) before trying again
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
        }

        // write to the output file
        Utils.setFileContents(fileContents, getFileID());
    }

    private void acknowledgePacket(int[] packet) {
        getNetworkLayer().sendPacket(new Integer[] {packet[0]});
    }
}
