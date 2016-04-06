/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */
public class TCPManager {
    private Node node;
    private int addr;
    private Manager manager;
    private SocketManager sockMan = new SocketManager();

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
    }

    /**
     * Start this TCP manager
     */
    public void start() {}

    /** SOCKET API **/

    /**
     * Create a socket
     *
     * @return TCPSock the newly created socket, which is not yet bound to
     *                 a local port
     */
    public TCPSock socket() {
        return new TCPSock(this, node);
    }

    /** END SOCKET API **/

    public void log(String text) { node.logOutput(text); }
    public void logError(String text) { node.logError(text); }

    public void send(int srcPort,
                     int destAddr,
                     int destPort,
                     int type,
                     int window,
                     int seqNum,
                     byte[] payload) {
        Transport transport =
            new Transport(srcPort, destPort, type, window, seqNum, payload);
        node.sendSegment(node.getAddr(),
                         destAddr,
                         Protocol.TRANSPORT_PKT,
                         transport.pack());
    }

    public void receive(int srcAddr,
                        int srcPort,
                        int destAddr,
                        int destPort,
                        Transport transport) {
        if (destAddr != addr) return;

        String address =
            SocketManager.AddressPair.toString(srcAddr, srcPort, destPort);
        node.logOutput("TCP Packet: " + address + " sent " + transport.getType());

        TCPSock sock = sockMan.find(srcAddr, srcPort, destPort);
        if (sock == null) {
            node.logError("Receive: Could not find socket for " + address);
            return;
        }

        sock.receive(srcAddr, srcPort, transport);
    }

    // Assign the port to the TCPSock.
    public boolean bind(TCPSock sock, int port) {
        return sockMan.assign(port, sock);
    }
    public boolean bind(int srcAddr, int srcPort, int destPort, TCPSock sock) {
        return sockMan.assign(srcAddr, srcPort, destPort, sock);
    }

    public boolean unbind(int destPort) {
        return sockMan.unassign(destPort);
    }
    public boolean unbind(int srcAddr, int srcPort, int destPort) {
        return sockMan.unassign(srcAddr, srcPort, destPort);
    }

    public void addTimer(long deltaT, Callback callback) {
        node.getManager().addTimer(addr, deltaT, callback);
    }
}
