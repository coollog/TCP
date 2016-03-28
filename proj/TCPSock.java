import java.util.concurrent.*;
import java.util.*;

public class TCPSock {
    private static final byte dummy[] = new byte[0];

    // TCP socket states
    // UNBOUND - just created, must bind port now.
    // BOUND - port bound, but not connected/listening.
    // SHUTDOWN - close requested, FIN not sent (due to unsent data in queue)
    private enum State {
        UNBOUND,
        BOUND,
        CLOSED,
        LISTEN,
        SYN_SENT,
        ESTABLISHED,
        SHUTDOWN
    }

    private enum Type {
        NONE,
        SERVER_LISTENER,
        SERVER_CLIENT,
        CLIENT
    }

    private State state = State.UNBOUND;
    private Type type = Type.NONE;

    private Node node;
    private TCPManager tcpMan;

    private int myPort;

    // Server variables
    private ArrayBlockingQueue<TCPSock> serverBacklog;
    private HashSet<String> serverClientAddresses;

    // Client variables
    private int clientSeqNo;
    private int clientWindowSize;
    private int clientDestAddr;
    private int clientDestPort;

    public TCPSock(TCPManager tcpMan, Node node) {
        this.tcpMan = tcpMan;
        this.node = node;
    }

    private TCPSock(TCPManager tcpMan,
                    Node node,
                    int destAddr,
                    int destPort,
                    Transport transport) {
        this(tcpMan, node);

        clientSeqNo = transport.getSeqNum() + 1;
        clientWindowSize = transport.getWindow();
        clientDestAddr = destAddr;
        clientDestPort = destPort;
        state = State.ESTABLISHED;
        type = Type.SERVER_CLIENT;

        // Send back ACK.
        send(Transport.ACK, 0, clientSeqNo, dummy);
    }

    /** SOCKET API (Non-blocking) **/

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int localPort) {
        if (!canBind()) return -1;

        // TCPManager manages the assignment of ports.
        if (tcpMan.bind(this, localPort) == -1) return -1;

        state = State.BOUND;
        myPort = localPort;

        return 0;
    }

    /**
     * Listen for connections on a socket
     * @param backlog int Maximum number of pending connections
     * @return int 0 on success, -1 otherwise
     */
    public int listen(int backlog) {
        if (!canListen()) return -1;

        state = State.LISTEN;
        type = Type.SERVER_LISTENER;

        // Create the backlog queue.
        serverBacklog = new ArrayBlockingQueue<TCPSock>(backlog);
        serverClientAddresses = new HashSet<String>();

        return 0;
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        if (!isListening()) return null;

        return serverBacklog.poll();
    }

    public boolean isConnectionPending() { return state == State.SYN_SENT; }
    public boolean isClosed() { return state == State.CLOSED; }
    public boolean isConnected() { return state == State.ESTABLISHED; }
    public boolean isClosurePending() { return state == State.SHUTDOWN; }

    /**
     * Initiate connection to a remote socket
     *
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddr, int destPort) {
        if (!canConnect()) return -1;

        // Initialize client variables.
        clientSeqNo = generateSeqNo();
        clientWindowSize = 2 * Transport.MAX_PAYLOAD_SIZE;
        clientDestAddr = destAddr;
        clientDestPort = destPort;

        // Send SYN.
        send(Transport.SYN, 0, clientSeqNo, dummy);

        clientSeqNo ++;
        state = State.SYN_SENT;
        type = Type.CLIENT;

        node.logOutput("Connecting...");

        return 0;
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
        // TODO: Implement.
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
        // TODO: Implement.
    }

    /**
     * Write to the socket up to len bytes from the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer to write from
     * @param pos int starting position in buffer
     * @param len int number of bytes to write
     * @return int on success, the number of bytes written, which may be smaller
     *             than len; on failure, -1
     */
    public int write(byte[] buf, int pos, int len) {
        // TODO: Implement.
        return -1;
    }

    /**
     * Read from the socket up to len bytes into the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer
     * @param pos int starting position in buffer
     * @param len int number of bytes to read
     * @return int on success, the number of bytes read, which may be smaller
     *             than len; on failure, -1
     */
    public int read(byte[] buf, int pos, int len) {
        // TODO: Implement.
        return -1;
    }

    /** END SOCKET API **/

    public void receive(int srcAddr, int srcPort, Transport transport) {
        if (isListening()) {
            switch (transport.getType()) {
            case Transport.SYN:
                node.logOutput("SYN received from " + AddressPair.toString(srcAddr, srcPort));
                receiveSYN(srcAddr, srcPort, transport);
                break;
            }
        } else if (isClient()) {
            switch (transport.getType()) {
            case Transport.ACK:
                if (isConnectionPending()) {
                    state = State.ESTABLISHED;
                    node.logOutput("Connected!");
                } else {

                }
                break;
            }
        } else if (isServerClient()) {

        } else {
            node.logError("Received message when not server or client?");
            return;
        }
    }

    private void receiveSYN(int srcAddr, int srcPort, Transport transport) {
        // Make sure backlog has room.
        if (serverBacklog.remainingCapacity() == 0) return;

        // Make the TCPSock.
        TCPSock sock = new TCPSock(tcpMan, node, srcAddr, srcPort, transport);

        // Register with TCPManager.
        if (tcpMan.bind(srcAddr, srcPort, myPort, sock) == -1) return;

        // Add to backlog.
        serverBacklog.add(sock);
    }

    private int generateSeqNo() {
        Random rand = new Random(System.nanoTime());
        return rand.nextInt(1 << 16);
    }

    // Add connection to backlog.
    private int appendToBacklog(TCPSock sock) {
        if (!isListening()) return -1;

        try { serverBacklog.add(sock); }
        catch (IllegalStateException e) { return -1; }

        return 0;
    }

    private void send(int type, int window, int seqNum, byte[] payload) {
        Transport transport =
            new Transport(
                myPort, clientDestPort, type, window, seqNum, payload);
        node.sendSegment(node.getAddr(),
                         clientDestAddr,
                         Protocol.TRANSPORT_PKT,
                         transport.pack());
    }

    // Handles timeouts.
    private void timeout() {

    }

    private boolean canBind() { return state == State.UNBOUND; }
    private boolean canListen() { return state == State.BOUND; }
    private boolean canConnect() { return state == State.BOUND; }
    private boolean isListening() { return type == Type.SERVER_LISTENER; }
    private boolean isServerClient() { return type == Type.SERVER_CLIENT; }
    private boolean isClient() { return type == Type.CLIENT; }
}
