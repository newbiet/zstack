package org.zstack.test.identity;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.identity.*;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.BeanConstructor;
import org.zstack.test.DBUtil;

public class TestCreateUser {
    Api api;
    ComponentLoader loader;
    DatabaseFacade dbf;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        BeanConstructor con = new BeanConstructor();
        /* This loads spring application context */
        loader = con.addXml("PortalForUnitTest.xml").addXml("AccountManager.xml").build();
        dbf = loader.getComponent(DatabaseFacade.class);
        api = new Api();
        api.startServer();
    }
    
    @Test
    public void test() throws ApiSenderException {
        AccountInventory inv = api.createAccount("Test", "Test");
        AccountVO vo = dbf.findByUuid(inv.getUuid(), AccountVO.class);
        Assert.assertNotNull(vo);
        Assert.assertEquals("Test", vo.getPassword());
        
        SessionInventory session = api.loginByAccount(inv.getName(), vo.getPassword());
        UserInventory uinv = api.createUser(inv.getUuid(), "TestUser", "password", session);
        UserVO uvo = dbf.findByUuid(uinv.getUuid(), UserVO.class);
        Assert.assertNotNull(uvo);
        Assert.assertEquals("password", uvo.getPassword());
    }
}
