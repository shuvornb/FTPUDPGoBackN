import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class FTPUDPServer {
    // Maximum Segment Size - Quantity of data from the application layer in the segment
    public static final int MSS = 4;

    // Probability of loss during packet sending
    public static final double PROBABILITY = 0.1;

    // Window size - Number of packets sent without acking
    public static final int WINDOW_SIZE = 2;

    // Time (ms) before Resending all the non-acked packets
    public static final int TIMER = 30;

    // Server PORT number
    public static final int PORT = 50000;

    // Directory of files
    public static final String DIRECTORY_NAME = "C:\\Users\\shuvo\\Desktop\\Input";

    public static void main(String[] args) {
        DatagramSocket socket;
        DatagramPacket inPacket;
        DatagramPacket outPacket;
        byte[] inBuf, outBuf;
        String msg;

        try{
            socket = new DatagramSocket(PORT);
            while (true) {
                System.out.println("Server running...");
                inBuf = new byte[100];
                inPacket = new DatagramPacket(inBuf, inBuf.length);
                socket.receive(inPacket);

                int sourcePort = inPacket.getPort();
                InetAddress sourceAddress = inPacket.getAddress();
                msg = new String(inPacket.getData(), 0, inPacket.getLength());
                System.out.println("Client: " + sourceAddress + ":" + sourcePort);

                if(msg.equals("list")) {

                    File directory = new File(DIRECTORY_NAME);
                    File[] fileList = directory.listFiles();

                    StringBuilder sb = new StringBuilder("\n");
                    int c = 0;
                    for (File value : fileList) {
                        if (value.canRead()) c++;
                    }

                    sb.append(c).append(" files found.\n\n");
                    for (File file : fileList) {
                        sb.append(file.getName()).append(" ").append(file.length()).append(" Bytes\n");
                    }

                    outBuf = (sb.toString()).getBytes();
                    outPacket = new DatagramPacket(outBuf, 0, outBuf.length, sourceAddress, sourcePort);
                    socket.send(outPacket);
                }
                else if(msg.contains("get")) {

                    // Sequence number of the last packet sent
                    int seqOfLastPacketSent = 0;

                    // Sequence number of the last acked packet
                    int seqOfLastPacketAcked = 0;

                    // Data to be sent
                    String fileName = extractFileNameFromMessage(msg);
                    byte[] dataBytes = extractFileContent(fileName);
                    System.out.println("Data size: " + dataBytes.length + " bytes");

                    // Last packet sequence number
                    int noOfPackets = (int) Math.ceil( (double) dataBytes.length / MSS);
                    System.out.println("Number of packets to send: " + noOfPackets);

                    // List of all the packets sent
                    ArrayList<RDTPacket> sent = new ArrayList<>();

                    while(true) {

                        while(seqOfLastPacketSent - seqOfLastPacketAcked < WINDOW_SIZE && seqOfLastPacketSent < noOfPackets){

                            // Array to store part of the bytes to send
                            byte[] segmentBytes;

                            // Copy segment of data bytes to array
                            segmentBytes = Arrays.copyOfRange(dataBytes, seqOfLastPacketSent*MSS, seqOfLastPacketSent*MSS + MSS);

                            // Create RDTPacket object
                            RDTPacket rdtPacketObject = new RDTPacket(seqOfLastPacketSent, segmentBytes, (seqOfLastPacketSent == noOfPackets-1) ? true : false);

                            // Serialize the RDTPacket object
                            byte[] sendData = Serializer.toBytes(rdtPacketObject);

                            // Create the packet
                            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, sourceAddress, sourcePort);
                            System.out.println("Sending packet with sequence number " + seqOfLastPacketSent +  " and size " + sendData.length + " bytes");

                            // Add packet to the sent list
                            sent.add(rdtPacketObject);

                            // Send the packet
                            socket.send(packet);

                            // Increase the last sent
                            seqOfLastPacketSent++;

                        }

                        // Byte array for the ACK sent by the receiver
                        byte[] ackBytes = new byte[40];

                        // Creating packet for the ACK
                        DatagramPacket ack = new DatagramPacket(ackBytes, ackBytes.length);

                        try{
                            // If an ACK was not received in the time specified (continues on the catch clausule)
                            socket.setSoTimeout(TIMER);

                            // Receive the packet
                            socket.receive(ack);

                            // Deserialize the RDTAck object
                            RDTAck ackObject = (RDTAck) Serializer.toObject(ack.getData());
                            System.out.println("Received ACK for " + ackObject.getPacket());

                            // If this ack is for the last packet+1, stop the sender
                            if(ackObject.getPacket() == noOfPackets){
                                break;
                            }

                            seqOfLastPacketAcked = Math.max(seqOfLastPacketAcked, ackObject.getPacket());

                        }catch(SocketTimeoutException e){

                            // then send all the sent but non-acked packets
                            for(int i = seqOfLastPacketAcked; i < seqOfLastPacketSent; i++){

                                // Serialize the RDTPacket object
                                byte[] sendData = Serializer.toBytes(sent.get(i));

                                // Create the packet
                                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, sourceAddress, sourcePort );
                                socket.send(packet);
                                System.out.println("Resending packet with sequence number " + sent.get(i).getSeq() +  " and size " + sendData.length + " bytes");
                            }
                        }

                    }

                    System.out.println("Finished transmission");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractFileNameFromMessage(String msg) {
        return msg.split(" ")[1];
    }

    public static byte[] extractFileContent(String fileName) throws IOException {
        File directory = new File(DIRECTORY_NAME);
        File[] fileList = directory.listFiles();

        int index = -1;
        int i = 0;
        while (i< Objects.requireNonNull(fileList).length) {
            if(fileList[i].getName().equalsIgnoreCase(fileName)) {
                index = i;
                break;
            }
            i++;
        }

        if(index == -1) return null;
        else {
            File file = new File(fileList[index].getAbsolutePath());
            FileReader fr = new FileReader(file);
            BufferedReader brf = new BufferedReader(fr);
            String s;
            StringBuilder sb = new StringBuilder();

            while((s = brf.readLine()) != null) {
                sb.append(s);
            }

            if(brf.readLine() == null) {
                System.out.println("File Read Successful. Closing Socket");
            }
            return sb.toString().getBytes();
        }
    }
}
