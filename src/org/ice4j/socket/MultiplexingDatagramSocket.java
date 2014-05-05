/*
 * ice4j, the OpenSource Java Solution for NAT and Firewall Traversal.
 * Maintained by the SIP Communicator community (http://sip-communicator.org).
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.ice4j.socket;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

/**
 * Represents a <tt>DatagramSocket</tt> which allows filtering
 * <tt>DatagramPacket</tt>s it reads from the network using
 * <tt>DatagramPacketFilter</tt>s so that the <tt>DatagramPacket</tt>s do not
 * get received through it but through associated
 * <tt>MultiplexedDatagramSocket</tt>s.
 *
 * @author Lubomir Marinov
 */
public class MultiplexingDatagramSocket
    extends SafeCloseDatagramSocket
{

    /**
     * The <tt>Logger</tt> used by the <tt>MultiplexingDatagramSocket</tt> class
     * and its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MultiplexingDatagramSocket.class.getName());

    /**
     * The constant which represents an empty array with
     * <tt>MultiplexedDatagramSocket</tt> element type. Explicitly defined in
     * order to reduce allocations.
     */
    private static final MultiplexedDatagramSocket[] NO_SOCKETS
        = new MultiplexedDatagramSocket[0];

    /**
     * The indicator which determines whether this <tt>DatagramSocket</tt> is
     * currently reading from the network  using
     * {@link DatagramSocket#receive(DatagramPacket)}. When <tt>true</tt>,
     * subsequent requests to read from the network will be blocked until the
     * current read is finished.
     */
    private boolean inReceive = false;

    /**
     * The list of <tt>DatagramPacket</tt>s to be received through this
     * <tt>DatagramSocket</tt> i.e. not accepted by the <tt>DatagramFilter</tt>s
     * of {@link #sockets} at the time of the reading from the network.
     */
    private final List<DatagramPacket> received
        = new LinkedList<DatagramPacket>();

    /**
     * The value with which {@link DatagramSocket#setReceiveBufferSize(int)} is
     * to be invoked if {@link #setReceiveBufferSize} is <tt>true</tt>. 
     */
    private int receiveBufferSize;

    /**
     * The <tt>Object</tt> which synchronizes the access to {@link #inReceive}.
     */
    private final Object receiveSyncRoot = new Object();

    /**
     * The indicator which determines whether
     * {@link DatagramSocket#setReceiveBufferSize(int)} is to be invoked with
     * the value of {@link #receiveBufferSize}.
     */
    private boolean setReceiveBufferSize = false;

    /**
     * The <tt>MultiplexedDatagramSocket</tt>s filtering
     * <tt>DatagramPacket</tt>s away from this <tt>DatagramSocket</tt>.
     */
    private MultiplexedDatagramSocket[] sockets = NO_SOCKETS;

    /**
     * The <tt>Object</tt> which synchronizes the access to the {@link #sockets}
     * field of this instance.
     */
    private final Object socketsSyncRoot = new Object();

    /**
     * Buffer variable for storing the SO_TIMEOUT value set by the
     * last <tt>setSoTimeout()</tt> call. Altough not strictly needed,
     * getting the locally stored value as opposed to retrieving it
     * from a parent <tt>getSoTimeout()</tt> call seems to
     * significantly improve efficiency, at least on some platforms.
     */
    private int soTimeout = 0;

    /**
     * Initializes a new <tt>MultiplexingDatagramSocket</tt> instance which is
     * to enable <tt>DatagramPacket</tt> filtering and binds it to any available
     * port on the local host machine.  The socket will be bound to the wildcard
     * address, an IP address chosen by the kernel.
     *
     * @throws SocketException if the socket could not be opened, or the socket
     * could not bind to the specified local port
     * @see DatagramSocket#DatagramSocket()
     */
    public MultiplexingDatagramSocket()
        throws SocketException
    {
        super();
    }

    /**
     * Initializes a new <tt>MultiplexingDatagramSocket</tt> instance which is
     * to enable <tt>DatagramPacket</tt> filtering on a specific
     * <tt>DatagramSocket</tt>.
     *
     * @param delegate the <tt>DatagramSocket</tt> on which
     * <tt>DatagramPacket</tt> filtering is to be enabled by the new instance
     * @throws SocketException if anything goes wrong while initializing the new
     * instance
     */
    public MultiplexingDatagramSocket(DatagramSocket delegate)
        throws SocketException
    {
        super(delegate);
    }

    /**
     * Initializes a new <tt>MultiplexingDatagramSocket</tt> instance which is
     * to enable <tt>DatagramPacket</tt> filtering and binds it to the specified
     * port on the local host machine.  The socket will be bound to the wildcard
     * address, an IP address chosen by the kernel.
     *
     * @param port the port to bind the new socket to
     * @throws SocketException if the socket could not be opened, or the socket
     * could not bind to the specified local port
     * @see DatagramSocket#DatagramSocket(int)
     */
    public MultiplexingDatagramSocket(int port)
        throws SocketException
    {
        super(port);
    }

    /**
     * Initializes a new <tt>MultiplexingDatagramSocket</tt> instance which is
     * to enable <tt>DatagramPacket</tt> filtering, bound to the specified local
     * address.  The local port must be between 0 and 65535 inclusive. If the IP
     * address is 0.0.0.0, the socket will be bound to the wildcard address, an
     * IP address chosen by the kernel.
     *
     * @param port the local port to bind the new socket to
     * @param laddr the local address to bind the new socket to
     * @throws SocketException if the socket could not be opened, or the socket
     * could not bind to the specified local port
     * @see DatagramSocket#DatagramSocket(int, InetAddress)
     */
    public MultiplexingDatagramSocket(int port, InetAddress laddr)
        throws SocketException
    {
        super(port, laddr);
    }

    /**
     * Initializes a new <tt>MultiplexingDatagramSocket</tt> instance which is
     * to enable <tt>DatagramPacket</tt> filtering, bound to the specified local
     * socket address.
     * <p>
     * If the specified local socket address is <tt>null</tt>, creates an
     * unbound socket.
     * </p>
     *
     * @param bindaddr local socket address to bind, or <tt>null</tt> for an
     * unbound socket
     * @throws SocketException if the socket could not be opened, or the socket
     * could not bind to the specified local port
     * @see DatagramSocket#DatagramSocket(SocketAddress)
     */
    public MultiplexingDatagramSocket(SocketAddress bindaddr)
        throws SocketException
    {
        super(bindaddr);
    }

    /**
     * Initializes a new <tt>DatagramPacket</tt> instance which is a clone of a
     * specific <tt>DatagramPacket</tt> i.e. the properties of the clone
     * <tt>DatagramPacket</tt> are clones of the specified
     * <tt>DatagramPacket</tt>.
     *
     * @param p the <tt>DatagramPacket</tt> to clone
     * @return a new <tt>DatagramPacket</tt> instance which is a clone of the
     * specified <tt>DatagramPacket</tt>
     */
    public static DatagramPacket clone(DatagramPacket p)
    {
        synchronized (p)
        {
            byte[] data = p.getData().clone();
            InetAddress address = p.getAddress();
            int port = p.getPort();

            if ((address == null) || (port < 0))
            {
                return new DatagramPacket(data, p.getOffset(), p.getLength());
            }
            else
            {
                return
                    new DatagramPacket(
                            data, p.getOffset(), p.getLength(),
                            address, port);
            }
        }
    }

    /**
     * Closes a specific <tt>MultiplexedDatagramSocket</tt> which filters
     * <tt>DatagramPacket</tt>s away from this <tt>DatagramSocket</tt>.
     *
     * @param multiplexed the <tt>MultiplexedDatagramSocket</tt> to close
     */
    void close(MultiplexedDatagramSocket multiplexed)
    {
        synchronized (socketsSyncRoot)
        {
            int socketCount = sockets.length;

            for (int i = 0; i < socketCount; i++)
            {
                if (sockets[i].equals(multiplexed))
                {
                    if (socketCount == 1)
                        sockets = NO_SOCKETS;
                    else
                    {
                        MultiplexedDatagramSocket[] newSockets
                            = new MultiplexedDatagramSocket[socketCount - 1];

                        System.arraycopy(sockets, 0, newSockets, 0, i);
                        System.arraycopy(
                                sockets, i + 1,
                                newSockets, i,
                                newSockets.length - i);
                        sockets = newSockets;
                    }
                    break;
                }
            }
        }
    }

    /**
     * Copies the properties of a specific <tt>DatagramPacket</tt> to another
     * <tt>DatagramPacket</tt>. The property values are not cloned.
     *
     * @param src the <tt>DatagramPacket</tt> which is to have its properties
     * copied to <tt>dest</tt>
     * @param dest the <tt>DatagramPacket</tt> which is to have its properties
     * set to the value of the respective properties of <tt>src</tt>
     */
    public static void copy(DatagramPacket src, DatagramPacket dest)
    {
        synchronized (dest)
        {
            dest.setAddress(src.getAddress());
            dest.setPort(src.getPort());

            byte[] srcData = src.getData();

            if (srcData == null)
                dest.setLength(0);
            else
            {
                byte[] destData = dest.getData();

                if (destData == null)
                    dest.setLength(0);
                else
                {
                    int destOffset = dest.getOffset();
                    int destLength = destData.length - destOffset;
                    int srcLength = src.getLength();

                    if (destLength >= srcLength)
                        destLength = srcLength;
                    else if (logger.isLoggable(Level.WARNING))
                    {
                        logger.log(
                                Level.WARNING,
                                "Truncating received DatagramPacket data!");
                    }
                    System.arraycopy(
                            srcData, src.getOffset(),
                            destData, destOffset,
                            destLength);
                    dest.setLength(destLength);
                }
            }
        }
    }

    /**
     * Gets a <tt>MultiplexedDatagramSocket</tt> which filters
     * <tt>DatagramPacket</tt>s away from this <tt>DatagramSocket</tt> using a
     * specific <tt>DatagramPacketFilter</tt>. If such a
     * <tt>MultiplexedDatagramSocket</tt> does not exist in this instance, it is
     * created.
     *
     * @param filter the <tt>DatagramPacketFilter</tt> to get a
     * <tt>MultiplexedDatagramSocket</tt> for
     * @return a <tt>MultiplexedDatagramSocket</tt> which filters
     * <tt>DatagramPacket</tt>s away from this <tt>DatagramSocket</tt> using the
     * specified <tt>filter</tt>
     * @throws SocketException if creating the
     * <tt>MultiplexedDatagramSocket</tt> for the specified <tt>filter</tt>
     * fails
     */
    public MultiplexedDatagramSocket getSocket(DatagramPacketFilter filter)
        throws SocketException
    {
        if (filter == null)
            throw new NullPointerException("filter");

        synchronized (socketsSyncRoot)
        {
            /*
             * If a socket for the specified filter exists already, do not
             * create a new one and return the existing.
             */
            for (MultiplexedDatagramSocket socket : sockets)
            {
                if (filter.equals(socket.getFilter()))
                    return socket;
            }

            // Create a new socket for the specified filter.
            MultiplexedDatagramSocket socket
                = new MultiplexedDatagramSocket(this, filter);

            // Remember the new socket.
            int socketCount = sockets.length;

            if (socketCount == 0)
                sockets = new MultiplexedDatagramSocket[] { socket };
            else
            {
                MultiplexedDatagramSocket[] newSockets
                    = new MultiplexedDatagramSocket[socketCount + 1];

                System.arraycopy(sockets, 0, newSockets, 0, socketCount);
                newSockets[socketCount] = socket;
                sockets = newSockets;
            }

            return socket;
        }
    }

    /**
     * Receives a datagram packet from this socket. The <tt>DatagramPacket</tt>s
     * returned by this method do not match any of the
     * <tt>DatagramPacketFilter</tt>s of the <tt>MultiplexedDatagramSocket</tt>s
     * associated with this instance at the time of their receipt. When this
     * method returns, the <tt>DatagramPacket</tt>'s buffer is filled with the
     * data received. The datagram packet also contains the sender's IP address,
     * and the port number on the sender's machine.
     * <p>
     * This method blocks until a datagram is received. The <tt>length</tt>
     * field of the datagram packet object contains the length of the received
     * message. If the message is longer than the packet's length, the message
     * is truncated.
     * </p>
     *
     * @param p the <tt>DatagramPacket</tt> into which to place the incoming
     * data
     * @throws SocketTimeoutException if <tt>setSoTimeout</tt> was
     * previously called and the timeout has expired
     * @throws IOException if an I/O error occurs
     * @see DatagramSocket#receive(DatagramPacket)
     */
    @Override
    public void receive(DatagramPacket p)
        throws SocketTimeoutException, IOException
    {
	receive(received, p, soTimeout);
    }

    /**
     * Receives a <tt>DatagramPacket</tt> from a specific list of
     * <tt>DatagramPacket</tt>s if it is not empty or from the network if the
     * specified list is empty. When this method returns, the
     * <tt>DatagramPacket</tt>'s buffer is filled with the data received. The
     * datagram packet also contains the sender's IP address, and the port
     * number on the sender's machine.
     *
     * @param received the list of previously received <tt>DatagramPacket</tt>
     * from which the first is to be removed and returned if available
     * @param p the <tt>DatagramPacket</tt> into which to place the incoming
     * data
     * @param timeout the maximum time in milliseconds to wait for a
     * packet.  A timeout of zero is interpreted as an infinite
     * timeout
     * @throws SocketTimeoutException if the timeout has expired
     * @throws IOException if an I/O error occurs
     */
    private void receive(List<DatagramPacket> received, DatagramPacket p,
			 int timeout)
        throws SocketTimeoutException, IOException
    {
	long now, start = System.currentTimeMillis();
        DatagramPacket r = null;

        do
        {
	    now = System.currentTimeMillis();
	    if (timeout > 0 && now - start >= timeout)
		throw new SocketTimeoutException("Socket timeout");

            synchronized (received)
            {
                if (!received.isEmpty())
                {
                    r = received.remove(0);
                    if (r != null)
                        break;
                }
            }

            boolean wait;

            synchronized (receiveSyncRoot)
            {
                if (inReceive)
                {
                    wait = true;
                }
                else
                {
                    wait = false;
                    inReceive = true;
                }
            }

            try
            {
                if (wait)
                {
                    synchronized (received)
                    {
                        if (received.isEmpty())
                        {
                            try
                            {
                                received.wait((timeout > 0 ?
					       timeout - (now - start) :
					       1000));
                            }
                            catch (InterruptedException ie)
                            {
                            }
                        }
                        else
                        {
                            received.notifyAll();
                        }
                    }
                    continue;
                }

                DatagramPacket c = clone(p);

                synchronized (receiveSyncRoot)
                {
                    if (setReceiveBufferSize)
                    {
                        setReceiveBufferSize = false;
                        try
                        {
                            super.setReceiveBufferSize(receiveBufferSize);
                        }
                        catch (Throwable t)
                        {
                            if (t instanceof ThreadDeath)
                                throw (ThreadDeath) t;
                        }
                    }
                }
                super.receive(c);

                synchronized (socketsSyncRoot)
                {
                    boolean accepted = false;

                    for (MultiplexedDatagramSocket socket : sockets)
                    {
                        if (socket.getFilter().accept(c))
                        {
                            synchronized (socket.received)
                            {
                                socket.received.add(accepted ? clone(c) : c);
                                socket.received.notifyAll();
                            }
                            accepted = true;

                            /*
                             * Emil: Don't break because we want all filtering
                             * sockets to get the received packet.
                             */
                            //break;
                        }
                    }

                    if (!accepted)
                    {
                        synchronized (this.received)
                        {
                            this.received.add(c);
                            this.received.notifyAll();
                        }
                    }
                }
            }
            finally
            {
                synchronized (receiveSyncRoot)
                {
                    if (!wait)
                        inReceive = false;
                }
            }
        }
        while (true);

        copy(r, p);
    }

    /**
     * Receives a <tt>DatagramPacket</tt> from this <tt>DatagramSocket</tt> upon
     * request from a specific <tt>MultiplexedDatagramSocket</tt>.
     *
     * @param multiplexed the <tt>MultiplexedDatagramSocket</tt> which requests
     * the receipt of a <tt>DatagramPacket</tt> from the network
     * @param p the <tt>DatagramPacket</tt> to receive the data from the network
     * @throws SocketTimeoutException if <tt>setSoTimeout</tt> was
     * previously called and the timeout has expired
     * @throws IOException if an I/O error occurs
     */
    void receive(MultiplexedDatagramSocket multiplexed, DatagramPacket p)
        throws SocketTimeoutException, IOException
    {
        receive(multiplexed.received, p, multiplexed.getSoTimeout());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReceiveBufferSize(int receiveBufferSize)
        throws SocketException
    {
        synchronized (receiveSyncRoot)
        {
            this.receiveBufferSize = receiveBufferSize;

            if (inReceive)
            {
                setReceiveBufferSize = true;
            }
            else
            {
                super.setReceiveBufferSize(receiveBufferSize);
                setReceiveBufferSize = false;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSoTimeout(int timeout)
    	throws SocketException
    {
    	super.setSoTimeout(timeout);
    	soTimeout = timeout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSoTimeout()
    {
    	return soTimeout;
    }
}
