package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.AbstractService;
import org.zstack.header.allocator.*;
import org.zstack.header.cluster.ReportHostCapacityMessage;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.FlowRollback;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.host.*;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.vm.VmAbnormalLifeCycleExtensionPoint;
import org.zstack.header.vm.VmAbnormalLifeCycleStruct;
import org.zstack.header.vm.VmAbnormalLifeCycleStruct.VmAbnormalLifeCycleOperation;
import org.zstack.header.vm.VmInstanceState;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.function.Function;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.*;
import java.util.concurrent.Callable;

import static org.zstack.utils.CollectionDSL.list;

public class HostAllocatorManagerImpl extends AbstractService implements HostAllocatorManager, VmAbnormalLifeCycleExtensionPoint {
	private static final CLogger logger = Utils.getLogger(HostAllocatorManagerImpl.class);

	private Map<String, HostAllocatorStrategyFactory> factories = Collections.synchronizedMap(new HashMap<String, HostAllocatorStrategyFactory>());

	@Autowired
	private CloudBus bus;
	@Autowired
	private DatabaseFacade dbf;
	@Autowired
	private PluginRegistry pluginRgty;
    @Autowired
    private HostCapacityReserveManager reserveMgr;
    @Autowired
    private HostCapacityOverProvisioningManager ratioMgr;
    @Autowired
    private ErrorFacade errf;

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
		if (msg instanceof AllocateHostMsg) {
			handle((AllocateHostMsg) msg);
		} else if (msg instanceof ReportHostCapacityMessage) {
			handle((ReportHostCapacityMessage) msg);
		} else if (msg instanceof ReturnHostCapacityMsg) {
            handle((ReturnHostCapacityMsg) msg);
        } else if (msg instanceof RecalculateHostCapacityMsg) {
            handle((RecalculateHostCapacityMsg) msg);
		} else {
			bus.dealWithUnknownMessage(msg);
		}
	}

    private void handle(RecalculateHostCapacityMsg msg) {
        final List<String> hostUuids = new ArrayList<String>();
        if (msg.getHostUuid() != null) {
            hostUuids.add(msg.getHostUuid());
        } else {
            SimpleQuery<HostVO> q = dbf.createQuery(HostVO.class);
            q.select(HostVO_.uuid);
            q.add(HostVO_.zoneUuid, Op.EQ, msg.getZoneUuid());
            hostUuids.addAll(q.<String>listValue());
        }

        if (hostUuids.isEmpty()) {
            return;
        }

        class Struct {
            String hostUuid;
            Long usedMemory;
            Long usedCpu;
        }

        List<Struct> ss = new Callable<List<Struct>>() {
            @Override
            @Transactional(readOnly = true)
            public List<Struct> call() {
                String sql = "select sum(vm.memorySize), vm.hostUuid, sum(vm.cpuNum * vm.cpuSpeed) from VmInstanceVO vm where vm.hostUuid in (:hostUuids) and vm.state not in (:vmStates) group by vm.hostUuid";
                TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
                q.setParameter("hostUuids", hostUuids);
                q.setParameter("vmStates", list(VmInstanceState.Destroyed, VmInstanceState.Created, VmInstanceState.Destroying));
                List<Tuple> ts = q.getResultList();

                List<Struct> ret = new ArrayList<Struct>();
                for (Tuple t : ts) {
                    Struct s = new Struct();
                    s.hostUuid = t.get(1, String.class);

                    if (t.get(0, Long.class) == null) {
                        continue;
                    }

                    s.usedMemory = ratioMgr.calculateMemoryByRatio(s.hostUuid, t.get(0, Long.class));
                    s.usedCpu = t.get(2, Long.class);
                    ret.add(s);
                }
                return ret;
            }
        }.call();

        List<String> hostHasVms = CollectionUtils.transformToList(ss, new Function<String, Struct>() {
            @Override
            public String call(Struct arg) {
                return arg.hostUuid;
            }
        });

        for (String huuid : hostUuids) {
            if (!hostHasVms.contains(huuid)) {
                Struct s = new Struct();
                s.hostUuid = huuid;
                ss.add(s);
            }
        }

        for (final Struct s : ss) {
            new HostCapacityUpdater(s.hostUuid).run(new HostCapacityUpdaterRunnable() {
                @Override
                public HostCapacityVO call(HostCapacityVO cap) {
                    long before = cap.getAvailableMemory();
                    long avail = s.usedMemory == null ? cap.getTotalMemory() : cap.getTotalMemory() - s.usedMemory;
                    cap.setAvailableMemory(avail);

                    long beforeCpu = cap.getAvailableCpu();
                    long availCpu = s.usedCpu == null ? cap.getTotalCpu() : cap.getTotalCpu() - s.usedCpu;
                    cap.setAvailableCpu(availCpu);

                    logger.debug(String.format("re-calculated available capacity on the host[uuid:%s]:" +
                            "\n[available memory] before: %s, now: %s" +
                            "\n[available cpu] before: %s, now :%s", s.hostUuid, before, avail, beforeCpu, availCpu));
                    return cap;
                }
            });
        }
    }

    private void handle(ReturnHostCapacityMsg msg) {
	    returnCapacity(msg.getHostUuid(), msg.getCpuCapacity(), msg.getMemoryCapacity());
    }

    private void handle(ReportHostCapacityMessage msg) {
        HostCapacityVO vo = dbf.findByUuid(msg.getHostUuid(), HostCapacityVO.class);
        long availCpu = msg.getTotalCpu() - msg.getUsedCpu();
        availCpu = availCpu > 0 ? availCpu : 0;
        long availMem = msg.getTotalMemory() - msg.getUsedMemory();
        availMem = availMem > 0 ? availMem : 0;
        if (vo == null) {
            vo = new HostCapacityVO();
            vo.setUuid(msg.getHostUuid());
            vo.setTotalCpu(msg.getTotalCpu());
            vo.setAvailableCpu(availCpu);
            vo.setTotalMemory(msg.getTotalMemory());
            vo.setAvailableMemory(availMem);
            vo.setTotalPhysicalMemory(msg.getTotalMemory());
            vo.setAvailablePhysicalMemory(availMem);

            HostCapacityStruct s = new HostCapacityStruct();
            s.setCapacityVO(vo);
            s.setTotalCpu(msg.getTotalCpu());
            s.setTotalMemory(msg.getTotalMemory());
            s.setUsedCpu(msg.getUsedCpu());
            s.setUsedMemory(msg.getUsedMemory());
            s.setInit(true);
            for (ReportHostCapacityExtensionPoint ext : pluginRgty.getExtensionList(ReportHostCapacityExtensionPoint.class)) {
                vo = ext.reportHostCapacity(s);
            }
            dbf.persist(vo);
        } else {
            vo.setTotalCpu(msg.getTotalCpu());
            vo.setAvailableCpu(availCpu);
            vo.setTotalPhysicalMemory(msg.getTotalMemory());
            vo.setAvailablePhysicalMemory(availMem);
            vo.setTotalMemory(msg.getTotalMemory());

            HostCapacityStruct s = new HostCapacityStruct();
            s.setCapacityVO(vo);
            s.setTotalCpu(msg.getTotalCpu());
            s.setTotalMemory(msg.getTotalMemory());
            s.setUsedCpu(msg.getUsedCpu());
            s.setUsedMemory(msg.getUsedMemory());
            s.setInit(false);
            for (ReportHostCapacityExtensionPoint ext : pluginRgty.getExtensionList(ReportHostCapacityExtensionPoint.class)) {
                vo = ext.reportHostCapacity(s);
            }
            dbf.update(vo);
        }
    }

	private void handle(final AllocateHostMsg msg) {
        HostAllocatorSpec spec = HostAllocatorSpec.fromAllocationMsg(msg);
        String allocatorStrategyType = null;
        for (HostAllocatorStrategyExtensionPoint ext : pluginRgty.getExtensionList(HostAllocatorStrategyExtensionPoint.class)) {
            allocatorStrategyType = ext.getHostAllocatorStrategyName(spec);
            if (allocatorStrategyType != null) {
                logger.debug(String.format("%s returns allocator strategy type[%s]", ext.getClass(), allocatorStrategyType));
                break;
            }
        }

        if (allocatorStrategyType == null) {
            allocatorStrategyType = msg.getAllocatorStrategy();
        }

        HostAllocatorStrategyFactory factory = getHostAllocatorStrategyFactory(HostAllocatorStrategyType.valueOf(allocatorStrategyType));
        HostAllocatorStrategy strategy = factory.getHostAllocatorStrategy();
        factory.marshalSpec(spec, msg);

        if (msg.isDryRun()) {
            final AllocateHostDryRunReply reply = new AllocateHostDryRunReply();
            strategy.dryRun(spec, new ReturnValueCompletion<List<HostInventory>>() {
                @Override
                public void success(List<HostInventory> returnValue) {
                    reply.setHosts(returnValue);
                    bus.reply(msg, reply);
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    reply.setError(errorCode);
                    bus.reply(msg, reply);
                }
            });
        } else {
            final AllocateHostReply reply = new AllocateHostReply();
            strategy.allocate(spec,  new ReturnValueCompletion<HostInventory>(msg) {
                @Override
                public void success(HostInventory returnValue) {
                    reply.setHost(returnValue);
                    bus.reply(msg, reply);
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    reply.setError(errorCode);
                    bus.reply(msg, reply);
                }
            });
        }
	}

	private void handleApiMessage(APIMessage msg) {
	    if (msg instanceof APIGetCpuMemoryCapacityMsg) {
	        handle((APIGetCpuMemoryCapacityMsg) msg);
        } else  if (msg instanceof APIGetHostAllocatorStrategiesMsg) {
            handle((APIGetHostAllocatorStrategiesMsg) msg);
	    } else {
	        bus.dealWithUnknownMessage(msg);
	    }
	}

    private void handle(APIGetHostAllocatorStrategiesMsg msg) {
        APIGetHostAllocatorStrategiesReply reply = new APIGetHostAllocatorStrategiesReply();
        reply.setHostAllocatorStrategies(HostAllocatorStrategyType.getAllExposedTypeNames());
        bus.reply(msg, reply);
    }


    private void handle(final APIGetCpuMemoryCapacityMsg msg) {
        APIGetCpuMemoryCapacityReply reply = new APIGetCpuMemoryCapacityReply();

        Tuple ret = new Callable<Tuple>() {
            @Override
            @Transactional(readOnly = true)
            public Tuple call() {
                if (msg.getHostUuids() != null && !msg.getHostUuids().isEmpty()) {
                    String sql = "select sum(hc.totalCpu), sum(hc.availableCpu), sum(hc.availableMemory), sum(hc.totalMemory) from HostCapacityVO hc, HostVO host where hc.uuid in (:hostUuids)" +
                            " and hc.uuid = host.uuid and host.state = :hstate and host.status = :hstatus";
                    TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
                    q.setParameter("hostUuids", msg.getHostUuids());
                    q.setParameter("hstate", HostState.Enabled);
                    q.setParameter("hstatus", HostStatus.Connected);
                    return q.getSingleResult();
                }  else if (msg.getClusterUuids() != null && !msg.getClusterUuids().isEmpty()) {
                    String sql = "select sum(hc.totalCpu), sum(hc.availableCpu), sum(hc.availableMemory), sum(hc.totalMemory) from " +
                            "HostCapacityVO hc, HostVO host where hc.uuid = host.uuid and host.clusterUuid in (:clusterUuids) and host.state = :hstate and host.status = :hstatus";
                    TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
                    q.setParameter("clusterUuids", msg.getClusterUuids());
                    q.setParameter("hstate", HostState.Enabled);
                    q.setParameter("hstatus", HostStatus.Connected);
                    return q.getSingleResult();
                } else if (msg.getZoneUuids() != null && !msg.getZoneUuids().isEmpty()) {
                    String sql = "select sum(hc.totalCpu), sum(hc.availableCpu), sum(hc.availableMemory), sum(hc.totalMemory) from HostCapacityVO hc, HostVO host" +
                            " where hc.uuid = host.uuid and host.zoneUuid in (:zoneUuids) and host.state = :hstate and host.status = :hstatus";
                    TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
                    q.setParameter("zoneUuids", msg.getZoneUuids());
                    q.setParameter("hstate", HostState.Enabled);
                    q.setParameter("hstatus", HostStatus.Connected);
                    return q.getSingleResult();
                }

                throw new CloudRuntimeException("should not be here");
            }
        }.call();

        long totalCpu = ret.get(0, Long.class) == null ? 0 : ret.get(0, Long.class) ;
        long availCpu = ret.get(1, Long.class) == null ? 0 : ret.get(1, Long.class);
        long availMemory = ret.get(2, Long.class) == null ? 0 : ret.get(2, Long.class);
        long totalMemory = ret.get(3, Long.class) == null ? 0 : ret.get(3, Long.class);

        ReservedHostCapacity rc = null;
        if (msg.getHostUuids() != null && !msg.getHostUuids().isEmpty()) {
            rc = reserveMgr.getReservedHostCapacityByHosts(msg.getHostUuids());
        } else if (msg.getClusterUuids() != null && !msg.getClusterUuids().isEmpty()) {
            rc = reserveMgr.getReservedHostCapacityByClusters(msg.getClusterUuids());
        } else if (msg.getZoneUuids() != null && !msg.getZoneUuids().isEmpty()) {
            rc = reserveMgr.getReservedHostCapacityByZones(msg.getZoneUuids());
        } else {
            throw new CloudRuntimeException("should not be here");
        }

        availCpu = availCpu - rc.getReservedCpuCapacity();
        availMemory = availMemory - rc.getReservedMemoryCapacity();
        availCpu = availCpu > 0 ? availCpu : 0;
        availMemory = availMemory > 0 ? availMemory : 0;

        reply.setTotalCpu(totalCpu);
        reply.setTotalMemory(totalMemory);
        reply.setAvailableCpu(availCpu);
        reply.setAvailableMemory(availMemory);
        bus.reply(msg, reply);
    }

    @Override
	public String getId() {
		return bus.makeLocalServiceId(HostAllocatorConstant.SERVICE_ID);
	}

	private void populateHostAllocatorStrategyFactory() {
        for (HostAllocatorStrategyFactory ext : pluginRgty.getExtensionList(HostAllocatorStrategyFactory.class)) {
            HostAllocatorStrategyFactory old = factories.get(ext.getHostAllocatorStrategyType().toString());
            if (old != null) {
                throw new CloudRuntimeException(String.format("duplicate HostAllocatorStrategyFactory[%s, %s] for type[%s]",
                        old.getClass().getName(), ext.getClass().getName(), ext.getHostAllocatorStrategy()));
            }
            factories.put(ext.getHostAllocatorStrategyType().toString(), ext);
        }
	}
	
	@Override
	public boolean start() {
		populateHostAllocatorStrategyFactory();
		return true;
	}

	@Override
	public boolean stop() {
		return true;
	}

	@Override
	public HostAllocatorStrategyFactory getHostAllocatorStrategyFactory(HostAllocatorStrategyType type) {
		HostAllocatorStrategyFactory factory = factories.get(type.toString());
		if (factory == null) {
			throw new CloudRuntimeException(String.format("Unable to find HostAllocatorStrategyFactory with type[%s]", type));
		}

		return factory;
	}
	
	@Override
    public void returnCapacity(final String hostUuid, final long cpu, final long memory) {
        new HostCapacityUpdater(hostUuid).run(new HostCapacityUpdaterRunnable() {
            @Override
            public HostCapacityVO call(HostCapacityVO cap) {
                long availCpu = cap.getAvailableCpu() + cpu;
                availCpu = availCpu > cap.getTotalCpu() ? cap.getTotalCpu() : availCpu;
                /*
                if (availCpu > cap.getTotalCpu()) {
                    throw new CloudRuntimeException(String.format("invalid cpu capcity of the host[uuid:%s], available cpu[%s]" +
                            " is larger than the total cpu[%s]", hostUuid, availCpu, cap.getTotalCpu()));
                }
                */

                cap.setAvailableCpu(availCpu);

                long availMemory = cap.getAvailableMemory() + ratioMgr.calculateMemoryByRatio(hostUuid, memory);
                if (availMemory > cap.getTotalMemory()) {
                    throw new CloudRuntimeException(String.format("invalid memory capacity of host[uuid:%s], available memory[%s] is greater than total memory[%s]",
                            hostUuid, availMemory, cap.getTotalMemory()));
                }

                cap.setAvailableMemory(availMemory);

                return cap;
            }
        });
    }

    @Override
    public Flow createVmAbnormalLifeCycleHandlingFlow(final VmAbnormalLifeCycleStruct struct) {
        return new Flow() {
            String __name__ = "allocate-host-capacity";

            VmAbnormalLifeCycleOperation operation = struct.getOperation();
            Runnable rollback;

            @Override
            public void run(FlowTrigger trigger, Map data) {
                if (operation == VmAbnormalLifeCycleOperation.VmRunningOnTheHost) {
                    vmRunningOnHost(trigger);
                } else if (operation == VmAbnormalLifeCycleOperation.VmMigrateToAnotherHost) {
                    vmMigrateToAnotherHost(trigger);
                } else if (operation == VmAbnormalLifeCycleOperation.VmRunningFromIntermediateState) {
                    vmRunningFromIntermediateState(trigger);
                } else if (operation == VmAbnormalLifeCycleOperation.VmStoppedOnTheSameHost) {
                    vmStoppedOnTheSameHost(trigger);
                } else if (operation == VmAbnormalLifeCycleOperation.VmRunningFromUnknownStateHostChanged) {
                    vmRunningFromUnknownStateHostChanged(trigger);
                } else {
                    trigger.next();
                }
            }

            private void vmRunningFromUnknownStateHostChanged(FlowTrigger trigger) {
                // resync the capacity on the current host
                resyncHostCapacity();
                trigger.next();
            }

            private void resyncHostCapacity() {
                //TODO
            }

            private void vmStoppedOnTheSameHost(FlowTrigger trigger) {
                // return the capacity to the current host
                returnCapacity(struct.getCurrentHostUuid());
                rollback = new Runnable() {
                    @Override
                    public void run() {
                        long cpu = struct.getVmInstance().getCpuNum() * struct.getVmInstance().getCpuSpeed();
                        new HostAllocatorChain().reserveCapacity(struct.getCurrentHostUuid(), cpu, struct.getVmInstance().getMemorySize());
                    }
                };
                trigger.next();
            }

            private void returnCapacity(String hostUuid) {
                ReturnHostCapacityMsg msg = new ReturnHostCapacityMsg();
                msg.setCpuCapacity(struct.getVmInstance().getCpuNum() * struct.getVmInstance().getCpuSpeed());
                msg.setMemoryCapacity(struct.getVmInstance().getMemorySize());
                msg.setHostUuid(hostUuid);
                bus.makeLocalServiceId(msg, HostAllocatorConstant.SERVICE_ID);
                bus.send(msg);
            }

            private void vmRunningFromIntermediateState(FlowTrigger trigger) {
                // resync the capacity on the current host
                resyncHostCapacity();
                trigger.next();
            }

            private void vmMigrateToAnotherHost(FlowTrigger trigger) {
                // allocate the capacity on the current host
                // return the capacity to the original host
                try {
                    final long cpu = struct.getVmInstance().getCpuNum() * struct.getVmInstance().getCpuSpeed();
                    new HostAllocatorChain().reserveCapacity(struct.getCurrentHostUuid(), cpu, struct.getVmInstance().getMemorySize());
                    returnCapacity(struct.getOriginalHostUuid());

                    rollback = new Runnable() {
                        @Override
                        public void run() {
                            returnCapacity(struct.getCurrentHostUuid());
                            new HostAllocatorChain().reserveCapacity(struct.getOriginalHostUuid(), cpu, struct.getVmInstance().getMemorySize());
                        }
                    };

                    trigger.next();
                } catch (UnableToReserveHostCapacityException e) {
                    trigger.fail(errf.stringToOperationError(e.getMessage()));
                }
            }

            private void vmRunningOnHost(FlowTrigger trigger) {
                // allocate capacity on the current host
                try {
                    long cpu = struct.getVmInstance().getCpuNum() * struct.getVmInstance().getCpuSpeed();
                    new HostAllocatorChain().reserveCapacity(struct.getCurrentHostUuid(), cpu, struct.getVmInstance().getMemorySize());

                    rollback = new Runnable() {
                        @Override
                        public void run() {
                            returnCapacity(struct.getCurrentHostUuid());
                        }
                    };

                    trigger.next();
                } catch (UnableToReserveHostCapacityException e) {
                    trigger.fail(errf.stringToOperationError(e.getMessage()));
                }
            }

            @Override
            public void rollback(FlowRollback trigger, Map data) {
                if (rollback != null) {
                    rollback.run();
                }

                trigger.rollback();
            }
        };
    }
}
