package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;

/**
 * The server for the TFTP protocol
 * @author 198735
 */
public class TFTPUDPSocketServer extends Thread {

    protected DatagramSocket socket = null;
    // ip address, followed by port number and finally state
    protected HashMap<AddressAndPort, Integer> state;
    // ip address, followed by port number and finally file its accessing
    protected HashMap<AddressAndPort, String> filename;
    protected int svrport;

    public TFTPUDPSocketServer() throws SocketException {
        this("TFTPUDPSocketServer");
    }

    public TFTPUDPSocketServer(String name) throws SocketException {
        super(name);
        this.svrport = 9000;
        socket = new DatagramSocket(svrport);
        state = new HashMap<>();
        filename = new HashMap<>();

    }

    /**
     * Sends and receives all packets that come through port 9000, works out
     * their opcode and runs the appropriate code to send back to the clients
     */
    @Override
    public void run() {

        try {
            // run forever
            while (true) {

                DatagramPacket packet = new DatagramPacket(new byte[516], 516);
                socket.receive(packet);
                InetAddress addr = packet.getAddress();
                int srcPort = packet.getPort();
                //converts the packet from bytes back to String
                String packetData = new String(returnCorrectLengthByteArray(packet.getData()));
                System.out.println("received: "+ packetData);

                //  get data and port work out what opcode its has
                // read/write start of connections
                if(packetData.charAt(1) == "1".charAt(0) || packetData.charAt(1) == "2".charAt(0)){                
                    String currentFile = "Files/" + packetData.substring(2, packetData.length()-7);
                    System.out.println("Currentfile: " + currentFile);
                    if(Files.exists(Paths.get(currentFile))){
                        if(packetData.charAt(1) == "2".charAt(0)){
                            //send data back
                            state.put(new AddressAndPort(addr, srcPort), 1);
                            filename.put(new AddressAndPort(addr, srcPort), currentFile);
                            String data  = "03" + "01" + new String(readFromFile(currentFile,0));
                            System.out.println("Sending:" + data);
                            byte[] buf = data.getBytes();
                            packet = new DatagramPacket(buf, buf.length, addr, srcPort);
                            // send back ACK or Data or Error
                            socket.send(packet);
                        }
                        else{
                            //send ack back
                            state.put(new AddressAndPort(addr, srcPort), 1);
                            filename.put(new AddressAndPort(addr, srcPort), currentFile);
                            String data = "04" + "00";
                            System.out.println("Sending:" + data);
                            byte[] buf = data.getBytes();
                            packet = new DatagramPacket(buf, buf.length, addr, srcPort);
                            // send back ACK or Data or Error
                            socket.send(packet);
                        }
                    }
                    else{
                        if(packetData.charAt(1) == "2".charAt(0)){
                            // send back error
                            String data = "05" + "01" + "File: " + currentFile + " not found" + "0";
                            System.out.println("Sending:" + data);
                            byte[] buf = data.getBytes();
                            packet = new DatagramPacket(buf, buf.length, addr, srcPort);
                            // send back ACK or Data or Error
                            socket.send(packet);
                        }
                        else{
                            //create file
                            Files.createFile(Paths.get(currentFile));
                            System.out.println("creating file: " + currentFile);
                            //send ack back
                            state.put(new AddressAndPort(addr, srcPort), 1);
                            filename.put(new AddressAndPort(addr, srcPort), currentFile);
                            String data = "04" + "00";
                            System.out.println("Sending:" + data);
                            byte[] buf = data.getBytes();
                            packet = new DatagramPacket(buf, buf.length, addr, srcPort);
                            // send back ACK or Data or Error
                            socket.send(packet);
                        }
                    }                    
                }
                // data - writing to file
                else if(packetData.charAt(1) == "3".charAt(0)){
                    // if data make sure its correct seq # 
                    // else ignore
                    // if correct write to file
                    int currentN = state.get(new AddressAndPort(addr, srcPort));
                    int packetInt = Integer.parseInt(packetData.substring(2, 4));
                    if (packetInt == currentN){
                        writeToFile(filename.get(new AddressAndPort(addr, srcPort)), packetData.substring(3));
                        state.put(new AddressAndPort(addr, srcPort), currentN+1);
                        String data = "04" + returnCorrectN(currentN);
                        System.out.println("Sending:" + data);
                        byte[] buf = data.getBytes();
                        packet = new DatagramPacket(buf, buf.length, addr, srcPort);
                        // send back ACK or Data or Error
                        socket.send(packet);
                    }                                      
                } 
                //ACK - reading from file
                else if(packetData.charAt(1) == "4".charAt(0)){                
                    // if ack send the next packet if for that seq # 
                    // else ignore
                    // if correct read 512 bytes from file
                    int currentN = state.get(new AddressAndPort(addr, srcPort));
                    if(Integer.parseInt(new String(packet.getData()).substring(2, 4)) == currentN){
                        state.put(new AddressAndPort(addr, srcPort), currentN+1);
                        String data = "03" + returnCorrectN(currentN+1) + new String(readFromFile(filename.get(new AddressAndPort(addr, srcPort)), currentN));
                        System.out.println("Sending:" + data);
                        byte[] buf = data.getBytes();
                        packet = new DatagramPacket(buf, buf.length, addr, srcPort);
                        // send back ACK or Data or Error
                        socket.send(packet);
                    }
                }
                else{
                    //error - AHH! - should never get here!
                    System.out.println(packetData);
                    System.err.println("Error packet not correct opcode");
                    System.exit(0);
                }
                System.out.println("After sent pkt the HashMaps look like: ");
                System.out.println(state.toString());
                System.out.println(filename.toString());
                System.out.println(" ");
            }
        } catch (IOException e) {
            System.err.println(e);
        }
        socket.close();
    }
    
