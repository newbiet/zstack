package org.zstack.header.identity;

import org.zstack.header.query.APIQueryReply;

import java.util.List;

/**
 * Created by frank on 2/25/2016.
 */
public class APIQueryAccountResourceRefReply extends APIQueryReply {
    private List<AccountResourceRefInventory> inventories;

    public List<AccountResourceRefInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<AccountResourceRefInventory> inventories) {
        this.inventories = inventories;
    }
}
