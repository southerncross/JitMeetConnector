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
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.media.ControllerEvent;
import javax.media.ControllerListener;
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
import javax.media.rtp.ReceiveStreamListener;
import javax.media.rtp.SendStream;
import javax.media.rtp.SessionAddress;
import javax.media.rtp.SessionListener;
import javax.media.rtp.event.NewReceiveStreamEvent;
import javax.media.rtp.event.ReceiveStreamEvent;
import javax.media.rtp.event.SessionEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import net.java.sip.communicator.impl.protocol.jabber.extensions.DefaultPacketExtensionProvider;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.DtlsFingerprintPacketExtension;
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
import net.java.sip.communicator.service.protocol.media.SrtpControls;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.RemoteCandidate;
import org.jitsi.impl.neomedia.MediaStreamImpl;
import org.jitsi.impl.neomedia.format.MediaFormatFactoryImpl;
import org.jitsi.impl.neomedia.transform.dtls.DtlsControlImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.DefaultStreamConnector;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaDirection;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.MediaUseCase;
import org.jitsi.service.neomedia.RTPExtension;
import org.jitsi.service.neomedia.RTPTranslator;
import org.jitsi.service.neomedia.SSRCFactory;
import org.jitsi.service.neomedia.SrtpControl;
import org.jitsi.service.neomedia.SrtpControlType;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.service.neomedia.VideoMediaStream;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.service.neomedia.event.SrtpListener;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.util.Logger;
import org.jitsi.util.event.VideoEvent;
import org.jitsi.util.event.VideoListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.muc.MultiUserChat;

public class JitMeetConnector
{
    private Logger logger;

    private XMPPConnection connection;

    private MediaService mediaService;

    private Map<String, MediaDevice> mediaDevices;

    // The selected format for video or audio type
    private Map<String, MediaFormat> mediaFormats;

    private Map<String, MediaStream> mediaStreams;

    private Map<String, Byte> mediaDynamicPayloadTypeIds;

    private Map<String, Long> mediaRemoteSsrcs;

    private Agent iceAgent;

    // FIXME: I can't parse fingerprint section
    // of session-initiate packet, so I have to
    // fix hash function to sha-1
    private final String hashFunction = "sha-1";

    private Map<String, String> mediaRemoteFingerprints;

    // Muc conference id, it should be set manually
    private String conferenceId = "4cw6jzwdzx672e29";

    private boolean handleVideo = true;

    private final SrtpListener srtpListener = new SrtpListener()
    {
        public void securityMessageReceived(String message, String i18nMessage,
            int severity)
        {
            System.out
                .println("Lishunyang@SrtpListener:securityMessageReceived");
            // for (SrtpListener listener : getSrtpListeners())
            // {
            // listener.securityMessageReceived(
            // message, i18nMessage, severity);
            // }
        }

        public void securityNegotiationStarted(MediaType mediaType,
            SrtpControl sender)
        {
            System.out
                .println("Lishunyang@SrtpListener:securityNegotiationStarted");
            // for (SrtpListener listener : getSrtpListeners())
            // listener.securityNegotiationStarted(mediaType, sender);
        }

        public void securityTimeout(MediaType mediaType)
        {
            System.out.println("Lishunyang@SrtpListener:securityTimeout");
            // for (SrtpListener listener : getSrtpListeners())
            // listener.securityTimeout(mediaType);
        }

        public void securityTurnedOff(MediaType mediaType)
        {
            System.out.println("Lishunyang@SrtpListener:securityTurnedOff");
            // for (SrtpListener listener : getSrtpListeners())
            // listener.securityTurnedOff(mediaType);
        }

        public void securityTurnedOn(MediaType mediaType, String cipher,
            SrtpControl sender)
        {
            System.out.println("Lishunyang@SrtpListener:securityTurnedOn");
            // for (SrtpListener listener : getSrtpListeners())
            // listener.securityTurnedOn(mediaType, cipher, sender);
        }
    };

