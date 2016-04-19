package org.zstack.console;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.CoreGlobalProperty;
import org.zstack.core.Platform;
import org.zstack.core.ansible.AnsibleFacade;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.core.thread.AsyncThread;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.Component;
import org.zstack.header.console.*;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.rest.RESTFacade;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: frank
 * Time: 7:32 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractConsoleProxyBackend implements ConsoleBackend, Component, ManagementNodeReadyExtensionPoint {
    private static final CLogger logger = Utils.getLogger(AbstractConsoleProxyBackend.class);

    @Autowired
    protected DatabaseFacade dbf;
    @Autowired
    protected RESTFacade restf;
    @Autowired
    protected CloudBus bus;
    @Autowired
    protected AnsibleFacade asf;
    @Autowired
    protected ErrorFacade errf;

    protected static final String ANSIBLE_PLAYBOOK_NAME = "consoleproxy.py";

    protected abstract ConsoleProxy getConsoleProxy(VmInstanceInventory vm, ConsoleProxyVO vo);
    protected abstract ConsoleProxy getConsoleProxy(SessionInventory session, VmInstanceInventory vm);
    protected abstract void connectAgent();
    protected abstract boolean isAgentConnected();

    private void establishNewProxy(ConsoleProxy proxy, SessionInventory session, final VmInstanceInventory vm, final ReturnValueCompletion<ConsoleInventory> complete) {
        proxy.establishProxy(session, vm, new ReturnValueCompletion<ConsoleProxyInventory>() {
            @Override
            public void success(ConsoleProxyInventory ret) {
                ConsoleProxyVO vo = new ConsoleProxyVO();
                vo.setAgentIp(ret.getAgentIp());
                vo.setProxyIdentity(ret.getProxyIdentity());
                vo.setScheme(ret.getScheme());
                vo.setProxyHostname(ret.getProxyHostname());
                vo.setProxyPort(ret.getProxyPort());
                vo.setTargetHostname(ret.getTargetHostname());
                vo.setTargetPort(ret.getTargetPort());
                vo.setToken(ret.getToken());
                vo.setVmInstanceUuid(vm.getUuid());
                vo.setUuid(Platform.getUuid());
                vo.setAgentType(ret.getAgentType());
                vo.setStatus(ConsoleProxyStatus.Active);
                vo = dbf.persistAndRefresh(vo);

                complete.success(ConsoleInventory.valueOf(vo));
            }

            @Override
            public void fail(ErrorCode errorCode) {
                complete.fail(errorCode);
            }
        });
    }

    @Override
    public void grantConsoleAccess(final SessionInventory session, final VmInstanceInventory vm, final ReturnValueCompletion<ConsoleInventory> complete) {
        if (!isAgentConnected()) {
            complete.fail(errf.stringToOperationError(
                    "the console agent is not connected; it's mostly like the management node just starts, please wait for the console agent connected."
            ));
            return;
        }

        SimpleQuery<ConsoleProxyVO> q = dbf.createQuery(ConsoleProxyVO.class);
        q.add(ConsoleProxyVO_.vmInstanceUuid, SimpleQuery.Op.EQ, vm.getUuid());
        q.add(ConsoleProxyVO_.status, SimpleQuery.Op.EQ, ConsoleProxyStatus.Active);
        final ConsoleProxyVO vo = q.find();

        if (vo == null) {
            // new console proxy
            ConsoleProxy proxy = getConsoleProxy(session, vm);
            establishNewProxy(proxy, session, vm, complete);
            return;
        }


        String hostIp = getHostIp(vm);
        if (hostIp == null) {
            throw new OperationFailureException(errf.stringToOperationError(
                    String.format("cannot find host IP of the vm[uuid:%s], is the vm running???", vm.getUuid())
            ));
        }

        if (vo.getTargetHostname().equals(hostIp)) {
            // vm is on the same host
            final ConsoleProxy proxy = getConsoleProxy(vm, vo);
            proxy.checkAvailability(new ReturnValueCompletion<Boolean>() {
                @Override
                public void success(Boolean returnValue) {
                    if (returnValue) {
                        ConsoleInventory retInv = ConsoleInventory.valueOf(vo);
                        complete.success(retInv);
                    } else {
                        //TODO: run cleanup on agent side
                        dbf.remove(vo);

                        establishNewProxy(proxy, session, vm, complete);
                    }
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    complete.fail(errorCode);
                }
            });
        } else {
            // vm is on another host
            FlowChain chain = FlowChainBuilder.newShareFlowChain();
            chain.setName(String.format("recreate-console-for-vm-%s", vm.getUuid()));
            chain.then(new ShareFlow() {
                ConsoleInventory ret;

                @Override
                public void setup() {
                    flow(new NoRollbackFlow() {
                        String __name__ = "delete-old-console";

                        @Override
                        public void run(final FlowTrigger trigger, Map data) {
                            deleteConsoleSession(vm, new Completion(trigger) {
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
                    });

                    flow(new NoRollbackFlow() {
                        String __name__ = "create-new-console";

                        @Override
                        public void run(final FlowTrigger trigger, Map data) {
                            ConsoleProxy proxy = getConsoleProxy(session, vm);
                            establishNewProxy(proxy, session, vm, new ReturnValueCompletion<ConsoleInventory>(trigger) {
                                @Override
                                public void success(ConsoleInventory returnValue) {
                                    ret = returnValue;
                                    trigger.next();
                                }

                                @Override
                                public void fail(ErrorCode errorCode) {
                                    trigger.fail(errorCode);
                                }
                            });
                        }
                    });

                    done(new FlowDoneHandler(complete) {
                        @Override
                        public void handle(Map data) {
                            complete.success(ret);
                        }
                    });

                    error(new FlowErrorHandler(complete) {
                        @Override
                        public void handle(ErrorCode errCode, Map data) {
                            complete.fail(errCode);
                        }
                    });
                }
            }).start();
        }
    }

    @Transactional(readOnly = true)
    protected String getHostIp(VmInstanceInventory vm) {
        String sql = "select h.managementIp from HostVO h, VmInstanceVO vm where h.uuid = vm.hostUuid and vm.uuid = :uuid";
        TypedQuery<String> q = dbf.getEntityManager().createQuery(sql, String.class);
        q.setParameter("uuid", vm.getUuid());
        List<String> ret = q.getResultList();
        return ret.isEmpty() ? null : ret.get(0);
    }

    @Override
    public void deleteConsoleSession(final VmInstanceInventory vm, final Completion completion) {
        SimpleQuery<ConsoleProxyVO> q = dbf.createQuery(ConsoleProxyVO.class);
        q.add(ConsoleProxyVO_.vmInstanceUuid, SimpleQuery.Op.EQ, vm.getUuid());
        q.add(ConsoleProxyVO_.status, SimpleQuery.Op.EQ, ConsoleProxyStatus.Active);
        final ConsoleProxyVO vo = q.find();
        if (vo != null) {
            ConsoleProxy proxy = getConsoleProxy(vm, vo);
            proxy.deleteProxy(vm, new Completion(completion) {
                @Override
                public void success() {
                    dbf.remove(vo);
                    logger.debug(String.format("deleted a console proxy[vmUuid:%s, host IP: %s, host port: %s, proxy IP: %s, proxy port: %s",
                            vm.getUuid(), vo.getTargetHostname(), vo.getTargetPort(), vo.getProxyHostname(), vo.getProxyPort()));
                    completion.success();
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    completion.fail(errorCode);
                }
            });
        } else {
            completion.success();
        }
    }


    private void deploySaltState() {
        if (CoreGlobalProperty.UNIT_TEST_ON) {
            return;
        }

        asf.deployModule("ansible/consoleproxy", ANSIBLE_PLAYBOOK_NAME);
    }

    @Override
    public boolean start() {
        deploySaltState();
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    @AsyncThread
    public void managementNodeReady() {
        if (CoreGlobalProperty.UNIT_TEST_ON) {
            return;
        }

        connectAgent();
    }
}
