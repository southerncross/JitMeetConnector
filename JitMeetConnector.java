package test;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import org.ice4j.ice.RemoteCandidate;
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
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.muc.MultiUserChat;

public class JitMeetConnector
{
    private XMPPConnection connection;

    private Map<String, MediaDevice> devices;

    private Agent iceAgent;

    // Muc conference id, it should be set manually
    private String conferenceId = "b1xq9zd9p69lik9";

    public static void main(String[] args)
    {
        JitMeetConnector connector;

        connector = new JitMeetConnector();
        connector.run();
    }

    public void run()
    {
        try
        {
            initiate();

            // Login anonymously, get temporary account
            connection.connect();
            connection.loginAnonymously();

            // Display connection information
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            System.out.println("ConnectionID: " + connection.getConnectionID());
            System.out.println("Host: " + connection.getHost());
            System.out.println("Port: " + connection.getPort());
            System.out.println("ServiceName: " + connection.getServiceName());
            System.out.println("User: " + connection.getUser());
            System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

            // Join muc conference
            MultiUserChat muc =
                new MultiUserChat(connection, conferenceId
                    + "@conference.example.com");
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
            e.printStackTrace();
        }
        finally
        {
            uninitiate();
        }
    }

    public void initiate()
    {
        // Create XMPP connection
        ConnectionConfiguration conf =
            new ConnectionConfiguration("jitmeet.example.com", 5222);
        XMPPConnection.DEBUG_ENABLED = true;
        connection = new XMPPConnection(conf);

        // Start libjitsi service
        LibJitsi.start();

        // Initiate iceAgent
        initiateAgent();

        // Initiate devices
        initiateDevices();

        // Initiate packet extension
        initiatePacketExtension();

        // Initiate packet listener
        initiatePacketListener();
    }

    private void uninitiate()
    {
        iceAgent.free();
        connection.disconnect();
        LibJitsi.stop();
    }