    public JitMeetConnector()
    {
        mediaDevices = new HashMap<String, MediaDevice>();
        mediaFormats = new HashMap<String, MediaFormat>();
        mediaStreams = new HashMap<String, MediaStream>();
        mediaDynamicPayloadTypeIds = new HashMap<String, Byte>();
        mediaRemoteSsrcs = new HashMap<String, Long>();
        mediaRemoteFingerprints = new HashMap<String, String>();
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
        logger = Logger.getLogger(JitMeetConnector.class);

        ConnectionConfiguration conf =
            new ConnectionConfiguration("jitmeet.example.com", 5222);
        // -Dsmack.debugEnabled=true
        // XMPPConnection.DEBUG_ENABLED = true;
        connection = new XMPPConnection(conf);

        LibJitsi.start();
        mediaService = LibJitsi.getMediaService();

        initiateDevices();

        harvestLocalCandidates();

        initiateProviders();

        initiatePacketListener();
    }

    private void uninitiate()
    {
        iceAgent.free();
        connection.disconnect();
        LibJitsi.stop();
    }

    private void initiateDevices()
    {
        if (null == mediaDevices)
        {
            mediaDevices = new HashMap<String, MediaDevice>();
        }

        for (MediaType media : MediaType.values())
        {
            MediaDevice device =
                mediaService.getDefaultDevice(media, MediaUseCase.ANY);
            mediaDevices.put(media.toString(), device);
        }
    }

