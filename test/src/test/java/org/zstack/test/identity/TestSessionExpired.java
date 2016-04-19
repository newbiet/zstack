package org.zstack.test.identity;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.config.GlobalConfigFacade;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.identity.AccountInventory;
import org.zstack.header.identity.AccountVO;
import org.zstack.header.identity.SessionInventory;
import org.zstack.identity.IdentityGlobalConfig;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.BeanConstructor;
import org.zstack.test.DBUtil;

import java.util.concurrent.TimeUnit;

public class TestSessionExpired {
    Api api;
    ComponentLoader loader;
    DatabaseFacade dbf;
    GlobalConfigFacade gcf;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        BeanConstructor con = new BeanConstructor();
        /* This loads spring application context */
        loader = con.addXml("PortalForUnitTest.xml").addXml("AccountManager.xml").build();
        dbf = loader.getComponent(DatabaseFacade.class);
        gcf = loader.getComponent(GlobalConfigFacade.class);
        api = new Api();    
        api.startServer();
    }
    
    
    @Test(expected=ApiSenderException.class)
    public void test() throws ApiSenderException, InterruptedException {
        IdentityGlobalConfig.SESSION_TIMEOUT.updateValue(1);
        AccountInventory inv = api.createAccount("Test", "Test");
        AccountVO vo = dbf.findByUuid(inv.getUuid(), AccountVO.class);
        Assert.assertNotNull(vo);
        SessionInventory session = api.loginByAccount(inv.getName(), vo.getPassword());
        api.setAdminSession(session);
        TimeUnit.SECONDS.sleep(3);
        api.listAccount(null);
   }

}
