package org.zstack.header.storage.backup;

import org.zstack.header.image.ImageInventory;
import org.zstack.header.message.NeedReplyMessage;

public class DownloadImageMsg extends NeedReplyMessage implements BackupStorageMessage {
	private ImageInventory imageInventory;
    private String backupStorageUuid;

	public DownloadImageMsg(ImageInventory inventory) {
	    super();
	    this.imageInventory = inventory;
    }

    public void setBackupStorageUuid(String backupStorageUuid) {
        this.backupStorageUuid = backupStorageUuid;
    }

    public ImageInventory getImageInventory() {
        return imageInventory;
    }

    public void setImageInventory(ImageInventory imageInventory) {
        this.imageInventory = imageInventory;
    }

    @Override
    public String getBackupStorageUuid() {
        return backupStorageUuid;
    }
}
