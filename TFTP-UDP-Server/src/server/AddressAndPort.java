package server;

import java.net.InetAddress;
import java.util.Objects;

/**
 * Used to implement the hash map with two keys in order to correctly identify clients
 * using their IP address and port number.
 * 
 * @author 198735
 */
public class AddressAndPort {

    private final InetAddress x;
    private final int y;

    public AddressAndPort(InetAddress x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * returns whether or not an object is equal to this.
     * @param o
     * @return true/false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        else if (!(o instanceof AddressAndPort)) return false;
        else{
            AddressAndPort addressAndPort = (AddressAndPort) o;
            return x.equals(addressAndPort.x) && y == addressAndPort.y;
        }
    }

    /**
     * Hashes the two parameters together in order to use for the HashMap
     * @return int
     */
    @Override
    public int hashCode() {
        return Objects.hash(x,y);
    }
    
    /**
     * Returns the values of the parameters together to make readable
     * @return string
     */
    @Override
    public String toString(){
        return x.getHostAddress() + " " + y;
    }

}
