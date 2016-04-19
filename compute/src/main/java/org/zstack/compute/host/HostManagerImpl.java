package org.zstack.compute.host;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.CoreGlobalProperty;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.*;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.DbEntityLister;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.core.defer.Deferred;
import org.zstack.core.thread.AsyncThread;
import org.zstack.core.thread.SyncThread;
import org.zstack.core.workflow.*;
import org.zstack.header.AbstractService;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.cluster.ClusterVO_;
import org.zstack.header.core.Completion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.host.*;
import org.zstack.header.managementnode.ManagementNodeChangeListener;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.header.message.NeedReplyMessage;
import org.zstack.search.GetQuery;
import org.zstack.search.SearchQuery;
import org.zstack.tag.TagManager;
import org.zstack.utils.*;
import org.zstack.utils.function.ForEachFunction;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import java.util.*;

public class HostManagerImpl extends AbstractService implements HostManager, ManagementNodeChangeListener, ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(HostManagerImpl.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private DbEntityLister dl;
    @Autowired
    private ResourceDestinationMaker destMaker;
    @Autowired
    private ErrorFacade errf;
    @Autowired
    private HostExtensionPointEmitter extEmitter;
    @Autowired
    protected HostTracker tracker;
    @Autowired
    private TagManager tagMgr;

    private Map<String, HypervisorFactory> hypervisorFactories = Collections.synchronizedMap(new HashMap<String, HypervisorFactory>());
    private Map<String, HostMessageHandlerExtensionPoint> msgHandlers = Collections.synchronizedMap(new HashMap<String, HostMessageHandlerExtensionPoint>());
    private static final Set<Class> allowedMessageAfterSoftDeletion = new HashSet<Class>();

    static {
        allowedMessageAfterSoftDeletion.add(HostDeletionMsg.class);
    }

    private void handleApiMessage(APIMessage msg) {
        if (msg instanceof APIAddHostMsg) {
            handle((APIAddHostMsg) msg);
        } else if (msg instanceof APIListHostMsg) {
            handle((APIListHostMsg) msg);
        } else if (msg instanceof APISearchHostMsg) {
            handle((APISearchHostMsg) msg);
        } else if (msg instanceof APIGetHostMsg) {
            handle((APIGetHostMsg) msg);
        } else if (msg instanceof APIGetHypervisorTypesMsg) {
            handle((APIGetHypervisorTypesMsg) msg);
        } else if (msg instanceof HostMessage) {
            HostMessage hmsg = (HostMessage) msg;
            passThrough(hmsg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handle(APIGetHypervisorTypesMsg msg) {
        APIGetHypervisorTypesReply reply = new APIGetHypervisorTypesReply();
        List<String> res = new ArrayList<String>();
        res.addAll(HypervisorType.getAllTypeNames());
        reply.setHypervisorTypes(res);
        bus.reply(msg, reply);
    }

    private void handle(APIGetHostMsg msg) {
        GetQuery q = new GetQuery();
        String res = q.getAsString(msg, HostInventory.class);
        APIGetHostReply reply = new APIGetHostReply();
        reply.setInventory(res);
        bus.reply(msg, reply);
    }

    private void handle(APISearchHostMsg msg) {
        SearchQuery<HostInventory> query = SearchQuery.create(msg, HostInventory.class);
        String content = query.listAsString();
        APISearchHostReply reply = new APISearchHostReply();
        reply.setContent(content);
        bus.reply(msg, reply);
    }

    private void handle(APIListHostMsg msg) {
        List<HostVO> vos = dl.listByApiMessage(msg, HostVO.class);
        List<HostInventory> invs = HostInventory.valueOf(vos);
        APIListHostReply reply = new APIListHostReply();
        reply.setInventories(invs);
        bus.reply(msg, reply);
    }

    private void passThrough(HostMessage msg) {
        HostVO vo = dbf.findByUuid(msg.getHostUuid(), HostVO.class);
        if (vo == null && allowedMessageAfterSoftDeletion.contains(msg.getClass())) {
            HostEO eo = dbf.findByUuid(msg.getHostUuid(), HostEO.class);
            vo = ObjectUtils.newAndCopy(eo, HostVO.class);
        }

        if (vo == null) {
            String err = "Cannot find host: " + msg.getHostUuid() + ", it may have been deleted";
            bus.replyErrorByMessageType((Message) msg, err);
            return;
        }

        HypervisorFactory factory = this.getHypervisorFactory(HypervisorType.valueOf(vo.getHypervisorType()));
        Host host = factory.getHost(vo);
        host.handleMessage((Message) msg);
    }

    @Override
    @MessageSafe
    public void handleMessage(Message msg) {
        if (msg instanceof APIMessage) {
            handleApiMessage((APIMessage) msg);
        } else {
            handleLocalMessage(msg);
        }
    }

    private void handleLocalMessage(Message msg) {
        if (msg instanceof HostMessage) {
            passThrough((HostMessage) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    @Deferred
    private void handle(final APIAddHostMsg msg) {
        final APIAddHostEvent evt = new APIAddHostEvent(msg.getId());

        final ClusterVO cluster = findClusterByUuid(msg.getClusterUuid());
        final HostVO hvo = new HostVO();
        if (msg.getResourceUuid() != null) {
            hvo.setUuid(msg.getResourceUuid());
        } else {
            hvo.setUuid(Platform.getUuid());
        }
        hvo.setClusterUuid(cluster.getUuid());
        hvo.setZoneUuid(cluster.getZoneUuid());
        hvo.setName(msg.getName());
        hvo.setDescription(msg.getDescription());
        hvo.setHypervisorType(cluster.getHypervisorType());
        hvo.setManagementIp(msg.getManagementIp());
        hvo.setStatus(HostStatus.Connecting);
        hvo.setState(HostState.Enabled);

        final HypervisorFactory factory = getHypervisorFactory(HypervisorType.valueOf(cluster.getHypervisorType()));
        final HostVO vo = factory.createHost(hvo, msg);

        tagMgr.createTagsFromAPICreateMessage(msg, vo.getUuid(), HostVO.class.getSimpleName());

        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        final HostInventory inv = HostInventory.valueOf(vo);
        chain.setName(String.format("add-host-%s", vo.getUuid()));
        chain.then(new NoRollbackFlow() {
            String __name__ = "call-before-add-host-extension";

            @Override
            public void run(final FlowTrigger trigger, Map data) {
                extEmitter.beforeAddHost(inv, new Completion(trigger) {
                    @Override
                    public void success() {
                        trigger.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        trigger.fail(errorCode);
                    }
                });
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "send-connect-host-message";

            @Override
            public void run(final FlowTrigger trigger, Map data) {
                ConnectHostMsg connectMsg = new ConnectHostMsg(vo.getUuid());
                connectMsg.setNewAdd(true);
                connectMsg.setStartPingTaskOnFailure(false);
                bus.makeTargetServiceIdByResourceUuid(connectMsg, HostConstant.SERVICE_ID, hvo.getUuid());
                bus.send(connectMsg, new CloudBusCallBack(trigger) {
                    @Override
                    public void run(MessageReply reply) {
                        if (reply.isSuccess()) {
                            trigger.next();
                        } else {
                            trigger.fail(reply.getError());
                        }
                    }
                });
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "check-host-os-version";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                String distro = HostSystemTags.OS_DISTRIBUTION.getTokenByResourceUuid(vo.getUuid(), HostSystemTags.OS_DISTRIBUTION_TOKEN);
                String release = HostSystemTags.OS_RELEASE.getTokenByResourceUuid(vo.getUuid(), HostSystemTags.OS_RELEASE_TOKEN);
                String version = HostSystemTags.OS_VERSION.getTokenByResourceUuid(vo.getUuid(), HostSystemTags.OS_VERSION_TOKEN);

                if (distro == null && release == null && version == null) {
                    trigger.fail(errf.stringToOperationError(String.format("after connecting, host[name:%s, ip:%s] returns a null os version", vo.getName(), vo.getManagementIp())));
                    return;
                }

                SimpleQuery<HostVO> q = dbf.createQuery(HostVO.class);
                q.select(HostVO_.uuid);
                q.add(HostVO_.clusterUuid, Op.EQ, vo.getClusterUuid());
                q.add(HostVO_.uuid, Op.NOT_EQ, vo.getUuid());
                q.setLimit(1);
                List<String> huuids = q.listValue();
                if (huuids.isEmpty()) {
                    // this the first host in cluster
                    trigger.next();
                    return;
                }

                String otherHostUuid = huuids.get(0);
                String cdistro = HostSystemTags.OS_DISTRIBUTION.getTokenByResourceUuid(otherHostUuid, HostSystemTags.OS_DISTRIBUTION_TOKEN);
                String crelease = HostSystemTags.OS_RELEASE.getTokenByResourceUuid(otherHostUuid, HostSystemTags.OS_RELEASE_TOKEN);
                String cversion = HostSystemTags.OS_VERSION.getTokenByResourceUuid(otherHostUuid, HostSystemTags.OS_VERSION_TOKEN);
                if (cdistro == null && crelease == null && cversion == null) {
                    // this the first host in cluster
                    trigger.next();
                    return;
                }

                String mineVersion = String.format("%s;%s;%s", distro, release, version);
                String currentVersion = String.format("%s;%s;%s", cdistro, crelease, cversion);

                if (!mineVersion.equals(currentVersion)) {
                    trigger.fail(errf.stringToOperationError(String.format("cluster[uuid:%s] already has host with os version[%s], but new added host[name:%s ip:%s] has host os version[%s]",
                            vo.getClusterUuid(), currentVersion, vo.getName(), vo.getManagementIp(), mineVersion)));
                    return;
                }

                trigger.next();
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "call-after-add-host-extension";

            @Override
            public void run(final FlowTrigger trigger, Map data) {
                extEmitter.afterAddHost(inv, new Completion(trigger) {
                    @Override
                    public void success() {
                        trigger.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        trigger.fail(errorCode);
                    }
                });
            }
        }).done(new FlowDoneHandler(msg) {
            @Override
            public void handle(Map data) {
                HostInventory inv = factory.getHostInventory(vo.getUuid());
                inv.setStatus(HostStatus.Connected.toString());
                evt.setInventory(inv);
                bus.publish(evt);
                logger.debug(String.format("successfully added host[name:%s, hypervisor:%s, uuid:%s]", vo.getName(), vo.getHypervisorType(), vo.getUuid()));
            }
        }).error(new FlowErrorHandler(msg) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                evt.setErrorCode(errf.instantiateErrorCode(HostErrors.UNABLE_TO_ADD_HOST, errCode));
                bus.publish(evt);

                // delete host totally through the database, so other tables
                // refer to the host table will clean up themselves
                dbf.remove(vo);
                dbf.eoCleanup(HostVO.class);

                CollectionUtils.safeForEach(pluginRgty.getExtensionList(FailToAddHostExtensionPoint.class), new ForEachFunction<FailToAddHostExtensionPoint>() {
                    @Override
                    public void run(FailToAddHostExtensionPoint ext) {
                        ext.failedToAddHost(inv, msg);
                    }
                });
            }
        }).start();
    }

    private ClusterVO findClusterByUuid(String uuid) {
        SimpleQuery<ClusterVO> query = dbf.createQuery(ClusterVO.class);
        query.add(ClusterVO_.uuid, Op.EQ, uuid);
        return query.find();
    }

    @Override
    public String getId() {
        return bus.makeLocalServiceId(HostConstant.SERVICE_ID);
    }

    private void populateExtensions() {
        for (HypervisorFactory f : pluginRgty.getExtensionList(HypervisorFactory.class)) {
            HypervisorFactory old = hypervisorFactories.get(f.getHypervisorType().toString());
            if (old != null) {
                throw new CloudRuntimeException(String.format("duplicate HypervisorFactory[%s, %s] for hypervisor type[%s]",
                        old.getClass().getName(), f.getClass().getName(), f.getHypervisorType()));
            }
            hypervisorFactories.put(f.getHypervisorType().toString(), f);
        }

        for (HostMessageHandlerExtensionPoint handler : pluginRgty.getExtensionList(HostMessageHandlerExtensionPoint.class)) {
            assert handler.getMessageNameTheExtensionServed() != null;
            @SuppressWarnings("unchecked")
            List<String> msgNames = handler.getMessageNameTheExtensionServed();
            for (String msgName : msgNames) {
                @SuppressWarnings("rawtypes")
                HostMessageHandlerExtensionPoint old = msgHandlers.get(msgName);
                if (old != null) {
                    throw new CloudRuntimeException(String.format("Duplicate handler[%s, %s] found for message[%s], old one is %s, new one is %s", old.getClass().getName(), handler
                            .getClass().getName(), msgName, old.getClass().getName(), handler.getClass().getName()));
                }
                msgHandlers.put(msgName, handler);
            }
        }
    }

    @Override
    public boolean start() {
        populateExtensions();
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public void nodeJoin(String nodeId) {
    }

    @Override
    @SyncThread
    public void nodeLeft(String nodeId) {
        logger.debug(String.format("Management node[uuid:%s] left, node[uuid:%s] starts to take over hosts", nodeId, Platform.getManagementServerId()));
        loadHost();
    }

    @Override
    public void iAmDead(String nodeId) {
    }

    private Bucket getHostManagedByUs() {
        int qun = 10000;
        long amount = dbf.count(HostVO.class);
        int times = (int)(amount / qun) + (amount % qun != 0 ? 1 : 0);
        List<String> connected = new ArrayList<String>();
        List<String> disconnected = new ArrayList<String>();
        int start = 0;
        for (int i=0; i<times; i++) {
            SimpleQuery<HostVO> q = dbf.createQuery(HostVO.class);
            q.select(HostVO_.uuid, HostVO_.status);
            q.setLimit(qun);
            q.setStart(start);
            List<Tuple> lst = q.listTuple();
            start += qun;
            for (Tuple t : lst) {
                String huuid = t.get(0, String.class);
                if (!destMaker.isManagedByUs(huuid)) {
                    continue;
                }
                HostStatus state = t.get(1, HostStatus.class);
                if (state == HostStatus.Connected) {
                    connected.add(huuid);
                } else {
                    // for Disconnected and Connecting, treat as Disconnected
                    disconnected.add(huuid);
                }
            }
        }

        return Bucket.newBucket(connected, disconnected);
    }


    private void loadHost() {
        Bucket hosts = getHostManagedByUs();
        List<String> connected = hosts.get(0);
        List<String> disconnected = hosts.get(1);
        List<String> hostsToLoad = new ArrayList<String>();

        if (CoreGlobalProperty.UNIT_TEST_ON) {
            hostsToLoad.addAll(connected);
            hostsToLoad.addAll(disconnected);
        } else {
            hostsToLoad.addAll(disconnected);
            tracker.trackHost(connected);
        }

        if (hostsToLoad.isEmpty()) {
            return;
        }

        String serviceId = bus.makeLocalServiceId(HostConstant.SERVICE_ID);
        final List<ConnectHostMsg> msgs = new ArrayList<ConnectHostMsg>(hostsToLoad.size());
        for (String uuid : hostsToLoad) {
            ConnectHostMsg connectMsg = new ConnectHostMsg(uuid);
            connectMsg.setNewAdd(false);
            connectMsg.setServiceId(serviceId);
            connectMsg.setStartPingTaskOnFailure(true);
            msgs.add(connectMsg);
        }

        bus.send(msgs, HostGlobalConfig.HOST_LOAD_PARALLELISM_DEGREE.value(Integer.class), new CloudBusSteppingCallback() {
            @Override
            public void run(NeedReplyMessage msg, MessageReply reply) {
                ConnectHostMsg cmsg = (ConnectHostMsg) msg;
                if (!reply.isSuccess()) {
                    logger.warn(String.format("failed to load host[uuid:%s], %s", cmsg.getHostUuid(), reply.getError()));
                } else {
                    logger.debug(String.format("host[uuid:%s] load successfully", cmsg.getHostUuid()));
                }
            }
        });
    }

    @Override
    public void iJoin(String nodeId) {
    }


    public HypervisorFactory getHypervisorFactory(HypervisorType type) {
        HypervisorFactory factory = hypervisorFactories.get(type.toString());
        if (factory == null) {
            throw new CloudRuntimeException("No factory for hypervisor: " + type + " found, check your HypervisorManager.xml");
        }

        return factory;
    }

    @Override
    public HostMessageHandlerExtensionPoint getHostMessageHandlerExtension(Message msg) {
        return msgHandlers.get(msg.getMessageName());
    }

    @Override
    @AsyncThread
    public void managementNodeReady() {
        logger.debug(String.format("Management node[uuid:%s] joins, start loading host...", Platform.getManagementServerId()));
        loadHost();
    }
}
