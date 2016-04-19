package org.zstack.header.identity;

import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;

/**
 * Created by frank on 2/25/2016.
 */
@AutoQuery(replyClass = APIQueryAccountResourceRefReply.class, inventoryClass = AccountResourceRefInventory.class)
public class APIQueryAccountResourceRefMsg extends APIQueryMessage {
}
