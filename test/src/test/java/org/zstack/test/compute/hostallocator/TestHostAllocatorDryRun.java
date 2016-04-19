package org.zstack.test.compute.hostallocator;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.allocator.*;
import org.zstack.header.configuration.InstanceOfferingInventory;
import org.zstack.header.host.HostInventory;
import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.vm.VmInstanceConstant.VmOperation;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.deployer.Deployer;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.function.Function;

/**
 * make every conditions ready
 *
 * confirm starts right and host capacity allocated right
 */
public class TestHostAllocatorDryRun {
    Deployer deployer;
    Api api; 
    ComponentLoader loader;
    CloudBus bus;
    DatabaseFacade dbf;
    
    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        deployer = new Deployer("deployerXml/vm/TestMigrateVm.xml");
        deployer.build();
        api = deployer.getApi();
        loader = deployer.getComponentLoader();
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
    }

    @Test
    public void test() throws ApiSenderException {
        VmInstanceInventory vm = deployer.vms.get("TestVm");
        DesignatedAllocateHostMsg msg = new DesignatedAllocateHostMsg();
        msg.setCpuCapacity(vm.getCpuNum() * vm.getCpuSpeed());
        msg.setMemoryCapacity(vm.getMemorySize());
        msg.getAvoidHostUuids().add(vm.getHostUuid());
        msg.setVmInstance(vm);
        msg.setServiceId(bus.makeLocalServiceId(HostAllocatorConstant.SERVICE_ID));
        msg.setAllocatorStrategy(HostAllocatorConstant.MIGRATE_VM_ALLOCATOR_TYPE);
        msg.setVmOperation(VmOperation.Migrate.toString());
        msg.setL3NetworkUuids(CollectionUtils.transformToList(vm.getVmNics(), new Function<String, VmNicInventory>() {
            @Override
            public String call(VmNicInventory arg) {
                return arg.getL3NetworkUuid();
            }
        }));
        msg.setDryRun(true);
        AllocateHostDryRunReply reply = (AllocateHostDryRunReply) bus.call(msg);
        for (HostInventory targetHost : reply.getHosts()) {
            HostCapacityVO hcvo = dbf.findByUuid(targetHost.getUuid(), HostCapacityVO.class);
            Assert.assertEquals(0, hcvo.getUsedCpu());
            Assert.assertEquals(0, hcvo.getUsedMemory());
        }
    }
}
