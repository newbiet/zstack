package org.zstack.test.mevoco.billing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.billing.*;
import org.zstack.cassandra.CassandraFacade;
import org.zstack.cassandra.CassandraOperator;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.allocator.HostCapacityOverProvisioningManager;
import org.zstack.header.identity.AccountConstant;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.storage.primary.PrimaryStorageOverProvisioningManager;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.network.service.flat.FlatNetworkServiceSimulatorConfig;
import org.zstack.simulator.kvm.KVMSimulatorConfig;
import org.zstack.storage.primary.local.LocalStorageSimulatorConfig;
import org.zstack.storage.primary.local.LocalStorageSimulatorConfig.Capacity;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.WebBeanConstructor;
import org.zstack.test.deployer.Deployer;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.data.SizeUnit;
import org.zstack.utils.function.Function;
import org.zstack.utils.logging.CLogger;

import java.util.concurrent.TimeUnit;

/**
 * test multiple prices
 */
public class TestBilling4 {
    CLogger logger = Utils.getLogger(TestBilling4.class);
    Deployer deployer;
    Api api;
    ComponentLoader loader;
    CloudBus bus;
    DatabaseFacade dbf;
    SessionInventory session;
    LocalStorageSimulatorConfig config;
    FlatNetworkServiceSimulatorConfig fconfig;
    KVMSimulatorConfig kconfig;
    PrimaryStorageOverProvisioningManager psRatioMgr;
    HostCapacityOverProvisioningManager hostRatioMgr;
    long totalSize = SizeUnit.GIGABYTE.toByte(100);
    CassandraFacade cassf;
    CassandraOperator ops;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        DBUtil.reDeployCassandra(BillingConstants.CASSANDRA_KEYSPACE);
        WebBeanConstructor con = new WebBeanConstructor();
        deployer = new Deployer("deployerXml/mevoco/TestMevoco.xml", con);
        deployer.addSpringConfig("mevocoRelated.xml");
        deployer.addSpringConfig("cassandra.xml");
        deployer.addSpringConfig("billing.xml");
        deployer.load();

        loader = deployer.getComponentLoader();
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
        config = loader.getComponent(LocalStorageSimulatorConfig.class);
        fconfig = loader.getComponent(FlatNetworkServiceSimulatorConfig.class);
        kconfig = loader.getComponent(KVMSimulatorConfig.class);
        psRatioMgr = loader.getComponent(PrimaryStorageOverProvisioningManager.class);
        hostRatioMgr = loader.getComponent(HostCapacityOverProvisioningManager.class);
        cassf = loader.getComponent(CassandraFacade.class);
        ops = cassf.getOperator(BillingConstants.CASSANDRA_KEYSPACE);

        Capacity c = new Capacity();
        c.total = totalSize;
        c.avail = totalSize;

        config.capacityMap.put("host1", c);

        deployer.build();
        api = deployer.getApi();
        session = api.loginAsAdmin();
    }
    
	@Test
	public void test() throws ApiSenderException, InterruptedException {
        VmInstanceInventory vm = deployer.vms.get("TestVm");
        api.stopVmInstance(vm.getUuid());
        APICreateResourcePriceMsg msg = new APICreateResourcePriceMsg();
        msg.setTimeUnit("s");
        msg.setPrice(100f);
        msg.setResourceName(BillingConstants.SPENDING_CPU);
        PriceInventory pc1 = api.createPrice(msg);

        msg = new APICreateResourcePriceMsg();
        msg.setTimeUnit("s");
        msg.setPrice(10f);
        msg.setResourceName(BillingConstants.SPENDING_MEMORY);
        msg.setResourceUnit("b");
        PriceInventory pm1 = api.createPrice(msg);

        int during = 2;
        vm = api.startVmInstance(vm.getUuid());

        TimeUnit.SECONDS.sleep(during);

        msg = new APICreateResourcePriceMsg();
        msg.setTimeUnit("s");
        msg.setPrice(80f);
        msg.setResourceName(BillingConstants.SPENDING_CPU);
        msg.setResourceUnit("s");
        PriceInventory pc2 = api.createPrice(msg);

        msg = new APICreateResourcePriceMsg();
        msg.setTimeUnit("s");
        msg.setPrice(120f);
        msg.setResourceName(BillingConstants.SPENDING_MEMORY);
        msg.setResourceUnit("b");
        PriceInventory pm2 = api.createPrice(msg);

        vm = api.stopVmInstance(vm.getUuid());
        float cpuPrice1 = vm.getCpuNum() * pc1.getPrice() * during;
        float memPrice1 = vm.getMemorySize() * pm1.getPrice() * during;

        logger.debug(String.format("phase1: cpu price: %s, memory price: %s, during: %s s", cpuPrice1, memPrice1, during));

        vm = api.startVmInstance(vm.getUuid());

        TimeUnit.SECONDS.sleep(during);
        api.destroyVmInstance(vm.getUuid());

        float cpuPrice2 = vm.getCpuNum() * pc2.getPrice() * during;
        float memPrice2 = vm.getMemorySize() * pm2.getPrice() * during;

        logger.debug(String.format("phase2: cpu price: %s, memory price: %s, during: %s s", cpuPrice2, memPrice2, during));

        final APICalculateAccountSpendingReply reply = api.calculateSpending(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID, null);

        float cpuPrice = cpuPrice1 + cpuPrice2;
        float memPrice = memPrice1 + memPrice2;
        Assert.assertEquals(cpuPrice + memPrice, reply.getTotal(), 0.02);

        Spending spending = CollectionUtils.find(reply.getSpending(), new Function<Spending, Spending>() {
            @Override
            public Spending call(Spending arg) {
                return BillingConstants.SPENDING_TYPE_VM.equals(arg.getSpendingType()) ? arg : null;
            }
        });
        Assert.assertNotNull(spending);

        SpendingDetails cpudetails = CollectionUtils.find(spending.getDetails(), new Function<SpendingDetails, SpendingDetails>() {
            @Override
            public SpendingDetails call(SpendingDetails arg) {
                return BillingConstants.SPENDING_CPU.equals(arg.type) ? arg : null;
            }
        });
        Assert.assertNotNull(cpudetails);
        Assert.assertEquals(cpuPrice, cpudetails.spending, 0.02);

        SpendingDetails memdetails = CollectionUtils.find(spending.getDetails(), new Function<SpendingDetails, SpendingDetails>() {
            @Override
            public SpendingDetails call(SpendingDetails arg) {
                return BillingConstants.SPENDING_MEMORY.equals(arg.type) ? arg : null;
            }
        });
        Assert.assertNotNull(memdetails);
        Assert.assertEquals(memPrice, memdetails.spending, 0.02);
    }
}
