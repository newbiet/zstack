package org.zstack.storage.primary.nfs;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.core.timeout.ApiTimeoutManager;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.host.HostConstant;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HypervisorType;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.backup.BackupStorageConstant;
import org.zstack.header.storage.backup.BackupStorageInventory;
import org.zstack.header.storage.backup.BackupStorageType;
import org.zstack.header.storage.primary.ImageCacheInventory;
import org.zstack.header.storage.primary.PrimaryStorageInventory;
import org.zstack.header.storage.primary.PrimaryStorageType;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.identity.AccountManager;
import org.zstack.kvm.*;
import org.zstack.storage.backup.BackupStoragePathMaker;
import org.zstack.storage.backup.sftp.*;
import org.zstack.storage.primary.nfs.NfsPrimaryStorageKVMBackendCommands.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.path.PathUtil;

import java.util.List;

import static org.zstack.utils.CollectionDSL.list;

public class NfsPrimaryToSftpBackupKVMBackend implements NfsPrimaryToBackupStorageMediator {
    private static final CLogger logger = Utils.getLogger(NfsPrimaryToSftpBackupKVMBackend.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private NfsPrimaryStorageFactory primaryStorageFactory;
    @Autowired
    private AccountManager acntMgr;
    @Autowired
    private ErrorFacade errf;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private NfsPrimaryStorageManager nfsMgr;
    @Autowired
    private ApiTimeoutManager timeoutMgr;

    public static final String CREATE_VOLUME_FROM_TEMPLATE_PATH = "/nfsprimarystorage/sftp/createvolumefromtemplate";
    public static final String UPLOAD_TO_SFTP_PATH = "/nfsprimarystorage/uploadtosftpbackupstorage";
    public static final String DOWNLOAD_FROM_SFTP_PATH = "/nfsprimarystorage/downloadfromsftpbackupstorage";

    @Override
    public PrimaryStorageType getSupportedPrimaryStorageType() {
        return PrimaryStorageType.valueOf(NfsPrimaryStorageConstant.NFS_PRIMARY_STORAGE_TYPE);
    }

    @Override
    public BackupStorageType getSupportedBackupStorageType() {
        return BackupStorageType.valueOf(SftpBackupStorageConstant.SFTP_BACKUP_STORAGE_TYPE);
    }

    @Override
    public List<HypervisorType> getSupportedHypervisorTypes() {
        return list(HypervisorType.valueOf(KVMConstant.KVM_HYPERVISOR_TYPE));
    }

    @Override
    public void createVolumeFromImageCache(final PrimaryStorageInventory primaryStorage, final ImageCacheInventory image,
                                           final VolumeInventory volume, final ReturnValueCompletion<String> completion) {
        HostInventory host = primaryStorageFactory.getConnectedHostForOperation(primaryStorage);

        final String installPath = NfsPrimaryStorageKvmHelper.makeRootVolumeInstallUrl(primaryStorage, volume);
        final String accountUuid = acntMgr.getOwnerAccountUuidOfResource(volume.getUuid());
        final CreateRootVolumeFromTemplateCmd cmd = new CreateRootVolumeFromTemplateCmd();
        cmd.setTemplatePathInCache(image.getInstallUrl());
        cmd.setInstallUrl(installPath);
        cmd.setAccountUuid(accountUuid);
        cmd.setName(volume.getName());
        cmd.setVolumeUuid(volume.getUuid());
        cmd.setUuid(primaryStorage.getUuid());

        KVMHostAsyncHttpCallMsg msg = new KVMHostAsyncHttpCallMsg();
        msg.setCommand(cmd);
        msg.setPath(CREATE_VOLUME_FROM_TEMPLATE_PATH);
        msg.setHostUuid(host.getUuid());
        msg.setCommandTimeout(timeoutMgr.getTimeout(cmd.getClass(), "5m"));
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, host.getUuid());
        bus.send(msg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                CreateRootVolumeFromTemplateResponse rsp = ((KVMHostAsyncHttpCallReply)reply).toResponse(CreateRootVolumeFromTemplateResponse.class);
                if (!rsp.isSuccess()) {
                    String err = String.format("fails to create root volume[uuid:%s] from cached image[path:%s] because %s",
                            volume.getUuid(), image.getImageUuid(),
                            rsp.getError());
                    logger.warn(err);
                    completion.fail(errf.stringToOperationError(err));
                    return;
                }


                nfsMgr.reportCapacityIfNeeded(primaryStorage.getUuid(), rsp);
                completion.success(installPath);
            }
        });
    }

    @Override
    public void downloadBits(final PrimaryStorageInventory pinv, final BackupStorageInventory bsinv, final String backupStorageInstallPath, final String primaryStorageInstallPath, final Completion completion) {
        GetSftpBackupStorageDownloadCredentialMsg gmsg = new GetSftpBackupStorageDownloadCredentialMsg();
        gmsg.setBackupStorageUuid(bsinv.getUuid());
        bus.makeTargetServiceIdByResourceUuid(gmsg, BackupStorageConstant.SERVICE_ID, bsinv.getUuid());
        bus.send(gmsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                HostInventory host = primaryStorageFactory.getConnectedHostForOperation(pinv);
                final GetSftpBackupStorageDownloadCredentialReply greply = reply.castReply();
                DownloadBitsFromSftpBackupStorageCmd cmd = new DownloadBitsFromSftpBackupStorageCmd();
                cmd.setHostname(greply.getHostname());
                cmd.setSshKey(greply.getSshKey());
                cmd.setPrimaryStorageInstallPath(primaryStorageInstallPath);
                cmd.setBackupStorageInstallPath(backupStorageInstallPath);
                cmd.setUuid(pinv.getUuid());

                KVMHostAsyncHttpCallMsg msg = new KVMHostAsyncHttpCallMsg();
                msg.setCommand(cmd);
                msg.setPath(DOWNLOAD_FROM_SFTP_PATH);
                msg.setHostUuid(host.getUuid());
                msg.setCommandTimeout(timeoutMgr.getTimeout(cmd.getClass(), "5m"));
                bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, host.getUuid());
                bus.send(msg, new CloudBusCallBack(completion) {
                    @Override
                    public void run(MessageReply reply) {
                        if (!reply.isSuccess()) {
                            completion.fail(reply.getError());
                            return;
                        }

                        DownloadBitsFromSftpBackupStorageResponse rsp = ((KVMHostAsyncHttpCallReply)reply).toResponse(DownloadBitsFromSftpBackupStorageResponse.class);
                        if (!rsp.isSuccess()) {
                            completion.fail(errf.stringToOperationError(
                                    String.format(String.format("failed to download[%s] from SftpBackupStorage[hostname:%s] to nfs primary storage[uuid:%s, path:%s], %s",
                                            backupStorageInstallPath, greply.getHostname(), pinv.getUuid(), primaryStorageInstallPath, rsp.getError()))
                            ));
                            return;
                        }

                        nfsMgr.reportCapacityIfNeeded(pinv.getUuid(), rsp);
                        completion.success();
                    }
                });
            }
        });
    }

    @Override
    public void uploadBits(final PrimaryStorageInventory pinv, BackupStorageInventory bsinv, final String backupStorageInstallPath, final String primaryStorageInstallPath, final Completion completion) {
        GetSftpBackupStorageDownloadCredentialMsg gmsg = new GetSftpBackupStorageDownloadCredentialMsg();
        gmsg.setBackupStorageUuid(bsinv.getUuid());
        bus.makeTargetServiceIdByResourceUuid(gmsg, BackupStorageConstant.SERVICE_ID, bsinv.getUuid());
        bus.send(gmsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                GetSftpBackupStorageDownloadCredentialReply r = reply.castReply();
                upload(r.getHostname(), r.getSshKey());
            }

            private void upload(final String hostname, String sshKey) {
                final HostInventory host = primaryStorageFactory.getConnectedHostForOperation(pinv);
                UploadToSftpCmd cmd = new UploadToSftpCmd();
                cmd.setBackupStorageHostName(hostname);
                cmd.setBackupStorageSshKey(sshKey);
                cmd.setPrimaryStorageInstallPath(primaryStorageInstallPath);
                cmd.setBackupStorageInstallPath(backupStorageInstallPath);
                cmd.setUuid(pinv.getUuid());

                KVMHostAsyncHttpCallMsg msg = new KVMHostAsyncHttpCallMsg();
                msg.setCommand(cmd);
                msg.setPath(UPLOAD_TO_SFTP_PATH);
                msg.setHostUuid(host.getUuid());
                msg.setCommandTimeout(timeoutMgr.getTimeout(cmd.getClass(), "5m"));
                bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, host.getUuid());
                bus.send(msg, new CloudBusCallBack(completion) {
                    @Override
                    public void run(MessageReply reply) {
                        if (!reply.isSuccess()) {
                            completion.fail(reply.getError());
                            return;
                        }

                        UploadToSftpResponse rsp = ((KVMHostAsyncHttpCallReply)reply).toResponse(UploadToSftpResponse.class);
                        if (!rsp.isSuccess()) {
                            completion.fail(errf.stringToOperationError(
                                    String.format(String.format("failed to upload bits from nfs primary storage[uuid:%s, path:%s] to SFTP backup storage[hostname:%s, path: %s], %s",
                                            pinv.getUuid(), primaryStorageInstallPath, hostname,  backupStorageInstallPath, rsp.getError()))));
                            return;
                        }

                        completion.success();
                    }
                });
            }
        });
    }

    private String findSftpRootPath(String bsUuid) {
        SimpleQuery<SftpBackupStorageVO> q = dbf.createQuery(SftpBackupStorageVO.class);
        q.select(SftpBackupStorageVO_.url);
        q.add(SftpBackupStorageVO_.uuid, SimpleQuery.Op.EQ, bsUuid);
        return q.findValue();
    }

    @Override
    public String makeRootVolumeTemplateInstallPath(String bsUuid, String imageUuid) {
        return PathUtil.join(
                findSftpRootPath(bsUuid),
                BackupStoragePathMaker.makeRootVolumeTemplateInstallFolderPath(imageUuid),
                String.format("%s.qcow2", imageUuid)
        );
    }

    @Override
    public String makeVolumeSnapshotInstallPath(String bsUuid, String snapshotUuid) {
        return PathUtil.join(
                findSftpRootPath(bsUuid),
                BackupStoragePathMaker.makeVolumeSnapshotInstallFolderPath(snapshotUuid),
                String.format("%s.qcow2", snapshotUuid)
        );
    }

    @Override
    public String makeDataVolumeTemplateInstallPath(String backupStorageUuid, String volumeUuid) {
        return PathUtil.join(
                findSftpRootPath(backupStorageUuid),
                BackupStoragePathMaker.makeDataVolumeTemplateInstallFolderPath(volumeUuid),
                String.format("%s.qcow2", volumeUuid)
        );
    }
}
