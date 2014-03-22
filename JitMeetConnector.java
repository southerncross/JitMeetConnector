package test;

import java.util.Scanner;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.MultiUserChat;

public class JitMeetConnector
{
    public static void main(String[] args)
    {
        ConnectionConfiguration conf = new ConnectionConfiguration("jitmeet.example.com", 5222);
        XMPPConnection.DEBUG_ENABLED = true;
        XMPPConnection connection = new XMPPConnection(conf);
        
        try
        {
            connection.connect();
            connection.loginAnonymously();
            
            System.out.println("ConnectionID: " + connection.getConnectionID());
            System.out.println("Host: " + connection.getHost());
            System.out.println("Port: " + connection.getPort());
            System.out.println("ServiceName: " + connection.getServiceName());
            System.out.println("User: " + connection.getUser());
            
            MultiUserChat muc = new MultiUserChat(connection, "0kn6mlqar3w8w7b9@conference.jitmeet.example.com");
            muc.join("JitMeetConnector");
            
            while (true)
            {
                Scanner s = new Scanner(System.in);
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
