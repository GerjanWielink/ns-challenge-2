package protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import client.*;

public class NaiveDataTransferProtocol extends IRDTProtocol {

    // change the following as you wish:
    private static final int HEADER_SIZE = 1;   // number of header bytes in each packet
    private static final int PACKET_SIZE = 512;   // max. number of user data bytes in each packet
    private static final int WINDOW_SIZE = 15;
    private static final int TIMEOUT_DURATION = 1500;
    private List<Packet> sendingWindow;
    private List<Packet> receivingWindow;
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
            updateSendingWindow();
            try {
                Integer[] packet = getNetworkLayer().receivePacket();
                if (packet == null) {
                    Thread.sleep(50);
                    continue;
                }
                Packet acknowledgedPacket = getPacketBySequenceNumber(this.sendingWindow, packet[0]);

                if (acknowledgedPacket != null) {
                    System.out.println("Received acknowledgment for sequenceNumber: " + acknowledgedPacket.getSequenceNumber());
                    System.out.println("RTT" + (System.currentTimeMillis() - acknowledgedPacket.getTimeSent()));
                    acknowledgedPacket.setStatus(Packet.STATUS.ACKNOWLEDGED);
                }



            } catch (InterruptedException e) {
                stop = true;
            }
        }
    }

    private Packet getPacketBySequenceNumber (List<Packet> packetList, int sequenceNumber) {
        return packetList
                .stream()
                .filter(packet -> packet.getSequenceNumber() == sequenceNumber)
                .findAny()
                .orElse(null);
    }

    private void updateSendingWindow() {
        sendingWindow.sort(Comparator.comparingInt(Packet::getSequenceNumber));
        System.out.println("updateSendingWindow(): " + sendingWindow);


        while (sendingWindow.size() > 0 && sendingWindow.get(0).getStatus() == Packet.STATUS.ACKNOWLEDGED) {
            System.out.println("sendWindow.length: " + sendingWindow.size());
            sendingWindow.remove(0);
        }

        while (sendingWindow.size() < WINDOW_SIZE && packetProvider.hasNext()) {
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

        Packet elapsedPacket = getPacketBySequenceNumber(this.sendingWindow, sequenceNumber);
        if (elapsedPacket != null && elapsedPacket.getStatus() != Packet.STATUS.ACKNOWLEDGED) {
            sendPacket(elapsedPacket);
            elapsedPacket.updateTimeSent();
            client.Utils.Timeout.SetTimeout(TIMEOUT_DURATION, this, elapsedPacket.getSequenceNumber());
        }
        // handle expiration of the timeout:
        System.out.println("Timer expired with sequenceNumber=" + sequenceNumber);
    }

    private FileBuilder fileBuilder;

    @Override
    public void receiver() {
        System.out.println("Receiving...");

        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most efficient)
        //   is to reallocate the array every time we find out there's more data
        Integer[] fileContents = new Integer[0];


        this.receivingWindow = new ArrayList<>();
        // loop until we are done receiving the file
        boolean stop = false;

        boolean receivedClosingPackage = false;
        this.fileBuilder = new FileBuilder(WINDOW_SIZE);

        while (!stop) {

            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();

            // if we indeed received a packet
            if (packet != null) {

                // tell the user
                System.out.println("Received packet, length=" + packet.length + "  first byte=" + packet[0] );


                int dataLength = packet.length - HEADER_SIZE;
                Packet nextPacket = new Packet(Arrays.copyOfRange(packet,1,packet.length),packet[0]);
                if (receivingWindow.size() < 10 && withinReceivingFrame(nextPacket.getSequenceNumber(), fileBuilder.lastReceived(), fileBuilder.largestAcceptable())) {
                    if (getPacketBySequenceNumber(this.receivingWindow, nextPacket.getSequenceNumber()) == null) {
                        receivingWindow.add(nextPacket);
                    }
                }
                acknowledgePacket(packet);
                updateReceivingWindow();
                System.out.println("receivingWindow: " + receivingWindow);

                if (dataLength == 0) {
                    receivedClosingPackage = true;
                    System.out.println("Closing packet received");
                }
                if (receivedClosingPackage && receivingWindow.size() == 0) {
                    stop = true;
                }
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
        System.out.println(fileBuilder.getFile().length);
        Utils.setFileContents(fileBuilder.getFile(), getFileID());
    }

    private boolean withinReceivingFrame(int sequenceNumber, int lastReceived, int largestAcceptable) {
        return  (sequenceNumber > lastReceived && sequenceNumber < largestAcceptable) ||
                (largestAcceptable < lastReceived && (sequenceNumber > lastReceived || sequenceNumber < largestAcceptable));
    }

    private void acknowledgePacket(Integer[] packet) {
        System.out.println("Sent acknowledgement for sequenceNumber: " + packet[0]);
        getNetworkLayer().sendPacket(new Integer[] {packet[0]});
    }
    private void updateReceivingWindow() {
        Packet nextPacket = getPacketBySequenceNumber(this.receivingWindow, fileBuilder.getNextSequenceNumber());
        while(nextPacket != null) {
            fileBuilder.appendFrame(nextPacket);
            receivingWindow.remove(nextPacket);
            nextPacket = getPacketBySequenceNumber(this.receivingWindow, fileBuilder.getNextSequenceNumber());
        }

//        while (receivingWindow.size() > 0 && receivingWindow.get(0).getSequenceNumber() == fileBuilder.getNextSequenceNumber()) {
//            fileBuilder.appendFrame(receivingWindow.get(0));
//            acknowledgePacket(receivingWindow.get(0).getPacket());
//            receivingWindow.remove(0);
//        }
    }
}
