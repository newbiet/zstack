package org.zstack.storage.primary.local;

import org.zstack.header.query.APIQueryReply;

import java.util.List;

/**
 * Created by frank on 11/14/2015.
 */
public class APIQueryLocalStorageResourceRefReply extends APIQueryReply {
    private List<LocalStorageResourceRefInventory> inventories;

    public List<LocalStorageResourceRefInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<LocalStorageResourceRefInventory> inventories) {
        this.inventories = inventories;
    }
}
