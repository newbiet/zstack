package org.zstack.storage.primary.nfs;

import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.host.HostInventory;
import org.zstack.header.image.ImageInventory;
import org.zstack.header.storage.backup.BackupStorageInventory;
import org.zstack.header.storage.primary.*;
import org.zstack.header.storage.snapshot.VolumeSnapshotInventory;
import org.zstack.header.volume.VolumeInventory;

public interface NfsPrimaryToBackupStorageMediator extends PrimaryToBackupStorageMediator {
    void createVolumeFromImageCache(PrimaryStorageInventory primaryStorage, ImageCacheInventory image,
                                    VolumeInventory volume, ReturnValueCompletion<String> completion);

    void downloadBits(PrimaryStorageInventory pinv, BackupStorageInventory bsinv, String backupStorageInstallPath, String primaryStorageInstallPath, Completion completion);

    void uploadBits(PrimaryStorageInventory pinv, BackupStorageInventory bsinv, String backupStorageInstallPath, String primaryStorageInstallPath, Completion completion);

    String makeRootVolumeTemplateInstallPath(String backupStorageUuid, String imageUuid);

    String makeVolumeSnapshotInstallPath(String backupStorageUuid, String snapshotUuid);

    String makeDataVolumeTemplateInstallPath(String backupStorageUuid, String imageUuid);
}
