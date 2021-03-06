package com.cloud.network.rules;

import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.network.router.VirtualRouter;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.UserVmDao;
import org.apache.cloudstack.network.topology.NetworkTopologyVisitor;

public class DhcpEntryRules extends RuleApplier {

    private final NicProfile _nic;
    private final VirtualMachineProfile _profile;
    private final DeployDestination _destination;

    private NicVO _nicVo;
    private UserVmVO _userVM;

    public DhcpEntryRules(final Network network, final NicProfile nic, final VirtualMachineProfile profile, final DeployDestination destination) {
        super(network);

        _nic = nic;
        _profile = profile;
        _destination = destination;
    }

    @Override
    public boolean accept(final NetworkTopologyVisitor visitor, final VirtualRouter router) throws ResourceUnavailableException {
        _router = router;

        final UserVmDao userVmDao = visitor.getVirtualNetworkApplianceFactory().getUserVmDao();
        _userVM = userVmDao.findById(_profile.getId());

        userVmDao.loadDetails(_userVM);

        final NicDao nicDao = visitor.getVirtualNetworkApplianceFactory().getNicDao();
        _nicVo = nicDao.findById(_nic.getId());

        return visitor.visit(this);
    }

    public VirtualMachineProfile getProfile() {
        return _profile;
    }

    public DeployDestination getDestination() {
        return _destination;
    }

    public NicVO getNicVo() {
        return _nicVo;
    }

    public UserVmVO getUserVM() {
        return _userVM;
    }
}
