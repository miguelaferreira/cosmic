package com.cloud.api.commands;

import com.cloud.api.response.NiciraNvpDeviceResponse;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.NiciraNvpDeviceVO;
import com.cloud.network.element.NiciraNvpElementService;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PhysicalNetworkResponse;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@APICommand(name = "listNiciraNvpDevices", responseObject = NiciraNvpDeviceResponse.class, description = "Lists Nicira NVP devices",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListNiciraNvpDevicesCmd extends BaseListCmd {
    private static final String s_name = "listniciranvpdeviceresponse";

    @Inject
    protected NiciraNvpElementService niciraNvpElementService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID, type = CommandType.UUID, entityType = PhysicalNetworkResponse.class, description = "the Physical Network ID")
    private Long physicalNetworkId;

    @Parameter(name = ApiConstants.NICIRA_NVP_DEVICE_ID, type = CommandType.UUID, entityType = NiciraNvpDeviceResponse.class, description = "nicira nvp device ID")
    private Long niciraNvpDeviceId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getNiciraNvpDeviceId() {
        return niciraNvpDeviceId;
    }

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ResourceAllocationException {
        try {
            final List<NiciraNvpDeviceVO> niciraDevices = niciraNvpElementService.listNiciraNvpDevices(this);
            final ListResponse<NiciraNvpDeviceResponse> response = new ListResponse<>();
            final List<NiciraNvpDeviceResponse> niciraDevicesResponse = new ArrayList<>();

            if (niciraDevices != null && !niciraDevices.isEmpty()) {
                for (final NiciraNvpDeviceVO niciraDeviceVO : niciraDevices) {
                    final NiciraNvpDeviceResponse niciraDeviceResponse = niciraNvpElementService.createNiciraNvpDeviceResponse(niciraDeviceVO);
                    niciraDevicesResponse.add(niciraDeviceResponse);
                }
            }

            response.setResponses(niciraDevicesResponse);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (final InvalidParameterValueException invalidParamExcp) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, invalidParamExcp.getMessage());
        } catch (final CloudRuntimeException runtimeExcp) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, runtimeExcp.getMessage());
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
