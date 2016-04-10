# Q’s TCP
By Qingyang Chen. TCP implementation for CPSC 433. Relevant files are under proj/.

# Classes

## TCPManager
Manages the sockets in use (including binding of ports and creation of sockets), and acts as the interface between Node and the sockets themselves (by multiplexing/demultiplexing). See *SocketManager* for how it manages the sockets.

## TCPSock
Represents the actual socket. An instance can be of four types:
- NONE (just initialized and not determined to be a specific type)
- SERVER_LISTENER (a server listening on the port)
- SERVER_CLIENT (an accepted connection by a SERVER_LISTENER that can perform *read* from the TCP stream)
- CLIENT (connected to a SERVER_LISTENER and can perform *write* to the TCP stream)
Depending on the type, TCPSock keeps an instance of either *TCPSockClient*, *TCPSockServer*, or *TCPSockServerClient*, and uses them to handle the logic behind sending and receiving segments.

## TCPSockClient
Encapsulates the methods and variables for a CLIENT-type socket. Mostly, it sends data, and waits for ACKs from the server side. Uses *TCPSockClientTimer* to handle timeouts.

## TCPSockClientTimer
Maintains a segment queue of un-ACKed segments, and also manages a timer that whenever it times out, the first segment in the queue is resent. See *Segment.Buffer* for details on the segment queue. Only one timer is active, and any restarts of the timer invalidates any previously attached timeout event.

## TCPSockServer
Encapsulates the methods and variables for a SERVER_LISTENER-type socket. Maintains a backlog queue of attempted connections that haven’t yet been accepted.

## TCPSockServerClient
Encapsulates the methods and variables for a SERVER_CLIENT-type socket. Receives DATA from the client side and sends ACK for the highest consecutive sequence number received. Queues up segments from the client that have gaps from previous segments until the gaps are closed. See *Segment.Buffer* for details on this segment queue.

## SocketManager
Maps from ports to a socket set (*SocketManager.SockSet*). It can assign and unassign ports and demultiplex address-port pairs into the sockets associated with them.

## SocketManager.SockSet
Maintains a map from a string representation of an address-port pair to the socket associated with that pair. An empty string indicates that the socket is a listening socket.

## Segment
Represents a data segment in the TCP stream that consists of a type, sequence number, payload, and the time the segment was sent (for round-trip-time calculations).

## Segment.Buffer
A priority queue of *Segment*s that orders the segments by their sequence numbers.

# Protocol

## Connect
1. Client sends SYN.
2. Server receives SYN, creates the SERVER_CLIENT-type socket for the connection, sends back ACK, and adds the new socket onto the backlog for *accept*.
3. Client receives ACK, increments its *send base*, and sets its state to “Connection Established”.

## Data Sending
1. Client sends DATA, adds the segment to the timer queue, starts the timer if it is not running, and increments its sequence number by the size of the payload.
2. Server receives DATA (via a SERVER_CLIENT-type socket) and checks the sequence number. The segment is added into its segment queue. If the sequence number of in-order, all consecutive segments on the queue is unloaded into the *read buffer*. An ACK is sent for the highest unloaded sequence number.
3. Client receives ACK. If the ACK is a duplicate ACK, upon receiving 3, the first unACKed segment is resent; otherwise, the *send base* is updated to the ACKed sequence number. If more segments still need to be ACKed, the timer is restarted.

## Close
1. Client sends FIN, increments its sequence number, and sets its state to “Shutting down…”.
2. Server receives FIN and adds it to its segment buffer. An ACK for the FIN is sent once all prior segments have been ACKed. At this stage, the server closes the SERVER_CLIENT-type socket permanently.
3. Client receives ACK for the FIN and closes its socket permanently.

# Features

## Timeout Optimization
On the client side, upon receiving an ACK, a round-trip-time (sampleRTT) can be calculated for the ACKed segment (only valid if the segment was not resent). The timeout interval is then readjusted by:
`estimatedRTT = 0.875 x estimatedRTT + 0.125 x sampleRTT
devRTT = 0.75 x devRTT + 0.25 x | sampleRTT - estimatedRTT |
timeoutInterval = estimatedRTT + 4 x devRTT`
The default values are:
`estimatedRTT = 1000 ms
devRTT = 0
timeoutInterval = 1000 ms`

## TCP Fast Retransmit
Upon receiving 3 duplicate ACKs, the first unACKed segment is resent. This helps to resend lost segments without waiting for the timer to timeout.

## Flow Control
The SERVER_CLIENT-type socket sends back a *window size* equal to how much space is left in the read buffer. The client uses this information to determine the maximum number of bytes it can still send (along with the *congestion window size* - see *AIMD Congestion Control*). A minimum of 1 byte is always sent. This helps to make sure the client does not overwhelm the server with new data.

## AIMD Congestion Control
The client maintains a *congestion window size* that is initially the maximum payload size. This window size is additively increased whenever an ACK is received, and multiplicatively decreased whenever the timer times out. A smaller decrease is applied when a fast retransmit is performed. The client uses this information to determine the maximum number of bytes it can still send (along with the *flow control window size* - see *Flow Control*). A minimum of 1 byte is always sent. This helps to make sure the client does not overwhelm the network with new data.
