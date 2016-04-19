package com.zstack.utils.test;

import org.apache.commons.net.util.SubnetUtils;
import org.junit.Test;
import org.zstack.utils.network.NetworkUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.zstack.utils.CollectionDSL.list;

/**
 * Created by frank on 8/11/2015.
 */
public class TestEval {
    public static class A {
    }

    @Test
    public void test() throws IOException {
        List<String> lst = list("CloudBus.serverIp.1", "CloudBus.serverIp.5", "CloudBus.serverIp.2", "CloudBus.serverIp.0");
        Collections.sort(lst);
        System.out.println(lst);

        System.out.println(NetworkUtils.isNetmask("255.255.255.0"));
        System.out.println(NetworkUtils.isNetmask("255.255.255.088"));
        System.out.println(NetworkUtils.isNetmaskExcept("0.0.0.0", "0.0.0.0"));

        SubnetUtils sub = new SubnetUtils("192.168.0.10/16");
        System.out.println(sub.getInfo().isInRange("192.168.0.3"));
    }
}
