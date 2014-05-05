package test;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.media.DataSink;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NoDataSinkException;
import javax.media.NoDataSourceException;
import javax.media.NoProcessorException;
import javax.media.Processor;
import javax.media.control.TrackControl;
import javax.media.format.UnsupportedFormatException;
import javax.media.format.VideoFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.SourceCloneable;
import javax.media.rtp.InvalidSessionAddressException;
import javax.media.rtp.RTPConnector;
import javax.media.rtp.RTPManager;
import javax.media.rtp.SendStream;
import javax.media.rtp.SessionAddress;
import javax.swing.JFrame;
import javax.swing.JPanel;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.jinglesdp.JingleUtils;
import net.java.sip.communicator.service.protocol.media.DynamicPayloadTypeRegistry;
import net.java.sip.communicator.service.protocol.media.DynamicRTPExtensionsRegistry;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.RemoteCandidate;
import org.jitsi.impl.neomedia.format.MediaFormatFactoryImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.DefaultStreamConnector;
import org.jitsi.service.neomedia.MediaDirection;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.RTPExtension;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.service.neomedia.VideoMediaStream;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.util.event.VideoEvent;
import org.jitsi.util.event.VideoListener;
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

    private MediaService mediaService;

    // private Map<String, MediaDevice> mediaDevices;

    private Map<String, MediaFormat> mediaFormats;

    private Map<String, MediaStream> mediaStreams;

    private Map<String, Byte> mediaDynamicPayloadTypeIds;

    private Agent iceAgent;

    // Muc conference id, it should be set manually
    private String conferenceId = "wqlii8abm2easjor";

    public JitMeetConnector()
    {
        // mediaDevices = new HashMap<String, MediaDevice>();
        mediaFormats = new HashMap<String, MediaFormat>();
        mediaStreams = new HashMap<String, MediaStream>();
        mediaDynamicPayloadTypeIds = new HashMap<String, Byte>();
    }

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
        // -Dsmack.debugEnabled=true
        // XMPPConnection.DEBUG_ENABLED = true;
        connection = new XMPPConnection(conf);

        // Start libjitsi service
        LibJitsi.start();

        // Initiate mediaService
        mediaService = LibJitsi.getMediaService();

        // Initiate iceAgent
        initiateAgent();

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

    // TODO: Is there any code of Jitsi that I can reuse to simplify this
    // method?
    private void initiateAgent()
    {
        iceAgent = new Agent();
        int MIN_STREAM_PORT = 7000;
        int MAX_STREAM_PORT = 9000;
        Random rand = new Random();
        int streamPort =
            rand.nextInt() % (MAX_STREAM_PORT - MIN_STREAM_PORT)
                + MIN_STREAM_PORT;

        streamPort = (streamPort & 1) == 1 ? streamPort + 1 : streamPort;
        streamPort =
            (streamPort + 1) > MAX_STREAM_PORT ? MIN_STREAM_PORT : streamPort;
        try
        {
            int rtpPort = streamPort;
            IceMediaStream stream = iceAgent.createMediaStream("audio");
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

        try
        {
            int rtpPort = streamPort;
            IceMediaStream stream = iceAgent.createMediaStream("video");
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

    // JingleIQ extension, media extension(wrote by myself..)
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
                        iceConnectivityEstablish(jiq);
                        parseFormatsAndPayloadTypes(jiq);
                        prepareMediaStreams();
                        acceptJingleSessionInitiate(jiq);
                        startMediaStreams();
                    }
                }
                else
                {
                    // Display all other received packets
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

    // Add remote candidates to ice agent and then start check
    // Before calling this method, make sure that:
    // 1. initiateAgent has been done
    private void iceConnectivityEstablish(JingleIQ jiq)
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

                        // Don't use ipv6 address
                        if (inetAddr instanceof Inet6Address)
                            continue;

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

        // Wait something ready
        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        
        // ICE check
        iceAgent.startConnectivityEstablishment();

        // FIXME: This is ugly, I should use callback
        while (IceProcessingState.TERMINATED != iceAgent.getState())
        {
            System.out
                .println("lishunyang@initiatePacketListener:JitMeetConnector "
                    + iceAgent.getState());
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

    }

    // Before calling this method, make sure that:
    // 1. initiateDevices has been done
    private void parseFormatsAndPayloadTypes(JingleIQ initiateIq)
    {
        MediaFormatFactoryImpl fmtFactory = new MediaFormatFactoryImpl();
        List<ContentPacketExtension> initiateContents =
            initiateIq.getContentList();
        // Audio content & Video content
        for (ContentPacketExtension content : initiateContents)
        {

            RtpDescriptionPacketExtension description =
                content
                    .getFirstChildOfType(RtpDescriptionPacketExtension.class);
            String media = description.getMedia();
            // Only use the first element of formats
            PayloadTypePacketExtension pt =
                description.getPayloadTypes().get(0);
            MediaFormat format =
                fmtFactory.createMediaFormat(pt.getName(), pt.getClockrate(),
                    pt.getChannels());
            mediaFormats.put(media, format);
            mediaDynamicPayloadTypeIds.put(media, (byte) pt.getID());
        }
    }

    // Create session-accept IQ and send it to peer
    // Before calling this method, make sure that:
    // 1. XMPP connection has been built
    private void acceptJingleSessionInitiate(JingleIQ initiateIq)
    {
        System.out.println("Lishunyang@JitMeetConnector:acceptJingleSessionInitiate");
        String peerJid = initiateIq.getFrom();
        String myJid = initiateIq.getTo();
        String sessionId = initiateIq.getSID();

        // Construct content list which includes an audio content and a video
        // content
        List<ContentPacketExtension> acceptContents =
            new ArrayList<ContentPacketExtension>();
        List<ContentPacketExtension> initiateContents =
            initiateIq.getContentList();
        // Audio content & Video content
        for (ContentPacketExtension content : initiateContents)
        {
            String contentName = content.getName();
            MediaFormat format = mediaFormats.get(contentName);
            byte ptId = mediaDynamicPayloadTypeIds.get(contentName);
            List<MediaFormat> formats = new ArrayList<MediaFormat>();
            formats.add(format);
            DynamicPayloadTypeRegistry ptRegister =
                new DynamicPayloadTypeRegistry();
            ptRegister.addMapping(format, ptId);
            DynamicRTPExtensionsRegistry rtpExtRegister =
                new DynamicRTPExtensionsRegistry();
            // TODO: What should "rtpExtensions" be?
            List<RTPExtension> rtpExtensions = null;

            ContentPacketExtension acceptContent =
                JingleUtils.createDescription(CreatorEnum.responder,
                    contentName,
                    JingleUtils.getSenders(MediaDirection.RECVONLY, false),
                    formats, rtpExtensions, ptRegister,
                    rtpExtRegister);

            // Construct new transport part
            IceUdpTransportPacketExtension transport =
                generateTransport(content);
            acceptContent.addChildExtension(transport);

            acceptContents.add(acceptContent);
        }

        JingleIQ acceptIq =
            JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId,
                acceptContents);
        connection.sendPacket(acceptIq);
    }

    // TODO: Is there any code of Jitsi that I can reuse to simplify this
    // method?
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

    // Get the media streams ready to start
    // Before calling this method, make sure that:
    // 1. initiateMediaStreams has been done
    // 2. getPayloadTypes has been done
    private void prepareMediaStreams()
    {
        System.out.println("Lishunyang@JitMeetConnector: prepareMediaSreams");
        // TODO: Now I only handle the video things
        // TODO: I think that there should be some existed method of Jitsi to
        // reuse to simplify this part
        IceMediaStream iceStream =
            iceAgent.getStream(MediaType.VIDEO.toString());
        Component rtpComponent =
            iceStream.getComponent(org.ice4j.ice.Component.RTP);
        Component rtcpComponent =
            iceStream.getComponent(org.ice4j.ice.Component.RTCP);
        CandidatePair rtpPair = rtpComponent.getSelectedPair();
        CandidatePair rtcpPair = rtcpComponent.getSelectedPair();
        StreamConnector connector =
            new DefaultStreamConnector(rtpPair.getLocalCandidate()
                .getDatagramSocket(), rtcpPair.getLocalCandidate()
                .getDatagramSocket());

        MediaStream stream =
            mediaService.createMediaStream(connector, MediaType.VIDEO);
        stream.setName(MediaType.VIDEO.toString());
        stream.setDirection(MediaDirection.RECVONLY);
        MediaFormat format = mediaFormats.get(MediaType.VIDEO.toString());
        byte payloadTypeId =
            mediaDynamicPayloadTypeIds.get(MediaType.VIDEO.toString());
        // TODO: check the result
        stream.addDynamicRTPPayloadType(payloadTypeId, format);
        stream.setFormat(format);

        // stream.setConnector(connector);
        stream.setTarget(new MediaStreamTarget(rtpPair.getRemoteCandidate()
            .getTransportAddress(), rtcpPair.getRemoteCandidate()
            .getTransportAddress()));
        
        mediaStreams.put(MediaType.VIDEO.toString(), stream);
    }

    // TODO: Now, it only handle the video things
    private void startMediaStreams()
    {
        System.out.println("lishunyang@JitMeetConnector:startMediaStream");
        String media = "video";
        final MediaStream stream = mediaStreams.get(media);

        stream.start();

        // MediaStreamStats stats = mediaStream.getMediaStreamStats();
        System.out
            .println("Lishunyang@startMediaStream:JitMeetConnector status "
                + stream.getMediaStreamStats().getLocalIPAddress() + " "
                + stream.getMediaStreamStats().getLocalPort());

        System.out
            .println("Lishunyang@startMediaStream:JitMeetConnector status "
                + stream.getMediaStreamStats().getRemoteIPAddress() + " "
                + stream.getMediaStreamStats().getRemotePort());
        
//         final JPanel p = new JPanel(new BorderLayout());
//         VideoMediaStream vms = (VideoMediaStream) stream;
//         vms.addVideoListener(new VideoListener()
//         {
//        
//         @Override
//         public void videoAdded(VideoEvent event)
//         {
//         videoUpdate(event);
//         }
//        
//         @Override
//         public void videoRemoved(VideoEvent event)
//         {
//         videoUpdate(event);
//         }
//        
//         @Override
//         public void videoUpdate(VideoEvent event)
//         {
//         p.removeAll();
//         VideoMediaStream s = (VideoMediaStream) stream;
//         for (java.awt.Component c : s.getVisualComponents())
//         {
//         p.add(c);
//         break;
//         }
//         p.revalidate();
//         }
//         });
//         final JFrame f = new JFrame("test");
//         f.getContentPane().add(p);
//         f.pack();
//         f.setResizable(false);
//         f.setVisible(true);
//         f.toFront();
//         p.addContainerListener(new ContainerListener()
//         {
//        
//         @Override
//         public void componentAdded(ContainerEvent e)
//         {
//         f.pack();
//         }
//        
//         @Override
//         public void componentRemoved(ContainerEvent e)
//         {
//         f.pack();
//         }
//         });

    }
}