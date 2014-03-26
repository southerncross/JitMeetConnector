package test;

import java.util.Scanner;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;

public class JitMeetConnector
{
    public static void main(String[] args)
    {
        // create XMPP connection
        ConnectionConfiguration conf =
            new ConnectionConfiguration("jitmeet.example.com", 5222);
        XMPPConnection.DEBUG_ENABLED = true;
        XMPPConnection connection = new XMPPConnection(conf);

        try
        {
            // login anonymously, get temporary account
            connection.connect();
            connection.loginAnonymously();

            // display connection information
            System.out.println("ConnectionID: " + connection.getConnectionID());
            System.out.println("Host: " + connection.getHost());
            System.out.println("Port: " + connection.getPort());
            System.out.println("ServiceName: " + connection.getServiceName());
            System.out.println("User: " + connection.getUser());

            // display all sent packets
            connection.addPacketSendingListener(new PacketListener()
            {

                @Override
                public void processPacket(Packet packet)
                {
                    System.out.println("----->: " + packet.toXML());
                }
            }, new PacketFilter()
            {

                @Override
                public boolean accept(Packet packet)
                {
                    return true;
                }
            });

            // display all incoming packets
            connection.addPacketListener(new PacketListener()
            {
                @Override
                public void processPacket(Packet packet)
                {
                    if (packet.getClass() == JingleIQ.class)
                    {
                        JingleIQ j = (JingleIQ) packet;
                        System.out.println("<-----: [jingle packet] "
                            + j.getSID() + " : " + j.getAction() + " : "
                            + packet.toXML());
                    }
                    else
                    {
                        System.out.println("<-----: " + packet.toXML());
                    }
                }
            }, new PacketFilter()
            {

                @Override
                public boolean accept(Packet packet)
                {
                    return true;
                }
            });

            // join conference
            MultiUserChat muc =
                new MultiUserChat(connection,
                    "krephqivqz7iudi@conference.example.com");
            muc.join("JitMeetConnector");

            Scanner s = new Scanner(System.in);
            while (true)
            {
                String str = s.nextLine();
                if (str.equalsIgnoreCase("bye"))
                    break;
                muc.sendMessage(str);
            }

            connection.disconnect();
        }
        catch (XMPPException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
