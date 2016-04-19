package org.zstack.header.storage.backup;

import org.zstack.header.message.APIEvent;

/**
 * Created by xing5 on 2016/4/9.
 */
public class APIReconnectBackupStorageEvent extends APIEvent {
    private BackupStorageInventory inventory;

    public APIReconnectBackupStorageEvent() {
    }

    public APIReconnectBackupStorageEvent(String apiId) {
        super(apiId);
    }

    public BackupStorageInventory getInventory() {
        return inventory;
    }

    public void setInventory(BackupStorageInventory inventory) {
        this.inventory = inventory;
    }
}
