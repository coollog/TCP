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

    // Assign the port to the TCPSock.
    public int bind(TCPSock sock, int port) {
        return sockMan.assign(port, sock);
    }

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

    public void receive(int srcAddr,
                        int srcPort,
                        int destAddr,
                        int destPort,
                        Transport transport) {
        if (destAddr != addr) return;

        TCPSock sock = sockMan.find(srcAddr, srcPort, destPort);
        if (sock == null) return;

        sock.receive(srcAddr, srcPort, transport);
    }

    public int bind(int srcAddr, int srcPort, int destPort, TCPSock sock) {
        return sockMan.assign(srcAddr, srcPort, destPort, sock);
    }
}
