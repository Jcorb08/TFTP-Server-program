package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Scanner;

/**
 * The client for the TFTP protocol
 * @author 198735
 */
public class TFTPUDPSocketClient {
    
    DatagramSocket socket;
    DatagramPacket packet;
    final InetAddress address;
    int clientport;
    final int svrport;
    final int dataLen;
    final int timeoutTime;
    
    
    public TFTPUDPSocketClient(InetAddress address) {
        this.address = address;
        this.dataLen = 516;
        this.svrport = 9000;
        this.clientport = 0;
        this.socket = null;
        this.timeoutTime = 40;
        this.packet = null;
        
    }
    
    /**
     * The main running method of the client, handles the transmission of each file,
     * and directs to the appropriate method for reading writing starting etc.
     * Repeats until the user wants to stop sending/receiving files.
     */
    public void run(){
        
        boolean exit = false;        
        while(!exit){
            Scanner input;
            try{
                // binds the port to any available on the local host machine
                // so even if two clients both have different port numbers
                int request = 0;
                boolean intInput = false;
                socket = new DatagramSocket();
                socket.setSoTimeout(timeoutTime);
                clientport = socket.getLocalPort();
                System.out.println("Using IP Address: " + address.getCanonicalHostName());
                System.out.println("Using port: " + clientport);
                String path = "";
                String filename;
                while(!intInput){
                    try{
                        System.out.print("1 to store file 2 to receive a file: ");
                        input = new Scanner(System.in);
                        request = input.nextInt();
                        if (request == 1 || request == 2){
                            System.out.println("input: " + request);                            
                            intInput = true;
                        }                               
                        else{
                            throw new Exception();
                        }
                    }
                    catch(Exception e){
                        System.out.println("Input not correct! Type 1 or 2.");
                    }
                }
                do{
                    System.out.print("Type in the filename: ");
                    input = new Scanner(System.in); 
                    filename = input.next();
                    path = "Files/" + filename;
                } while(!Files.exists(Paths.get(path)));
                
                // open connection
                startTFTP(request,filename);
                if (request == 1){
                //write to file
                // ack sent back from server
                    if(!recievePktHandler(packet)){return;}
                    write(path);
                }
                else{
                //read from file
                // data sent back instantly from server 
                    read();
                }
            }
            catch(IOException e){
                System.err.println(e);
            }
            System.out.println("File transfer finished.");
            System.out.println("To do another transfer type 'again',");
            System.out.print("to stop the client type 'exit': ");
            input = new Scanner(System.in);
            if((input.next().toLowerCase()).equals("exit")){exit = true;}
        }
        socket.close();
    }

    /**
     * Starts the first bit of the protocol with the packet being sent with 01/02,
     * opcode and the appropriate filename inputted by the user.
     * @param request
     * @param filename
     * @throws IOException 
     */
    private void startTFTP(int request, String filename) throws IOException {
        //mode always octet
        String data = ("0" + request + filename + "0" + "octet" + "0");
        byte[] buf = data.getBytes();
        packet = new DatagramPacket(buf, filename.length() + 9, address, svrport);
        System.out.println("Sending packet with the data: " + data);
        socket.send(packet);
    }

    /**
     * sends data packets with the inputted string attached and waits to receive
     * data packets before sending again, if the toWrite array is less than 512,
     * the writing stops, else it is decreased by 512 for the next packet
     * @throws IOException 
     */
    private void write(String path) throws IOException {
        byte[] toWrite = Files.readAllBytes(Paths.get(path));
        //byte[] toWrite = getWriteData().getBytes();
        System.out.print("Input String: ");
        System.out.println(new String(toWrite));
        int block = 0;
        while(true){
            // construct and send a data packet
            block += 1;
            String data = "03" + returnCorrectN(block) + new String(Arrays.copyOfRange(toWrite, 0, 512));
            byte[] buf = data.getBytes();
            System.out.println("Sending pkt of data: " + data);
            packet = new DatagramPacket(buf, data.length(), address, svrport);
            socket.send(packet);

            //decrease toWrite byte array only if its not the last packet to be sent
            // retrieve ACK Packet return if error
            if(!recievePktHandler(packet)){return;}
            if(toWrite.length > 512){
                toWrite = Arrays.copyOfRange(toWrite, 512, toWrite.length);
            }
            else{return;}
        }    
    }
    
    /**
     * keeps track of the current block and checks to make sure that the incoming,
     * data read from the server is not less than 516 as this would stop the transfer,
     * discards ACKs lower than that count.
     * @throws IOException 
     */
    private void read() throws IOException {
        boolean exit = false;
        int block;
        String file = new String();
        while(!exit){
            // retreives the packet and exits if error
            if(!recievePktHandler(packet)){return;}
            // prints out read file
            file += new String(Arrays.copyOfRange(packet.getData(), 4, packet.getData().length));
            block = Integer.parseInt("" + new String(packet.getData()).charAt(2) + new String(packet.getData()).charAt(3));
            System.out.println("Current Block #" + block);
            if(packet.getData()[dataLen-1] == 0){exit = true;}
            //sends ACK
            sendACKPacket(block);
        }
        System.out.println("File Contents: ");
        System.out.println(file);
    }
    
    /**
     * Tries to receive the incoming server sent packet 
     * it catches timeouts and repeats the sending and receiving 50 times 
     * before breaking in order to stop an infinite loop
     * @param p
     * @return true/false dependant on whether it is successful
     * @throws IOException 
     */
    private boolean recievePktHandler(DatagramPacket p) throws IOException{
        
        packet = new DatagramPacket(new byte[516], 516);
        for(int i = 0; i < 50; i++){
            try{
                socket.receive(packet);
                //check if ACK or ERROR
                if(new String(packet.getData()).charAt(1) == "5".charAt(0)){
                    System.err.println("Error: filename not found");
                    System.err.println("Packet received: " + new String(packet.getData()));
                    return false;
                }
                else{
                    System.out.print("Packet received: ");
                    System.out.println(new String(Arrays.copyOfRange(packet.getData(), 0, 4)));
                    return true;
                }

            }
            catch(SocketTimeoutException e){
                System.out.println("Timeout resending packet: " + new String(p.getData()));
                socket.send(p);
            }
        }
        return false;
    }
    
    /**
     * formats and sends the correct ACK packet to the server with the given n 
     * block number.
     * @param block
     * @throws IOException 
     */
    private void sendACKPacket(int block) throws IOException{
            // 04 here the opcode for indicating a ACK packet
            String data = "04" + returnCorrectN(block);
            System.out.println("Sending ACK packet: " + data);
            byte[] buf = data.getBytes();
            packet = new DatagramPacket(buf, 4, address, svrport);
            socket.send(packet);
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
    
    // the client will take the IP Address of the server (in dotted decimal format as an argument)
    public static void main(String[] args) throws UnknownHostException{
        InetAddress address;
        // The address must be transfomed from a String to an InetAddress (an IP addresse object in Java).
        // the address is loopback address 127.0.0.1 but could be anything
        if (args.length != 1) {
            System.out.println("the hostname of the server is required");
            System.out.println("Defaulting to 127.0.0.1");
            address = InetAddress.getByName("127.0.0.1");
        }
        else {
            address = InetAddress.getByName(args[0]);
        }
        
        (new TFTPUDPSocketClient(address)).run();
        
    }
    
}
