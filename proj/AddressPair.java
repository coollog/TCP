// TODO: Have this be a function in TCPSockServer.

public class AddressPair {
    public static String toString(int addr, int port) {
        return addr + ":" + port;
    }

    public static String toString(int srcAddr, int srcPort, int destPort) {
        return toString(srcAddr, srcPort) + "->" + destPort;
    }
}