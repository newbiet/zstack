package org.zstack.test.mevoco.billing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.billing.*;
import org.zstack.cassandra.CassandraFacade;
import org.zstack.cassandra.CassandraOperator;
import org.zstack.cassandra.Cql;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.allocator.HostCapacityOverProvisioningManager;
import org.zstack.header.identity.AccountConstant;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.storage.primary.PrimaryStorageOverProvisioningManager;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmInstanceState;
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
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * R: running
 * S: stopped
 */
public class TestBilling3 {
    CLogger logger = Utils.getLogger(TestBilling3.class);
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
        deployer = new Deployer("deployerXml/billing/TestBilling3.xml", con);
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

    private void check(APICalculateAccountSpendingReply reply, float cpuPrice, float memPrice) {
        Spending spending = CollectionUtils.find(reply.getSpending(), new Function<Spending, Spending>() {
            @Override
            public Spending call(Spending arg) {
                return BillingConstants.SPENDING_TYPE_VM.equals(arg.getSpendingType()) ? arg : null;
            }
        });
        Assert.assertNotNull(spending);

        float c = 0;
        float m = 0;
        for (SpendingDetails d : spending.getDetails()) {
            if (BillingConstants.SPENDING_CPU.equals(d.type)) {
                c += d.spending;
            }
            if (BillingConstants.SPENDING_MEMORY.equals(d.type)) {
                m += d.spending;
            }
        }
        Assert.assertEquals(cpuPrice, c, 0.02);
        Assert.assertEquals(memPrice, m, 0.02);
    }
    
