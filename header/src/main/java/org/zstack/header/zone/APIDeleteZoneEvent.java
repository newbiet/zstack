package org.zstack.header.zone;

import org.zstack.header.message.APIEvent;


/**
 * @apiResult
 *
 * api event for message :ref:`APIDeleteZoneMsg`
 *
 * @example
 *
{
"org.zstack.header.zone.APIDeleteZoneEvent": {
"success": true
}
}
 *
 * @since 0.1.0
 */
public class APIDeleteZoneEvent extends APIEvent {

	public APIDeleteZoneEvent(String apiId) {
		super(apiId);
	}
	
	public APIDeleteZoneEvent() {
		super(null);
	}
}
