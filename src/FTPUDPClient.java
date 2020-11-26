import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Scanner;

public class FTPUDPClient {

    // Probability of ACK loss
    public static final double PROBABILITY = 0.1;

    public static void main(String[] args) {
        DatagramSocket socket;
        DatagramPacket inPacket;
        DatagramPacket outPacket;
        byte[] inBuf, outBuf;
        String msg;
        Scanner sc = new Scanner(System.in);

        try {

            // prompt the user for server IP and PORT
            String serverIPAddress;
            System.out.println("Enter the server IP: ");
            serverIPAddress = sc.nextLine();
            int serverPort;
            System.out.println("Enter the server PORT: ");
            serverPort = sc.nextInt();
            sc.nextLine();

            // create connection with server
            InetAddress address = InetAddress.getByName(serverIPAddress);
            socket = new DatagramSocket();
            msg = "";
            outBuf = msg.getBytes();
            outPacket = new DatagramPacket(outBuf, 0, outBuf.length, address, serverPort);
            socket.send(outPacket);
            System.out.println("Connection established with the server.\n\n");

            // Display the allowed commands for the user
            printUserMenu();

            while(true) {
                // get command from the user
                String command;
                command = sc.nextLine();

                if(command.equals("list")) {
                    msg = "list";
                    outBuf = msg.getBytes();
                    outPacket = new DatagramPacket(outBuf, 0, outBuf.length, address, serverPort);
                    socket.send(outPacket);

                    inBuf = new byte[65535];
                    inPacket = new DatagramPacket(inBuf, inBuf.length);
                    socket.receive(inPacket);

                    String data = new String(inPacket.getData(), 0, inPacket.getLength());
                    System.out.println(data);
                    // Display the allowed commands for the user
                    printUserMenu();
                }

                else if(command.contains("get")) {
                    msg = command;
                    outBuf = msg.getBytes();
                    outPacket = new DatagramPacket(outBuf, 0, outBuf.length, address, serverPort);
                    socket.send(outPacket);


                    // 83 is the base size (in bytes) of a serialized RDTPacket object
                    byte[] receivedData = new byte[FTPUDPServer.MSS + 83];

                    int waitingFor = 0;

                    ArrayList<RDTPacket> received = new ArrayList<>();

                    boolean end = false;

                    while(!end){

                        System.out.println("Waiting for packet");

                        // Receive packet
                        DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);
                        socket.receive(receivedPacket);

                        // Deserialize to a RDTPacket object
                        RDTPacket packet = (RDTPacket) Serializer.toObject(receivedPacket.getData());
                        System.out.println("Packet with sequence number " + packet.getSeq() + " received (last: " + packet.isLast() + " )");

                        if(packet.getSeq() == waitingFor && packet.isLast()){
                            waitingFor++;
                            received.add(packet);
                            System.out.println("Last packet received");
                            end = true;

                        }
                        else if(packet.getSeq() == waitingFor){
                            waitingFor++;
                            received.add(packet);
                            System.out.println("Packed stored in buffer");
                        }
                        else{
                            System.out.println("Packet discarded (not in order)");
                        }

                        // Create an RDTAck object
                        RDTAck ackObject = new RDTAck(waitingFor);

                        // Serialize
                        byte[] ackBytes = Serializer.toBytes(ackObject);

                        DatagramPacket ackPacket = new DatagramPacket(ackBytes, ackBytes.length, receivedPacket.getAddress(), receivedPacket.getPort());

                        // Send the ack
                        socket.send(ackPacket);
                        System.out.println("Sending ACK to seq " + waitingFor + " with " + ackBytes.length  + " bytes");
                    }

                    // Print the data received
                    System.out.println(" ------------ DATA ---------------- ");

                    for(RDTPacket p : received) {
                        for (byte b : p.getData()) {
                            System.out.print((char) b);
                        }
                    }
                }

                else if(command.equals("exit")) {
                    System.out.println("Terminating Client...");
                    break;
                }

                else {
                    System.out.println("Invalid command.\n\n\n");
                    // Display the allowed commands for the user
                    printUserMenu();

                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void printUserMenu() {
        System.out.println("===================================================================");
        System.out.println("To list the files available in server type    -      list");
        System.out.println("To get a file from the server type            -      get <fileName>");
        System.out.println("To exit the program type                      -      exit");
        System.out.println("===================================================================\n\n");
    }
}