    // Refer to NetworkAddressManagerServiceImpl
    private void harvestLocalCandidates()
    {
        if (null == iceAgent)
        {
            iceAgent = new Agent();
        }
        int MIN_STREAM_PORT = 7000;
        int MAX_STREAM_PORT = 9000;
        Random rand = new Random();
        int streamPort =
            Math.abs(rand.nextInt()) % (MAX_STREAM_PORT - MIN_STREAM_PORT)
                + MIN_STREAM_PORT;
        System.out
            .println("Lishunyang@JitMeetConnector:harvestLocalCandidates streamPort "
                + streamPort);

        for (MediaType media : MediaType.values())
        {
            streamPort = (streamPort & 1) == 1 ? streamPort + 1 : streamPort;
            streamPort =
                (streamPort + 1) > MAX_STREAM_PORT ? MIN_STREAM_PORT
                    : streamPort;
            try
            {
                int rtpPort = streamPort;
                int rtcpPort = rtpPort + 1;
                IceMediaStream stream =
                    iceAgent.createMediaStream(media.toString());
                iceAgent.createComponent(stream, Transport.UDP, rtpPort,
                    rtpPort, rtpPort + 100);
                iceAgent.createComponent(stream, Transport.UDP, rtcpPort,
                    rtcpPort, rtcpPort + 100);
                streamPort += 2;
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
    }

    // JingleIQ extension, media extension(wrote by myself..)
    private void initiateProviders()
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
                        parseFormatsAndPayloadTypes(jiq);
                        harvestRemoteCandidates(extractTransports(jiq));
                        acceptJingleSessionInitiate(jiq);
                        checkIceConnectivity();
                        prepareMediaStreams();
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

    // Note: We assume that a content packet only contains one transport packet
    // extension
    private Map<String, IceUdpTransportPacketExtension> extractTransports(
        JingleIQ jiq)
    {
        Map<String, IceUdpTransportPacketExtension> transports =
            new HashMap<String, IceUdpTransportPacketExtension>();

        for (ContentPacketExtension content : jiq.getContentList())
        {
            transports.put(content.getName(), content
                .getFirstChildOfType(IceUdpTransportPacketExtension.class));
        }

        return transports;
    }

    // Refer to IceUdpTransportManager
    private boolean harvestRemoteCandidates(
        Map<String, IceUdpTransportPacketExtension> transports)
    {
        if (null == iceAgent)
        {
            System.out
                .println("Lishunyang@JitMeetConnector:harvestRemoteCandidates iceAgent is null");
            return false;
        }

        int generation = iceAgent.getGeneration();

        for (Map.Entry<String, IceUdpTransportPacketExtension> e : transports
            .entrySet())
        {
            IceUdpTransportPacketExtension transport = e.getValue();
            List<CandidatePacketExtension> candidates =
                transport
                    .getChildExtensionsOfType(CandidatePacketExtension.class);

            // Sort the remote candidates (host < reflexive < relayed) in order
            // to create first the host, then the reflexive, the relayed
            // candidates and thus be able to set the relative-candidate
            // matching the rel-addr/rel-port attribute.
            Collections.sort(candidates);

            String media = e.getKey();
            IceMediaStream stream = iceAgent.getStream(media);

            // Different stream may have different ufrag/password
            String ufrag = transport.getUfrag();

            if (ufrag != null)
                stream.setRemoteUfrag(ufrag);

            String password = transport.getPassword();

            if (password != null)
                stream.setRemotePassword(password);

            for (CandidatePacketExtension candidate : candidates)
            {
                // FIXME: Don't use IPv6
                try
                {
                    if (InetAddress.getByName(candidate.getIP()) instanceof Inet6Address)
                        continue;
                }
                catch (UnknownHostException e1)
                {
                    e1.printStackTrace();
                }

                /*
                 * Is the remote candidate from the current generation of the
                 * iceAgent?
                 */
                if (candidate.getGeneration() != generation)
                    continue;

                Component component =
                    stream.getComponent(candidate.getComponent());
                String relAddr;
                int relPort;
                TransportAddress relatedAddress = null;

                if (((relAddr = candidate.getRelAddr()) != null)
                    && ((relPort = candidate.getRelPort()) != -1))
                {
                    relatedAddress =
                        new TransportAddress(relAddr, relPort,
                            Transport.parse(candidate.getProtocol()));
                }

                RemoteCandidate relatedCandidate =
                    component.findRemoteCandidate(relatedAddress);
                RemoteCandidate remoteCandidate =
                    new RemoteCandidate(new TransportAddress(candidate.getIP(),
                        candidate.getPort(), Transport.parse(candidate
                            .getProtocol())), component,
                        org.ice4j.ice.CandidateType.parse(candidate.getType()
                            .toString()), candidate.getFoundation(),
                        candidate.getPriority(), relatedCandidate);

                component.addRemoteCandidate(remoteCandidate);
            }
        }

        return true;
    }

    private boolean checkIceConnectivity()
    {
        if (null == iceAgent)
        {
            System.out
                .println("Lishunyang@JitMeetConnector:checkIceConnectivity Agent is null");
            return false;
        }

        // ICE check
        iceAgent.startConnectivityEstablishment();

        // FIXME: This is ugly, I should use callback
        while (IceProcessingState.TERMINATED != iceAgent.getState())
        {
            System.out
                .println("lishunyang@JitMeetConnector:checkIceConnectivity Check status "
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
        return true;
    }

    private void parseFormatsAndPayloadTypes(JingleIQ initiateIq)
    {
        if (null == mediaFormats)
        {
            mediaFormats = new HashMap<String, MediaFormat>();
        }
        if (null == mediaDynamicPayloadTypeIds)
        {
            mediaDynamicPayloadTypeIds = new HashMap<String, Byte>();
        }
        if (null == mediaRemoteSsrcs)
        {
            mediaRemoteSsrcs = new HashMap<String, Long>();
        }
        if (null == mediaRemoteFingerprints)
        {
            mediaRemoteFingerprints = new HashMap<String, String>();
        }

        MediaFormatFactoryImpl fmtFactory = new MediaFormatFactoryImpl();
        List<ContentPacketExtension> initiateContents =
            initiateIq.getContentList();

        for (ContentPacketExtension content : initiateContents)
        {
            // Collect fomats and dynamic payload types
            RtpDescriptionPacketExtension description =
                content
                    .getFirstChildOfType(RtpDescriptionPacketExtension.class);
            String media = description.getMedia();
            // As for Video, the media format is fixed to RED
            if (media.equalsIgnoreCase("video"))
            {
                for (PayloadTypePacketExtension pt : description.getPayloadTypes())
                {
                    // Dynamic payloadtype of RED is 116
                    if (116 == pt.getID())
                    {
                        MediaFormat format =
                            fmtFactory.createMediaFormat(pt.getName(),
                                pt.getClockrate(), pt.getChannels());
                        mediaFormats.put(media, format);
                        mediaDynamicPayloadTypeIds.put(media, (byte) pt.getID());
                        break;
                    }
                }
            }
            // Only use the first element of formats
            else
            {
                PayloadTypePacketExtension pt =
                    description.getPayloadTypes().get(0);
                MediaFormat format =
                    fmtFactory.createMediaFormat(pt.getName(),
                        pt.getClockrate(), pt.getChannels());
                mediaFormats.put(media, format);
                mediaDynamicPayloadTypeIds.put(media, (byte) pt.getID());
            }

            mediaRemoteSsrcs.put(media, Long.valueOf(description.getSsrc()));

            // Collect fingerprints
            IceUdpTransportPacketExtension transport =
                content
                    .getFirstChildOfType(IceUdpTransportPacketExtension.class);
            mediaRemoteFingerprints.put(media, transport.getText());
        }
    }

    // Create session-accept IQ and send it to peer
    private boolean acceptJingleSessionInitiate(JingleIQ initiateIq)
    {
        if (0 == mediaFormats.size())
        {
            System.out
                .println("Lishunyang@JitMeetConnector:acceptJingleSessionInitiate mediaFormats is empty");
            return false;
        }
        if (0 == mediaDynamicPayloadTypeIds.size())
        {
            System.out
                .println("Lishunyang@JitMeetConnector:acceptJingleSessionInitiate mediaDynamicPayloadTypeIds is empty");
            return false;
        }
        System.out
            .println("Lishunyang@JitMeetConnector:acceptJingleSessionInitiate");

        String peerJid = initiateIq.getFrom();
        String myJid = initiateIq.getTo();
        String sessionId = initiateIq.getSID();

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
                    formats, rtpExtensions, ptRegister, rtpExtRegister);

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
        return true;
    }

    private IceUdpTransportPacketExtension generateTransport(
        ContentPacketExtension content)
    {
        if (null == iceAgent)
        {
            System.out
                .println("Lishunyang@JitMeetConnector:generateTransport iceAgent is null");
            return null;
        }

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
                candidate.setNetwork(1);
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

        // DtlsFingerprintPacketExtension
        // Do nothing here

        return transport;
    }

    // Get the media streams ready to start
    // Before calling this method, make sure that:
    private boolean prepareMediaStreams()
    {
        if (null == iceAgent)
        {
            System.out
                .println("Lishunyang@JitMeetConnector:prepareMediaStreams Agent is null");
            return false;
        }
        if (null == mediaService)
        {
            System.out
                .println("Lishunyang@JitMeetConnector:prepareMediaStreams Service is null");
            return false;
        }
        if (0 == mediaDynamicPayloadTypeIds.size())
        {
            System.out
                .println("Lishunyang@JitMeetConnector:prepareMediaStreams mediaDynamicPayloadTypeIds is empty");
            return false;
        }
        if (0 == mediaFormats.size())
        {
            System.out
                .println("Lishunyang@JitMeetConnector:prepareMediaStreams mediaFormats is empty");
            return false;
        }
        if (0 == mediaRemoteFingerprints.size())
        {
            System.out
                .println("Lishunyang@JitMeetConnector:prepareMediaStreams mediaRemoteFingerprints is empty");
            return false;
        }
        if (0 == mediaDevices.size())
        {
            System.out
                .println("Lishunyang@JitMeetConnector:prepareMediaStreams mediaDevices is empty");
            return false;
        }
        System.out.println("Lishunyang@JitMeetConnector:prepareMediaSreams");

        for (MediaType media : MediaType.values())
        {
            // Ignore video stream
            if (MediaType.VIDEO == media && !handleVideo)
                continue;

            IceMediaStream iceStream = iceAgent.getStream(media.toString());
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
            // DTLS stuff here, am I correct?
            // DtlsControl dtlsControl = (DtlsControl)
            // mediaService.createSrtpControl(SrtpControlType.DTLS_SRTP);
            // Map<String, String> fp = new HashMap<String, String>();
            // fp.put(hashFunction,
            // mediaRemoteFingerprints.get(media.toString()));
            // dtlsControl.setRemoteFingerprints(fp);
            // dtlsControl.setSetup(DtlsControl.Setup.ACTIVE);
            // dtlsControl.start(media);
            // dtlsControl.setConnector(connector);

            MediaStream stream =
                mediaService.createMediaStream(connector,
                    mediaDevices.get(media.toString()));
            // MediaStream stream =
            // mediaService.createMediaStream(connector,
            // mediaDevices.get(media.toString()), dtlsControl);

            // Translator, is this correct?
            RTPTranslator translator = mediaService.createRTPTranslator();
            stream.setRTPTranslator(translator);

            stream.setName(media.toString());
            stream.setDirection(MediaDirection.RECVONLY);
            MediaFormat format = mediaFormats.get(media.toString());
            stream.addDynamicRTPPayloadType(
                mediaDynamicPayloadTypeIds.get(media.toString()), format);
            stream.setFormat(format);
            stream.setTarget(new MediaStreamTarget(rtpPair.getRemoteCandidate()
                .getTransportAddress(), rtcpPair.getRemoteCandidate()
                .getTransportAddress()));

            mediaStreams.put(media.toString(), stream);

        }
        return true;
    }

    private void startMediaStreams()
    {
        System.out.println("Lishunyang@JitMeetConnector:startMediaStream");
        if (mediaStreams.size() < 1)
        {
            System.out
                .println("Lishunyang@JitMeetConnector:startMediaStream mediaStreams is empty");
            return;
        }

        for (MediaType media : MediaType.values())
        {
            MediaStream stream = mediaStreams.get(media.toString());
            // Start DTLS-SRTP
            // SrtpControl control = stream.getSrtpControl();
            // control.setSrtpListener(srtpListener);
            // control.start(media);

            stream.start();

            System.out
                .println("Lishunyang@JitMeetConnector:startMediaStream local("
                    + media + ") "
                    + stream.getMediaStreamStats().getLocalIPAddress() + " "
                    + stream.getMediaStreamStats().getLocalPort());

            System.out
                .println("Lishunyang@JitMeetConnector:startMediaStream remote("
                    + media + ") "
                    + stream.getMediaStreamStats().getRemoteIPAddress() + " "
                    + stream.getMediaStreamStats().getRemotePort());
            System.out
                .println("Lishunyang@JitMeetConnector:startMediaStream encoding("
                    + media + ") " + stream.getMediaStreamStats().getEncoding());
            System.out
                .println("Lishunyang@JitMeetConnector:startMediaStream localSsrc("
                    + media + ") " + stream.getLocalSourceID());
            System.out
                .println("Lishunyang@JitMeetConnector:startMediaStream remoteSsrc("
                    + media + ") " + stream.getRemoteSourceID());
        }

        if (!handleVideo)
            return;

        final MediaStream videoStream =
            mediaStreams.get(MediaType.VIDEO.toString());
        final JPanel p = new JPanel(new BorderLayout());
        VideoMediaStream vms = (VideoMediaStream) videoStream;
        vms.addVideoListener(new VideoListener()
        {
            @Override
            public void videoAdded(VideoEvent event)
            {
                System.out.println("Lishunyang@VideoListener:videoAdded");
                videoUpdate(event);
            }

            @Override
            public void videoRemoved(VideoEvent event)
            {
                System.out.println("Lishunyang@VideoListener:videoRemoved");
                videoUpdate(event);
            }

            @Override
            public void videoUpdate(VideoEvent event)
            {
                System.out.println("Lishunyang@VideoListener:videoUpdate");
                p.removeAll();
                VideoMediaStream s = (VideoMediaStream) videoStream;
                for (java.awt.Component c : s.getVisualComponents())
                {
                    p.add(c);
                    break;
                }
                p.revalidate();
            }
        });
        final JFrame f = new JFrame("test");
        f.getContentPane().add(p);
        f.pack();
        f.setResizable(false);
        f.setVisible(true);
        f.toFront();
        p.addContainerListener(new ContainerListener()
        {

            @Override
            public void componentAdded(ContainerEvent e)
            {
                System.out
                    .println("Lishunyang@ContainerListener:componentAdded");
                f.pack();
            }

            @Override
            public void componentRemoved(ContainerEvent e)
            {
                System.out.println("Lishunyang@VideoListener:componentRemoved");
                f.pack();
            }
        });
        //
        // while (true)
        // {
        // System.out.println("Lishunyang@JitMeetConnector:startMediaStream "
        // + videoStream.getMediaStreamStats()
        // .getDownloadRateKiloBitPerSec());
        // try
        // {
        // Thread.sleep(1000);
        // }
        // catch (InterruptedException e1)
        // {
        // e1.printStackTrace();
        // }
        // }

    }

}