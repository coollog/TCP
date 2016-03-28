public class TCPSock {
    public static final byte dummy[] = new byte[0];

    private static final int WINDOW_SIZE = 0x2000;
    private static final int READ_BUFFER_SIZE = 0x4000;

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

    private TCPSockServer server; // Server variables.
    private TCPSockClient client; // Client variables.
    private TCPSockServerClient serverClient; // Server-client variables.

    public TCPSock(TCPManager tcpMan, Node node) {
        this.tcpMan = tcpMan;
        this.node = node;
    }

    private TCPSock(TCPManager tcpMan,
                    Node node,
                    int myPort,
                    int destAddr,
                    int destPort,
                    Transport transport) {
        this(tcpMan, node);

        client = new TCPSockClient(destAddr, destPort);
        client.setNextSeqNum(transport.getSeqNum() + 1);
        client.setWindowSize(transport.getWindow());

        serverClient = new TCPSockServerClient(READ_BUFFER_SIZE);

        state = State.ESTABLISHED;
        type = Type.SERVER_CLIENT;
        this.myPort = myPort;

        // Send back ACK.
        send(Transport.ACK, 0, client.getNextSeqNum(), dummy);
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
        if (!tcpMan.bind(this, localPort)) return -1;

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

        // Start the server.
        server = new TCPSockServer(backlog);

        return 0;
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        if (!isListening()) return null;

        return server.pollBacklog();
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

        // Start the client.
        client = new TCPSockClient(destAddr, destPort);

        // Send SYN.
        // send(Transport.SYN, 0, clientNextSeqNum, dummy);
        clientSend(Transport.SYN, dummy);

        client.incNextSeqNum(1);
        state = State.SYN_SENT;
        type = Type.CLIENT;

        node.logOutput("Connecting...");

        return 0;
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
        if (isListening()) return;

        client.incNextSeqNum(1);
        clientSend(Transport.FIN, dummy);
        state = State.SHUTDOWN;

        node.logOutput("Sent FIN (" + client.getNextSeqNum() + ")");
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
        if (isClosed()) return;

        state = State.CLOSED;

        if (isServerClient()) {
            tcpMan.unbind(client.getDestAddr(),
                          client.getDestPort(),
                          myPort);
            return;
        }

        tcpMan.unbind(myPort);
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
        if (isClosed()) return -1;
        if (!isClient()) return -1;

        len = Math.min(Transport.MAX_PAYLOAD_SIZE, len);

        int bytesWritten = 0;

        // Write buf to payload.
        byte[] payload = new byte[len];
        for (int i = 0; i < Math.min(len, buf.length - pos); i ++) {
            payload[i] = buf[pos + i];
            bytesWritten ++;
        }

        clientSend(Transport.DATA, payload);

        return bytesWritten;
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
        if (isClosed()) return -1;
        if (!isServerClient()) return -1;

        return serverClient.read(buf, pos, len);
    }

    /** END SOCKET API **/

    public Node getNode() { return node; }
    public int getMyAddr() { return node.getAddr(); }
    public Manager getManager() { return node.getManager(); }

    public void receive(int srcAddr, int srcPort, Transport transport) {
        if (isClosed()) return;

        switch (transport.getType()) {

        case Transport.SYN:
            node.logOutput("SYN received from " + SocketManager.AddressPair.toString(srcAddr, srcPort));
            receiveSYN(srcAddr, srcPort, transport);
            break;

        case Transport.DATA:
            receiveDATA(transport);
            break;

        case Transport.ACK:
            receiveACK(transport);
            break;

        case Transport.FIN:
            receiveFIN(transport);
            break;
        }

        if (isNone())
            node.logError("Received message when not server or client?");
    }

    // Handles timeouts.
    public void timeout(Integer seqNum) {
        Segment segment = client.peekTimerQueue();
        if (segment == null) {
            client.stopTimer();
            node.logOutput("Timeout: No segments on queue.");
            return;
        }

        // // If the segment has already been ACK'd, return.
        // if (segment.getSeqNum() != seqNum) {
        //     node.logOutput("Timeout: " + seqNum + "!=" + segment.getSeqNum());
        //     return;
        // }

        node.logOutput("Timeout resend: " + segment.getType() + ", " + segment.getSeqNum());

        clientSend(
            segment.getType(), segment.getSeqNum(), segment.getPayload());

        client.startTimer(this);
    }

    // Server receive SYN on listener.
    private void receiveSYN(int srcAddr, int srcPort, Transport transport) {
        if (isServerClient()) {
            // Send back ACK for SYN.
            send(Transport.ACK, 0, client.getNextSeqNum(), dummy);
            return;
        }

        // Make sure backlog has room.
        if (server.isBacklogFull()) {
            node.logError("Backlog full.");
            return;
        }

        // Make the TCPSock.
        TCPSock sock =
            new TCPSock(tcpMan, node, myPort, srcAddr, srcPort, transport);

        // Register with TCPManager.
        if (!tcpMan.bind(srcAddr, srcPort, myPort, sock)) {
            node.logError("Could not register address with SocketManager.");
            return;
        }

        // Add to backlog.
        if (!server.addToBacklog(sock)) {
            sock.release();
            node.logError("Could not add to backlog.");
        }
    }

    // Client receive ACK.
    private void receiveACK(Transport transport) {
        receiveACKForFIN(transport);
        receiveACKForSYN(transport);
        receiveACKForDATA(transport);

        if (!isListening()) {
            // Remove from timer queue messages that are ACK'd.
            client.pruneTimerQueue(client.getSendBase());
        }
    }

    private void receiveACKForSYN(Transport transport) {
        if (!isClient()) return;
        if (!isConnectionPending()) return;

        if (transport.getSeqNum() == client.getNextSeqNum()) {
            state = State.ESTABLISHED;
            client.setSendBase(client.getNextSeqNum() + 1);

            node.logOutput("Connected!");
            return;
        }
        node.logError("Received future ACK while not connected.");
    }

    private void receiveACKForDATA(Transport transport) {
        if (isClosurePending()) return;
        if (!isClient()) return;
        if (isConnectionPending()) return;

        if (transport.getSeqNum() > client.getSendBase()) {
            node.logOutput("Received ACK, updated sendBase from " + client.getSendBase() + " to " + transport.getSeqNum());

            client.setSendBase(transport.getSeqNum());

            // If there are currently any not-yet-acknowledged segments,
            // start timer.
            if (client.getNextSeqNum() > client.getSendBase()) {
                node.logOutput("Still has unACKed segments till " + client.getNextSeqNum());

                client.startTimer(this);
            }
        } else { // A duplicate ACK received.
            // increment number of duplicate ACKs received for y
            // if (number of duplicate ACKS received for y==3) {
            //     /* TCP fast retransmit */
            //     resend segment with sequence number y
            // }
        }
    }

    private void receiveACKForFIN(Transport transport) {
        if (!isClosurePending()) return;

        if (transport.getSeqNum() != client.getNextSeqNum()) {
            node.logError("Received incorrect FIN ACK (" + client.getNextSeqNum() + "!=" + transport.getSeqNum() + ").");
            return;
        }

        client.setSendBase(transport.getSeqNum() + 1);
        release();

        node.logOutput("Received ACK for FIN. Closed.");
    }

    // ServerClient receive DATA.
    private void receiveDATA(Transport transport) {
        if (!isServerClient()) return;

        if (isConnected() || isClosurePending()) {
            node.logOutput("Received data (" + transport.getPayload().length + ") with seqNum " + transport.getSeqNum());

            if (client.getNextSeqNum() == transport.getSeqNum()) {
                int prevSeqNum = client.getNextSeqNum();

                // Segment received is in-order.
                // Deliver all consecutive received segments and ACK for last
                // delivered segment.
                serverClient.bufferSegment(
                    transport.getSeqNum(), transport.getPayload());
                client.incNextSeqNum(serverClient.unloadSegmentBuffer());

                node.logOutput("Sent ACK for data (" + transport.getPayload().length + ") with seqNum " + prevSeqNum + " and ackSeqNum " + client.getNextSeqNum());
            } else if (client.getNextSeqNum() < transport.getSeqNum()) {
                // Segment received is out-of-order.
                // Queue up the segment.
                serverClient.bufferSegment(
                    transport.getSeqNum(), transport.getPayload());
            } else {
                // Segment received is old, ignore.
                return;
            }

            // Send an ACK no matter what.
            send(Transport.ACK, 0, client.getNextSeqNum(), dummy);

            // Check if ACK needs to be sent for FIN if in SHUTDOWN.
            sendACKForFIN();
        }
    }

    private void receiveFIN(Transport transport) {
        if (!isServerClient()) return;

        if (!isClosurePending()) {
            node.logOutput("F");
            state = State.SHUTDOWN;
        }

        // Used for delayed ACK for FIN if still needs to receive older DATA.
        client.setSeqNumFIN(transport.getSeqNum());

        // Check if ACK needs to be sent for FIN.
        sendACKForFIN();
    }

    private void sendACKForFIN() {
        if (client.getSeqNumFIN() == -1) return;
        if (client.getNextSeqNum() + 1 < client.getSeqNumFIN()) return;

        send(Transport.ACK, 0, client.getSeqNumFIN(), dummy);

        release();

        node.logOutput("Sent ACK for FIN (" + client.getSeqNumFIN() + ")");
    }

    private void clientSend(int type, byte[] payload) {
        node.logOutput("Send: " + type + ", " + client.getNextSeqNum() + ", " + payload.length);

        clientSend(type, client.getNextSeqNum(), payload);
        client.incNextSeqNum(payload.length);
    }
    private void clientSend(int type, int seqNum, byte[] payload) {
        send(type, client.getWindowSize(), seqNum, payload);

        // Add segment to the queue waiting for ACK.
        client.addToTimerQueue(type, seqNum, payload);

        // Start timer.
        if (!client.isTimerRunning()) client.startTimer(this);
    }

    private void send(int type, int window, int seqNum, byte[] payload) {
        Transport transport =
            new Transport(
                myPort, client.getDestPort(), type, window, seqNum, payload);
        node.sendSegment(node.getAddr(),
                         client.getDestAddr(),
                         Protocol.TRANSPORT_PKT,
                         transport.pack());
    }

    private boolean canBind() { return state == State.UNBOUND; }
    private boolean canListen() { return state == State.BOUND; }
    private boolean canConnect() { return state == State.BOUND; }
    private boolean isNone() { return type == Type.NONE; }
    private boolean isListening() { return type == Type.SERVER_LISTENER; }
    private boolean isServerClient() { return type == Type.SERVER_CLIENT; }
    private boolean isClient() { return type == Type.CLIENT; }
}