    /**
     * Reads the file at the filename and returns the correct sequence of bytes
     * dependant on how big the currentN is.
     * @param filename
     * @param currentN
     * @return byte array of read bytes
     * @throws IOException 
     */
    private byte[] readFromFile(String filename, int currentN) throws IOException{
        byte[] read = Files.readAllBytes(Paths.get(filename));
        if(read.length <= currentN * 512){
            return new byte[0];
        }
        else if(read.length < (currentN * 512) + 512){
            return Arrays.copyOfRange(read, currentN * 512, read.length);
        }
        else{
            return Arrays.copyOfRange(read, currentN * 512, (currentN * 512) + 512);
        }
    }
    
    /**
     * Writes all data given in the parameter to the filename given with the option
     * Append, so that it is added to the file and doesn't remove the current contents
     * @param filename
     * @param data
     * @throws IOException 
     */
    private void writeToFile(String filename, String data) throws IOException{
        Files.write(Paths.get(filename), data.getBytes(), StandardOpenOption.APPEND);
    }
    
    /**
     * Ensures the returned string has two characters i.e. 1 is "01",
     * as the packets need two bytes for block number.
     * @param n
     * @return string for inputted n
     */
    private String returnCorrectN(int n){
        NumberFormat format = new DecimalFormat("00");
        return format.format(n);
    }        
    
    /**
     * Works out the correct length of the incoming byte array as the received
     * Datagram packet needs to be set to 516 bytes even if it only has 4 incoming
     * @param buf
     * @return 
     */
    private byte[] returnCorrectLengthByteArray(byte[] buf){
        if(buf[buf.length-1] == 0){
            for(int i = 0; i < buf.length; i++){
                if(buf[i] == 0){
                    return Arrays.copyOfRange(buf, 0, i);
                }
            }  
        }
        return buf;
    }
    
    public static void main(String[] args) throws IOException {
        TFTPUDPSocketServer server = new TFTPUDPSocketServer();
        server.start();
        System.out.println("TFTP Server Started");
        System.out.println("Server port: " + server.svrport);
    }

}
