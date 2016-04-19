package org.zstack.header.console;

import org.zstack.header.query.APIQueryReply;

import java.util.List;

/**
 * Created by xing5 on 2016/3/15.
 */
public class APIQueryConsoleProxyAgentReply extends APIQueryReply {
    private List<ConsoleProxyAgentInventory> inventories;

    public List<ConsoleProxyAgentInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<ConsoleProxyAgentInventory> inventories) {
        this.inventories = inventories;
    }
}
