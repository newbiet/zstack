package org.zstack.header.storage.primary;

import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.message.APIParam;
import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.zone.ZoneVO;

import java.util.List;

/**
 */
public class APIGetPrimaryStorageCapacityMsg extends APISyncCallMessage {
    @APIParam(required = false, resourceType = ZoneVO.class)
    private List<String> zoneUuids;
    @APIParam(required = false, resourceType = ClusterVO.class)
    private List<String> clusterUuids;
    @APIParam(required = false, resourceType = PrimaryStorageVO.class)
    private List<String> primaryStorageUuids;
    private boolean all;

    public List<String> getZoneUuids() {
        return zoneUuids;
    }

    public void setZoneUuids(List<String> zoneUuids) {
        this.zoneUuids = zoneUuids;
    }

    public List<String> getClusterUuids() {
        return clusterUuids;
    }

    public void setClusterUuids(List<String> clusterUuids) {
        this.clusterUuids = clusterUuids;
    }

    public List<String> getPrimaryStorageUuids() {
        return primaryStorageUuids;
    }

    public void setPrimaryStorageUuids(List<String> primaryStorageUuids) {
        this.primaryStorageUuids = primaryStorageUuids;
    }

    public boolean isAll() {
        return all;
    }

    public void setAll(boolean all) {
        this.all = all;
    }
}