    private void initiateAgent()
    {
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
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
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
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    // Audio & video devices
    private void initiateDevices()
    {
        devices = new HashMap<String, MediaDevice>();
        MediaService service = LibJitsi.getMediaService();
        devices.put(MediaType.AUDIO.toString(),
            service.getDefaultDevice(MediaType.AUDIO, MediaUseCase.CALL));
        devices.put(MediaType.VIDEO.toString(),
            service.getDefaultDevice(MediaType.VIDEO, MediaUseCase.CALL));

    }

    // JingleIQ extension, media extension(it doesn't belong to jitsi)
    private void initiatePacketExtension()
    {
        // Create IQ handler
        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        // I didn't find similar packet extension/provider to parse media
        // information(http://estos.de/ns/mjs), so I just wrote one
        providerManager.addExtensionProvider("media", "http://estos.de/ns/mjs",
            new MediaExtensionProvider());
    }

    private void initiatePacketListener()
    {
        connection.addPacketSendingListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                // Display all send packets
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

        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                // Display all receive packets
                System.out.println("<---: " + packet.toXML());

                if (JingleIQ.class == packet.getClass())
                {
                    JingleIQ jiq = (JingleIQ) packet;
                    System.out.println("<---: " + packet.toXML());

                    // Send a result IQ whenever meet a set IQ
                    if (IQ.Type.SET == jiq.getType())
                        connection.sendPacket(IQ.createResultIQ(jiq));

                    // SESSION_INITIATE
                    if (JingleAction.SESSION_INITIATE == jiq.getAction())
                    {
                        addRemoteCandidates(jiq);
                        acceptJingleSessionInitiate(jiq);
                        iceAgent.startConnectivityEstablishment();
                    }
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

    // Add remote candidates to ice agent
    private void addRemoteCandidates(JingleIQ jiq)
    {
        // Audio content & Video content
        for (ContentPacketExtension content : jiq.getContentList())
        {
            IceMediaStream stream = iceAgent.getStream(content.getName());
            if (null != stream)
            {
                // ICE-UDP transport
                for (IceUdpTransportPacketExtension transport : content
                    .getChildExtensionsOfType(IceUdpTransportPacketExtension.class))
                {
                    if (null != transport.getPassword())
                        stream.setRemotePassword(transport.getPassword());
                    if (null != transport.getUfrag())
                        stream.setRemoteUfrag(transport.getUfrag());

                    // Harvest remote candidates of this JingleIQ
                    List<CandidatePacketExtension> candidates =
                        transport
                            .getChildExtensionsOfType(CandidatePacketExtension.class);
                    if (null == candidates || 0 == candidates.size())
                        continue;
                    // Collections.sort(candidates); // It seems useless

                    // Add remote candidates
                    for (CandidatePacketExtension candidate : candidates)
                    {
                        if (candidate.getGeneration() != iceAgent
                            .getGeneration())
                            continue;
                        InetAddress inetAddr = null;
                        try
                        {
                            inetAddr = InetAddress.getByName(candidate.getIP());
                        }
                        catch (UnknownHostException e)
                        {
                            e.printStackTrace();
                        }

                        // Type of candidate is hot, so there is no relayed
                        // address?
                        TransportAddress relAddr = null;
                        if (null != candidate.getRelAddr()
                            && -1 != candidate.getRelPort())
                            relAddr =
                                new TransportAddress(candidate.getRelAddr(),
                                    candidate.getRelPort(),
                                    Transport.parse(candidate.getProtocol()
                                        .toLowerCase()));

                        // Either RTP or RTCP component
                        Component component =
                            stream.getComponent(candidate.getComponent());
                        if (null != component)
                        {
                            // Related candidate, relayed candidate?
                            RemoteCandidate relatedCandidate =
                                null != relAddr ? component
                                    .findRemoteCandidate(relAddr) : null;
                            TransportAddress transAddr =
                                new TransportAddress(inetAddr,
                                    candidate.getPort(),
                                    Transport.parse(candidate.getProtocol()
                                        .toLowerCase()));
                            // Seems like all types are host here
                            org.ice4j.ice.CandidateType type =
                                org.ice4j.ice.CandidateType.parse(candidate
                                    .getType().toString());
                            RemoteCandidate remoteCandidate =
                                new RemoteCandidate(transAddr, component, type,
                                    candidate.getFoundation(),
                                    candidate.getPriority(), relatedCandidate);
                            component.addRemoteCandidate(remoteCandidate);
                        }
                    }
                }
            }
        }
    }

    // Create session-accept IQ and send it to peer
    private void acceptJingleSessionInitiate(JingleIQ initiateIQ)
    {
        String peerJid = initiateIQ.getFrom();
        String myJid = initiateIQ.getTo();
        String sessionId = initiateIQ.getSID();

        // Construct content list
        List<ContentPacketExtension> acceptContents =
            new ArrayList<ContentPacketExtension>();
        List<ContentPacketExtension> initiateContents =
            initiateIQ.getContentList();
        // Audio content & Video content
        for (ContentPacketExtension initiateContent : initiateContents)
        {
            // Description
            RtpDescriptionPacketExtension description =
                generateDescription(initiateContent);

            // Transport
            IceUdpTransportPacketExtension transport =
                generateTransport(initiateContent);

            // Content
            ContentPacketExtension content =
                generateContent(description, transport);

            acceptContents.add(content);
        }

        JingleIQ acceptIQ =
            JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId,
                acceptContents);
        connection.sendPacket(acceptIQ);
    }

    private RtpDescriptionPacketExtension generateDescription(
        ContentPacketExtension content)
    {
        RtpDescriptionPacketExtension description =
            new RtpDescriptionPacketExtension();

        // Basic info
        description.setMedia(content.getName());

        // TODO Add ssrc

        // Payload-type
        List<RtpDescriptionPacketExtension> descriptions =
            content
                .getChildExtensionsOfType(RtpDescriptionPacketExtension.class);
        // Only one description actually
        for (RtpDescriptionPacketExtension des : descriptions)
        {
            MediaDevice device = devices.get(des.getMedia());
            // Though there may be many payload-type, take the first suitable
            // one
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
        IceUdpTransportPacketExtension transport =
            new IceUdpTransportPacketExtension();

        // Basic info
        transport.setPassword(iceAgent.getLocalPassword());
        transport.setUfrag(iceAgent.getLocalUfrag());

        // Candidates
        int id = 0;
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
                candidate.setNetwork(1); // TODO Don't really understand here,
                                         // just write 1
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
        ContentPacketExtension content = new ContentPacketExtension();

        // Basic info
        content.setCreator(CreatorEnum.responder);
        content.setName(description.getMedia());
        content.setSenders(SendersEnum.both);

        // Description
        content.addChildExtension(description);
        // Transport
        content.addChildExtension(transport);

        return content;
    }

    // Make payload packet extension from media format
    private PayloadTypePacketExtension formatToPayload(MediaFormat format)
    {
        PayloadTypePacketExtension payloadType =
            new PayloadTypePacketExtension();
        payloadType.setId(format.getRTPPayloadType());
        payloadType.setName(format.getEncoding());

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
