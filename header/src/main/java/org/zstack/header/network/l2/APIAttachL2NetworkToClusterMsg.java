package org.zstack.header.network.l2;

import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
/**
 * @api
 *
 * attach l2Network to cluster
 *
 * @category l2network
 *
 * @since 0.1.0
 *
 * @cli
 *
 * @httpMsg
 * {
"org.zstack.header.network.l2.APIAttachL2NetworkToClusterMsg": {
"l2NetworkUuid": "a766f7dec6e5477f9842289950b51e63",
"clusterUuid": "cb97e076b2e7497d9d4018fb4b4cfcea",
"session": {
"uuid": "d93f354c4339450e8c2a4c31de89da15"
}
}
}
 * @msg
 * {
"org.zstack.header.network.l2.APIAttachL2NetworkToClusterMsg": {
"l2NetworkUuid": "a766f7dec6e5477f9842289950b51e63",
"clusterUuid": "cb97e076b2e7497d9d4018fb4b4cfcea",
"session": {
"uuid": "d93f354c4339450e8c2a4c31de89da15"
},
"timeout": 1800000,
"id": "88c2cf5b89ed4c528091d1c69872f83e",
"serviceId": "api.portal"
}
}
 *
 * @result
 *
 * see :ref:`APIAttachL2NetworkToClusterEvent`
 */
public class APIAttachL2NetworkToClusterMsg extends APIMessage implements L2NetworkMessage {
    /**
     * @desc l2Network uuid
     */
    @APIParam(resourceType = L2NetworkVO.class)
    private String l2NetworkUuid;
    /**
     * @desc cluster uuid. See :ref:`ClusterInventory`
     */
    @APIParam(resourceType =  ClusterVO.class)
    private String clusterUuid;
    
    @Override
    public String getL2NetworkUuid() {
        return l2NetworkUuid;
    }

    public String getClusterUuid() {
        return clusterUuid;
    }

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    public void setL2NetworkUuid(String l2NetworkUuid) {
        this.l2NetworkUuid = l2NetworkUuid;
    }
}