	@Test
	public void test() throws ApiSenderException, InterruptedException {
        final VmInstanceInventory vm = deployer.vms.get("TestVm");
        api.stopVmInstance(vm.getUuid());
        final VmInstanceInventory vm1 = deployer.vms.get("TestVm1");
        api.stopVmInstance(vm1.getUuid());

        float cprice = 100.01f;
        float mprice = 10.03f;

        APICreateResourcePriceMsg msg = new APICreateResourcePriceMsg();
        msg.setTimeUnit("s");
        msg.setPrice(cprice);
        msg.setResourceName(BillingConstants.SPENDING_CPU);
        api.createPrice(msg);
        Cql cql = new Cql("select * from <table> where resourceName = :name limit 1");
        cql.setTable(PriceCO.class.getSimpleName()).setParameter("name", BillingConstants.SPENDING_CPU);
        PriceCO cpupco = ops.selectOne(cql.build(), PriceCO.class);
        Assert.assertNotNull(cpupco);

        final PriceUDF pudf = new PriceUDF();
        pudf.setPrice(cpupco.getPrice());
        pudf.setTimeUnit(cpupco.getTimeUnit());
        pudf.setResourceUnit(cpupco.getResourceUnit());

        msg = new APICreateResourcePriceMsg();
        msg.setTimeUnit("s");
        msg.setPrice(mprice);
        msg.setResourceName(BillingConstants.SPENDING_MEMORY);
        msg.setResourceUnit("b");
        api.createPrice(msg);
        cql = new Cql("select * from <table> where resourceName = :name limit 1");
        cql.setTable(PriceCO.class.getSimpleName()).setParameter("name", BillingConstants.SPENDING_MEMORY);
        PriceCO mempco = ops.selectOne(cql.build(), PriceCO.class);
        Assert.assertNotNull(mempco);

        final PriceUDF mudf = new PriceUDF();
        mudf.setPrice(mempco.getPrice());
        mudf.setTimeUnit(mempco.getTimeUnit());
        mudf.setResourceUnit(mempco.getResourceUnit());

        cql = new Cql("delete from <table> where accountUuid = :uuid");
        cql.setTable(VmUsageCO.class.getSimpleName()).setParameter("uuid", AccountConstant.INITIAL_SYSTEM_ADMIN_UUID);
        ops.execute(cql.build());

        class CreatePrice {
            VmInstanceInventory vmInstance;

            public CreatePrice(VmInstanceInventory vmInstance) {
                this.vmInstance = vmInstance;
            }

            void create(VmInstanceState state, Date date) {
                VmUsageCO u = new VmUsageCO();
                u.setAccountUuid(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID);
                u.setVmUuid(vmInstance.getUuid());
                u.setCpuNum(vmInstance.getCpuNum());
                u.setMemorySize(vmInstance.getMemorySize());
                u.setCpuPrice(pudf);
                u.setMemoryPrice(mudf);
                u.setInventory(JSONObjectUtil.toJsonString(vmInstance));
                u.setDateInLong(date.getTime());
                u.setName(vmInstance.getName());
                u.setDate();
                u.setState(state.toString());
                ops.insert(u);
            }
        }

        // state: R -> S -> R -> S -> R -> S
        Date baseDate = new Date();
        CreatePrice cp = new CreatePrice(vm);
        Date date1 = new Date(baseDate.getTime() + TimeUnit.DAYS.toMillis(1));
        cp.create(VmInstanceState.Running, date1);
        Date date2 = new Date(date1.getTime() + TimeUnit.DAYS.toMillis(2));
        cp.create(VmInstanceState.Stopped, date2);
        Date date3 = new Date(date2.getTime() + TimeUnit.DAYS.toMillis(6));
        cp.create(VmInstanceState.Running, date3);
        Date date4 = new Date(date3.getTime() + TimeUnit.DAYS.toMillis(2));
        cp.create(VmInstanceState.Stopped, date4);
        Date date5 = new Date(date4.getTime() + TimeUnit.DAYS.toMillis(10));
        cp.create(VmInstanceState.Running, date5);
        Date date6 = new Date(date5.getTime() + TimeUnit.DAYS.toMillis(7));
        cp.create(VmInstanceState.Stopped, date6);

        long during1 = date2.getTime() - date1.getTime();
        long during2 = date4.getTime() - date3.getTime();
        long during3 = date6.getTime() - date5.getTime();
        long duringInSeconds = TimeUnit.MILLISECONDS.toSeconds(during1 + during2 + during3);

        logger.debug(String.format("expected seconds[during1: %s, during2: %s, during3: %s total: %s]",
                TimeUnit.MILLISECONDS.toSeconds(during1),
                TimeUnit.MILLISECONDS.toSeconds(during2),
                TimeUnit.MILLISECONDS.toSeconds(during3),
                duringInSeconds));

        APICalculateAccountSpendingReply reply = api.calculateSpending(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID, null);

        float cpuPrice = vm.getCpuNum() * cprice * duringInSeconds;
        float memPrice = vm.getMemorySize() * mprice * duringInSeconds;
        Assert.assertEquals(reply.getTotal(), cpuPrice + memPrice, 0.02);
        check(reply, cpuPrice, memPrice);

        // S -> S -> S -> R -> R -> R -> S
        cp = new CreatePrice(vm1);
        Date date11 = new Date(baseDate.getTime() + TimeUnit.DAYS.toMillis(1));
        cp.create(VmInstanceState.Stopped, date11);
        Date date22 = new Date(date11.getTime() + TimeUnit.DAYS.toMillis(2));
        cp.create(VmInstanceState.Stopped, date22);
        Date date33 = new Date(date22.getTime() + TimeUnit.DAYS.toMillis(6));
        cp.create(VmInstanceState.Running, date33);
        Date date44 = new Date(date33.getTime() + TimeUnit.DAYS.toMillis(2));
        cp.create(VmInstanceState.Running, date44);
        Date date55 = new Date(date44.getTime() + TimeUnit.DAYS.toMillis(10));
        cp.create(VmInstanceState.Running, date55);
        Date date66 = new Date(date55.getTime() + TimeUnit.DAYS.toMillis(7));
        cp.create(VmInstanceState.Stopped, date66);
        long during11 = date66.getTime() - date33.getTime();
        duringInSeconds = TimeUnit.MILLISECONDS.toSeconds(during11);

        float cpuPrice11 = vm1.getCpuNum() * cprice * duringInSeconds;
        float memPrice11 = vm1.getMemorySize() * mprice * duringInSeconds;

        reply = api.calculateSpending(AccountConstant.INITIAL_SYSTEM_ADMIN_UUID, null);
        Assert.assertEquals(reply.getTotal(), cpuPrice + memPrice + cpuPrice11 + memPrice11, 0.02);
        check(reply, cpuPrice + cpuPrice11, memPrice + memPrice11);
    }
}
