package org.zstack.header.volume;

import org.zstack.header.configuration.DiskOfferingEO;
import org.zstack.header.image.ImageEO;
import org.zstack.header.storage.primary.PrimaryStorageEO;
import org.zstack.header.vm.VmInstanceEO;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;
import org.zstack.header.vo.Index;

import javax.persistence.*;
import java.sql.Timestamp;

@MappedSuperclass
public class VolumeAO {
    @Id
    @Column
    private String uuid;

    @Column
    @Index
    private String name;

    @Column
    private String description;

    @Column
    @ForeignKey(parentEntityClass = PrimaryStorageEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String primaryStorageUuid;

    @Column
    @ForeignKey(parentEntityClass = VmInstanceEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String vmInstanceUuid;

    @Column
    @ForeignKey(parentEntityClass = DiskOfferingEO.class, onDeleteAction = ReferenceOption.RESTRICT)
    private String diskOfferingUuid;

    @Column
    @ForeignKey(parentEntityClass = ImageEO.class, onDeleteAction = ReferenceOption.SET_NULL)
    private String rootImageUuid;

    @Column
    private String installPath;

    @Column
    @Enumerated(EnumType.STRING)
    private VolumeType type;

    @Column
    @Enumerated(EnumType.STRING)
    private VolumeStatus status;

    @Column
    private long size;

    @Column
    private Integer deviceId;

    @Column
    private String format;

    @Column
    @Enumerated(EnumType.STRING)
    private VolumeState state;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    public VolumeAO() {
        this.state = VolumeState.Enabled;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDiskOfferingUuid() {
        return diskOfferingUuid;
    }

    public void setDiskOfferingUuid(String diskOfferingUuid) {
        this.diskOfferingUuid = diskOfferingUuid;
    }

    public String getUuid() {
    	return uuid;
    }

	public void setUuid(String uuid) {
    	this.uuid = uuid;
    }

	public String getName() {
    	return name;
    }

	public void setName(String name) {
    	this.name = name;
    }

	public String getDescription() {
    	return description;
    }

	public void setDescription(String description) {
    	this.description = description;
    }

	public String getPrimaryStorageUuid() {
    	return primaryStorageUuid;
    }

	public void setPrimaryStorageUuid(String primaryStorageUuid) {
    	this.primaryStorageUuid = primaryStorageUuid;
    }

	public String getVmInstanceUuid() {
    	return vmInstanceUuid;
    }

	public void setVmInstanceUuid(String vmInstanceUuid) {
    	this.vmInstanceUuid = vmInstanceUuid;
    }

	public String getInstallPath() {
    	return installPath;
    }

	public void setInstallPath(String installPath) {
    	this.installPath = installPath;
    }

	public VolumeType getType() {
    	return type;
    }

	public void setType(VolumeType volumeType) {
    	this.type = volumeType;
    }

	public long getSize() {
    	return size;
    }

	public void setSize(long size) {
    	this.size = size;
    }

	public VolumeState getState() {
    	return state;
    }

	public void setState(VolumeState state) {
    	this.state = state;
    }

	public boolean isAttached() {
    	return this.vmInstanceUuid != null;
    }

    public String getRootImageUuid() {
        return rootImageUuid;
    }

    public void setRootImageUuid(String rootImageUuid) {
        this.rootImageUuid = rootImageUuid;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastOpDate() {
        return lastOpDate;
    }

    public void setLastOpDate(Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }

    public Integer getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public VolumeStatus getStatus() {
        return status;
    }

    public void setStatus(VolumeStatus status) {
        this.status = status;
    }
}
