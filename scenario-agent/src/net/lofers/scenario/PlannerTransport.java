package net.lofers.scenario;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.lofers.scenario.protocol.NpcControl.*;

/** One in-flight request, one replaceable pending sample, bounded authenticated-local IPC. */
final class PlannerTransport implements Runnable {
    static final int MAX_FRAME=256*1024;
    record Sample(long request,long capturedAt,Map<Object,Object> observations) {}
    record Reply(long request,long revision,Map<Object,Object> plans) {}
    final AtomicReference<Sample> pending=new AtomicReference<>();
    final AtomicReference<Reply> ready=new AtomicReference<>();
    final String world,epoch,registryHash;final Path socket;
    volatile String health="connecting";
    PlannerTransport(Path socket,String world,String epoch,String registryHash) {this.socket=socket;this.world=world;this.epoch=epoch;this.registryHash=registryHash;}
    Envelope.Builder envelope(long id) {return Envelope.newBuilder().setProtocolVersion(1).setWorld(world).setServerEpoch(epoch).setRequestId(id);}
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            try(SocketChannel channel=SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.configureBlocking(false);channel.connect(UnixDomainSocketAddress.of(socket));
                long deadline=System.nanoTime()+1_000_000_000L;
                while(!channel.finishConnect()){if(System.nanoTime()>deadline)throw new IOException("Connect timeout");LockSupport.parkNanos(1_000_000);}
                write(channel,envelope(0).setHello(Handshake.newBuilder().setBuild("42.20.4/b0bbce05d5")
                    .setModVersion("0.2.0").setRegistryHash(registryHash).setMaxFrameBytes(MAX_FRAME).setMaxResidents(128)).build());
                Envelope hello=read(channel);validate(hello,0);if(!hello.hasHello())throw new IOException("Handshake required");
                if(hello.getHello().getMaxFrameBytes()>MAX_FRAME)throw new IOException("Unsupported frame limit");
                health="ready";
                while(!Thread.currentThread().isInterrupted()) {
                    Sample sample=pending.getAndSet(null);
                    if(sample==null){LockSupport.parkNanos(20_000_000);continue;}
                    if(System.nanoTime()-sample.capturedAt>5_000_000_000L)continue;
                    ObservationBatch obs=(ObservationBatch)ProtocolCodec.encode("ObservationBatch",sample.observations);
                    write(channel,envelope(sample.request).setObservations(obs).build());
                    Envelope response=read(channel);validate(response,sample.request);
                    if(!response.hasPlans()||response.getPlans().getObservationRevision()!=obs.getRevision())throw new IOException("Mismatched plan revision");
                    Map<Object,Object> plans=ProtocolCodec.decode("PlanBatch",response.getPlans());
                    ready.set(new Reply(sample.request,obs.getRevision(),plans));health="ready";
                }
            } catch(Exception failure) {health="planner_unavailable";ready.set(null);LockSupport.parkNanos(1_000_000_000L);}
        }
    }
    void validate(Envelope e,long request) throws IOException {
        if(e.getProtocolVersion()!=1||!world.equals(e.getWorld())||!epoch.equals(e.getServerEpoch())||e.getRequestId()!=request)
            throw new IOException("Protocol/world/epoch/request mismatch");
    }
    static void write(SocketChannel channel,Envelope e) throws IOException {
        byte[] bytes=e.toByteArray();if(bytes.length==0||bytes.length>MAX_FRAME)throw new IOException("Frame limit");
        ByteBuffer b=ByteBuffer.allocate(bytes.length+4).order(ByteOrder.BIG_ENDIAN).putInt(bytes.length).put(bytes);b.flip();transfer(channel,b,true);
    }
    static Envelope read(SocketChannel channel) throws IOException {
        ByteBuffer header=ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);transfer(channel,header,false);header.flip();int size=header.getInt();
        if(size<=0||size>MAX_FRAME)throw new IOException("Frame limit");
        ByteBuffer body=ByteBuffer.allocate(size);transfer(channel,body,false);
        var input=com.google.protobuf.CodedInputStream.newInstance(body.array());input.setRecursionLimit(12);input.setSizeLimit(MAX_FRAME);
        Envelope result=Envelope.parseFrom(input);if(!input.isAtEnd())throw new IOException("Trailing frame data");return result;
    }
    static void transfer(SocketChannel c,ByteBuffer b,boolean output) throws IOException {
        long until=System.nanoTime()+2_000_000_000L;
        while(b.hasRemaining()) {
            int n=output?c.write(b):c.read(b);if(n<0)throw new EOFException();
            if(System.nanoTime()>until)throw new IOException("IPC timeout");if(n==0)LockSupport.parkNanos(1_000_000);
        }
    }
}
