package com.gg.game.session;

import com.gg.common.Constants;
import com.gg.common.GGLogger;
import com.gg.common.JsonHelper;
import com.gg.core.actor.ActorAgent;
import com.gg.core.actor.ActorBase;
import com.gg.core.actor.ActorRef;
import com.gg.core.actor.ActorSystem;
import com.gg.core.net.IMsgDispatch;
import com.gg.core.net.NetPBCallback;
import com.gg.core.net.NetPBHelper;
import com.gg.core.net.codec.Net;
import com.gg.game.GGGameApp;
import com.gg.game.agent.UserAgent;
import com.gg.game.proto.PSessionManager;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.google.protobuf.util.JsonFormat;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by hunter on 8/20/16.
 */
public class SessionManager extends ActorBase implements ISessionManager {
    private static final GGLogger logger = GGLogger.getLogger(SessionManager.class);

    private static final class Holder {
        private static final ISessionManager Instance;

        static {
            ActorSystem system = GGGameApp.getActorSystem();
            SessionManager sessionManager = new SessionManager(system);
            ActorRef sessionManagerRef = system.actor("SessionManager", sessionManager);
            Instance = ActorAgent.getAgent(ISessionManager.class, system.getSystemActor(), sessionManagerRef);
        }
    }

    public static final ISessionManager getInstance() {
        return Holder.Instance;
    }

    private static final String InstanceName = "ISessionManager";
    private static final String MethodName = "connect";

    private InnerSessionManager sessionManager;

    private Map<String, Session> sessionMap = new HashMap<>();

    private int ID = 0;

    private SessionManager(ActorSystem system) {
        super(system);
        sessionManager = new InnerSessionManager();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Net.Request request, RpcCallback callback) {
        logger.info("SessionManager process: {}.", JsonHelper.toJson(request));
        if (!InstanceName.equals(request.getInstance()) || !MethodName.equals(request.getMethod())) {
            logger.warn("wrong request, must send {}.{} first.", InstanceName, MethodName);
            NetPBCallback.Helper.disconnect(ctx.channel());
            return;
        }

        // parse ConnectRequest
        PSessionManager.ConnectRequest.Builder builder = PSessionManager.ConnectRequest.newBuilder();
        NetPBHelper.parseJson(request.getPayload(), builder);
        PSessionManager.ConnectRequest connectRequest = builder.build();

        // TODO ... auth

        String sid = "SESSION:" + (++ID);

        // add useragent dispatch
        String key = "useragent:" + connectRequest.getUsername();
        ActorRef userAgentRef = null;
        if (system.exist(key)) {
            userAgentRef = system.actor(key);
        } else {
            UserAgent userAgent = new UserAgent(key, system);
            userAgentRef = system.actor(key, userAgent);
        }
        Attribute<IMsgDispatch> attr = ctx.channel().attr(Constants.Net.DispatchKey);
        IMsgDispatch userDispatch = ActorAgent.getAgent(IMsgDispatch.class, this, userAgentRef);
        attr.set(userDispatch);

        // sessionManager.connect(null, connectRequest, callback);

        Session session = new Session(sid, key, ctx.channel());
        sessionMap.put(sid, session);

        PSessionManager.ConnectResponse resp =
                PSessionManager.ConnectResponse.newBuilder().setCode(1).setMsg("success").setSid(sid).build();
        callback.run(resp);
    }

    /**
     * 向客戶端推送消息
     */
    @Override
    public void push(String key, MessageOrBuilder obj) {
        for (Session s : sessionMap.values()) {
            if (s.key.equals(key)) {
                try {
                    Net.NetMessage msg = buildNetMessage(0, Net.MessageType.POST, obj);
                    s.channel.writeAndFlush(msg);
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    private Net.Response buildResponse(MessageOrBuilder obj) throws InvalidProtocolBufferException {
        String json = JsonFormat.printer().print(obj);
        Net.Response.Builder builder = Net.Response.newBuilder();
        builder.setName(obj.getClass().getSimpleName());
        builder.setResult(json);
        return builder.build();
    }

    private Net.NetMessage buildNetMessage(int index, Net.MessageType type, MessageOrBuilder obj)
            throws InvalidProtocolBufferException {
        Net.Response resp = buildResponse(obj);
        String json = JsonFormat.printer().print(resp);
        Net.NetMessage msg = Net.NetMessage.newBuilder().setIndex(index).setType(type).setPayload(json).build();
        return msg;
    }

    public void pushAll(MessageOrBuilder obj) {
        try {
            Net.NetMessage msg = buildNetMessage(0, Net.MessageType.POST, obj);
            for (Session s : sessionMap.values()) {
                s.channel.writeAndFlush(msg);
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    private static final class InnerSessionManager extends PSessionManager.ISessionManager {
        @Override
        public void connect(RpcController controller, PSessionManager.ConnectRequest request,
                RpcCallback<PSessionManager.ConnectResponse> done) {
            // PSessionManager.ConnectResponse resp =
            // PSessionManager.ConnectResponse.newBuilder().setCode(1).setMsg("success").setSid("testsid").build();
            // done.run(resp);
        }
    }

    public static final class Session {
        private String id;
        private String key;
        private Channel channel;

        public Session(String id, String key, Channel channel) {
            this.id = id;
            this.key = key;
            this.channel = channel;
        }
    }
}
