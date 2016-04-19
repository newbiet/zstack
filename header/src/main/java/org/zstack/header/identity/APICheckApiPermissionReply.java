package org.zstack.header.identity;

import org.zstack.header.message.APIReply;

import java.util.Map;

/**
 * Created by xing5 on 2016/3/10.
 */
public class APICheckApiPermissionReply extends APIReply {
    private Map<String, String> inventory;

    public Map<String, String> getInventory() {
        return inventory;
    }

    public void setInventory(Map<String, String> inventory) {
        this.inventory = inventory;
    }
}
