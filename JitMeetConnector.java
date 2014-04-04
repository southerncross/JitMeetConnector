package test;

import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.MediaUseCase;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.muc.MultiUserChat;

public class JitMeetConnector
{
    private XMPPConnection connection;

    private Map<String, MediaDevice> devices;

    private Agent iceAgent;

    public static void main(String[] args)
    {
        JitMeetConnector connector;

        connector = new JitMeetConnector();
        connector.run();
    }

    public void initiate()
    {
        // start libjitsi
        LibJitsi.start();

        // initiate Agent
        initiateAgent();

        // initiate devices
        initiateDevices();

        // initiate packet extension
        initiatePacketExtension();

        // initiate packet listener
        initiatePacketListener();

        // create XMPP connection
        ConnectionConfiguration conf =
            new ConnectionConfiguration("jitmeet.example.com", 5222);
        XMPPConnection.DEBUG_ENABLED = true;
        connection = new XMPPConnection(conf);
    }

    private void uninitiate()
    {
        connection.disconnect();
        LibJitsi.stop();
    }

    private void initiateAgent()
    {
        // agent
        iceAgent = new Agent();
        int MIN_STREAM_PORT = 5000;
        int MAX_STREAM_PORT = 9000;
        Random rand = new Random();
        int streamPort =
            rand.nextInt() % (MAX_STREAM_PORT - MIN_STREAM_PORT)
                + MIN_STREAM_PORT;

        streamPort = (streamPort & 1) == 1 ? streamPort + 1 : streamPort;
        streamPort =
            (streamPort + 1) > MAX_STREAM_PORT ? MIN_STREAM_PORT : streamPort;
        int rtpPort = streamPort;
        IceMediaStream stream = iceAgent.createMediaStream("audio");
        try
        {
            iceAgent.createComponent(stream, Transport.UDP, rtpPort, rtpPort,
                rtpPort + 100);
            iceAgent.createComponent(stream, Transport.UDP, rtpPort + 1,
                rtpPort + 1, rtpPort + 101);
        }
        catch (BindException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        streamPort += 2;
        streamPort =
            (streamPort + 1) > MAX_STREAM_PORT ? MIN_STREAM_PORT : streamPort;
        stream = iceAgent.createMediaStream("video");
        rtpPort = streamPort;
        try
        {
            iceAgent.createComponent(stream, Transport.UDP, rtpPort, rtpPort,
                rtpPort + 100);
            iceAgent.createComponent(stream, Transport.UDP, rtpPort + 1,
                rtpPort + 1, rtpPort + 101);
        }
        catch (BindException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private void initiateDevices()
    {
        devices = new HashMap<String, MediaDevice>();
        MediaService service = LibJitsi.getMediaService();
        devices.put(MediaType.AUDIO.toString(),
            service.getDefaultDevice(MediaType.AUDIO, MediaUseCase.CALL));
        devices.put(MediaType.VIDEO.toString(),
            service.getDefaultDevice(MediaType.VIDEO, MediaUseCase.CALL));
    }

    private void initiatePacketExtension()
    {
        // create IQ handler
        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        providerManager.addExtensionProvider("media", "http://estos.de/ns/mjs",
            new MediaExtensionProvider());
    }

    private void initiatePacketListener()
    {
        // display all sent packets
        connection.addPacketSendingListener(new PacketListener()
        {

            @Override
            public void processPacket(Packet packet)
            {
                System.out.println("--->: " + packet.toXML());
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

                if (packet instanceof Presence)
                {
                    System.out.println("<---: " + packet.toXML());
                }
                else if (packet.getClass() == JingleIQ.class)
                {
                    JingleIQ in = (JingleIQ) packet;
                    System.out.println("<---: " + packet.toXML());

                    if (IQ.Type.SET == in.getType())
                    {
                        // send a result IQ whenever meet a set IQ
                        connection.sendPacket(IQ.createResultIQ(in));
                    }

                    if (JingleAction.SESSION_INITIATE == in.getAction())
                    {
                        acceptJingleSessionInitiate(in);
                    }
                }
                else
                {
                    System.out.println("<---: " + packet.toXML());
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
    }

    public void run()
    {
        try
        {
            initiate();

            // login anonymously, get temporary account
            connection.connect();
            connection.loginAnonymously();

            // display connection information
            System.out.println("ConnectionID: " + connection.getConnectionID());
            System.out.println("Host: " + connection.getHost());
            System.out.println("Port: " + connection.getPort());
            System.out.println("ServiceName: " + connection.getServiceName());
            System.out.println("User: " + connection.getUser());

            // join conference
            MultiUserChat muc =
                new MultiUserChat(connection,
                    "e9b5w7crd09evcxr@conference.example.com");
            muc.join("JitMeetConnector");

            Scanner s = new Scanner(System.in);
            while (true)
            {
                String str = s.nextLine();
                if (str.equalsIgnoreCase("bye"))
                    break;
                muc.sendMessage(str);
            }
        }
        catch (XMPPException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally
        {
            uninitiate();
        }
    }

    private void acceptJingleSessionInitiate(JingleIQ initiateIQ)
    {
        System.out
            .println("lishunyang@acceptJingleSessionInitiate:JitMeetConnector");
        String peerJid = initiateIQ.getFrom();
        String myJid = initiateIQ.getTo();
        String sessionId = initiateIQ.getSID();

        // construct content list
        List<ContentPacketExtension> acceptContents =
            new ArrayList<ContentPacketExtension>();
        List<ContentPacketExtension> initiateContents =
            initiateIQ.getContentList();
        for (ContentPacketExtension initiateContent : initiateContents)
        {
            // description
            RtpDescriptionPacketExtension description =
                generateDescription(initiateContent);

            // transport
            IceUdpTransportPacketExtension transport =
                generateTransport(initiateContent);

            // content
            ContentPacketExtension content =
                generateContent(description, transport);

            acceptContents.add(content);
        }

        JingleIQ acceptIQ =
            JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId,
                acceptContents);
        System.out.println("[accept] " + acceptIQ.toXML());
        connection.sendPacket(acceptIQ);
    }

    private RtpDescriptionPacketExtension generateDescription(
        ContentPacketExtension content)
    {
        System.out.println("lishunyang@generateDescription:JitMeetConnector");
        RtpDescriptionPacketExtension description =
            new RtpDescriptionPacketExtension();

        // basic info
        description.setMedia(content.getName());
        // TODO add ssrc

        // payload-type
        List<RtpDescriptionPacketExtension> descriptions =
            content
                .getChildExtensionsOfType(RtpDescriptionPacketExtension.class);
        for (RtpDescriptionPacketExtension des : descriptions) // 实际上只有一个description
        {
            MediaDevice device = devices.get(des.getMedia());
            // 有多个payloadType，取第一个匹配id的，这个不是很明白
            List<PayloadTypePacketExtension> payloadTypes =
                des.getPayloadTypes();
            List<MediaFormat> formats = device.getSupportedFormats();
            for (PayloadTypePacketExtension payloadType : payloadTypes)
            {
                MediaFormat format = null;
                for (MediaFormat mf : formats)
                {
                    if (mf.getRTPPayloadType() == payloadType.getID())
                    {
                        format = mf;
                        break;
                    }
                }
                if (null == format)
                    continue;
                description.addPayloadType(formatToPayload(format));
                break;
            }
        }

        return description;
    }

    private IceUdpTransportPacketExtension generateTransport(
        ContentPacketExtension content)
    {
        System.out.println("lishunyang@generateTransport:JitMeetConnector");
        IceUdpTransportPacketExtension transport =
            new IceUdpTransportPacketExtension();

        // basic info
        transport.setPassword(iceAgent.getLocalPassword());
        transport.setUfrag(iceAgent.getLocalUfrag());

        // candidates
        int id = 0;
        System.out.println(content.getName());
        IceMediaStream stream = iceAgent.getStream(content.getName());
        for (Component component : stream.getComponents())
        {
            for (Candidate<?> can : component.getLocalCandidates())
            {
                CandidatePacketExtension candidate =
                    new CandidatePacketExtension();
                candidate.setComponent(component.getComponentID());
                candidate.setFoundation(can.getFoundation());
                candidate.setGeneration(iceAgent.getGeneration());
                candidate.setID(String.valueOf(id++));
                candidate.setNetwork(1); // TODO 这里不太明白，设为1吧
                TransportAddress ta = can.getTransportAddress();
                candidate.setIP(ta.getHostAddress());
                candidate.setPort(ta.getPort());
                candidate.setPriority(can.getPriority());
                candidate.setProtocol(can.getTransport().toString());
                candidate.setType(CandidateType.valueOf(can.getType()
                    .toString()));
                transport.addCandidate(candidate);
            }
        }

        return transport;
    }

    private ContentPacketExtension generateContent(
        RtpDescriptionPacketExtension description,
        IceUdpTransportPacketExtension transport)
    {
        System.out.println("lishunyang@generateContent:JitMeetConnector");
        ContentPacketExtension content = new ContentPacketExtension();

        // basic info
        content.setCreator(CreatorEnum.responder);
        content.setName(description.getMedia());
        content.setSenders(SendersEnum.both);

        // description
        content.addChildExtension(description);
        // transport
        content.addChildExtension(transport);

        return content;
    }

    private PayloadTypePacketExtension formatToPayload(MediaFormat format)
    {
        System.out.println("lishunyang@formatToPayload:JitMeetConnector");
        PayloadTypePacketExtension payloadType =
            new PayloadTypePacketExtension();
        payloadType.setId(format.getRTPPayloadType());
        payloadType.setName(format.getEncoding());

        // video 没有channel?
        if (format instanceof AudioMediaFormat)
            payloadType.setChannels(((AudioMediaFormat) format).getChannels());
        payloadType.setClockrate((int) format.getClockRate());

        for (Map.Entry<String, String> entry : format.getFormatParameters()
            .entrySet())
        {
            ParameterPacketExtension parameter = new ParameterPacketExtension();
            parameter.setName(entry.getKey());
            parameter.setValue(entry.getValue());
            payloadType.addParameter(parameter);
        }
        for (Map.Entry<String, String> entry : format.getAdvancedAttributes()
            .entrySet())
        {
            ParameterPacketExtension parameter = new ParameterPacketExtension();
            parameter.setName(entry.getKey());
            parameter.setValue(entry.getValue());
            payloadType.addParameter(parameter);
        }

        return payloadType;
    }
}
