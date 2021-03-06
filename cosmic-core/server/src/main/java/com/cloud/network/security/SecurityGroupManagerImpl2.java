package com.cloud.network.security;

import com.cloud.agent.api.SecurityGroupRulesCmd;
import com.cloud.agent.manager.Commands;
import com.cloud.configuration.Config;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.network.security.SecurityGroupWork.Step;
import com.cloud.network.security.SecurityRule.SecurityRuleType;
import com.cloud.uservm.UserVm;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Profiler;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.mgmt.JmxUtil;
import com.cloud.vm.NicVO;
import com.cloud.vm.VirtualMachine.State;
import org.apache.cloudstack.managed.context.ManagedContext;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same as the base class -- except it uses the abstracted security group work queue
 */
public class SecurityGroupManagerImpl2 extends SecurityGroupManagerImpl {
    SecurityGroupWorkQueue _workQueue = new LocalSecurityGroupWorkQueue();
    SecurityGroupWorkTracker _workTracker;
    SecurityManagerMBeanImpl _mBean;

    @Inject
    ManagedContext _managedContext;

    WorkerThread[] _workers;
    private final Set<Long> _disabledVms = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private boolean _schedulerDisabled = false;

    @Override
    //@DB
    public void scheduleRulesetUpdateToHosts(final List<Long> affectedVms, final boolean updateSeqno, final Long delayMs) {
        if (affectedVms.size() == 0) {
            return;
        }
        if (_schedulerDisabled) {
            s_logger.debug("Security Group Mgr v2: scheduler disabled, doing nothing for " + affectedVms.size() + " vms");
            return;
        }
        final Set<Long> workItems = new TreeSet<>();
        workItems.addAll(affectedVms);
        workItems.removeAll(_disabledVms);

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Security Group Mgr v2: scheduling ruleset updates for " + affectedVms.size() + " vms " + " (unique=" + workItems.size() +
                    "), current queue size=" + _workQueue.size());
        }

        final Profiler p = new Profiler();
        p.start();
        int updated = 0;
        if (updateSeqno) {
            updated = _rulesetLogDao.createOrUpdate(workItems);
            if (updated < workItems.size()) {
                throw new CloudRuntimeException("Failed to create ruleset log entries");
            }
        }
        final int newJobs = _workQueue.submitWorkForVms(workItems);
        _mBean.logScheduledDetails(workItems);
        p.stop();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Security Group Mgr v2: done scheduling ruleset updates for " + workItems.size() + " vms: num new jobs=" + newJobs +
                    " num rows insert or updated=" + updated + " time taken=" + p.getDurationInMillis());
        }
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        _mBean = new SecurityManagerMBeanImpl(this);
        try {
            JmxUtil.registerMBean("SecurityGroupManager", "SecurityGroupManagerImpl2", _mBean);
        } catch (final Exception e) {
            s_logger.error("Failed to register MBean", e);
        }
        final boolean result = super.configure(name, params);
        final Map<String, String> configs = _configDao.getConfiguration("Network", params);
        final int bufferLength = NumbersUtil.parseInt(configs.get(Config.SecurityGroupWorkPerAgentMaxQueueSize.key()), 100);
        _workTracker = new SecurityGroupWorkTracker(_agentMgr, _answerListener, bufferLength);
        _answerListener.setWorkDispatcher(_workTracker);
        return result;
    }

    @Override
    protected void createThreadPools() {
        _workers = new WorkerThread[_numWorkerThreads];
        for (int i = 0; i < _workers.length; i++) {
            _workers[i] = new WorkerThread("SecGrp-Worker-" + i);
        }
    }

    @Override
    public boolean start() {
        for (final WorkerThread thread : _workers) {
            thread.start();
        }
        return true;
    }

    @Override
    public void work() {
        s_logger.trace("Checking the work queue");
        final List<SecurityGroupWork> workItems;
        try {
            workItems = _workQueue.getWork(1);
            for (final SecurityGroupWork work : workItems) {
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("Processing " + work.getInstanceId());
                }

                try {
                    final VmRulesetLogVO rulesetLog = _rulesetLogDao.findByVmId(work.getInstanceId());
                    if (rulesetLog == null) {
                        s_logger.warn("Could not find ruleset log for vm " + work.getInstanceId());
                        continue;
                    }
                    work.setLogsequenceNumber(rulesetLog.getLogsequence());
                    sendRulesetUpdates(work);
                    _mBean.logUpdateDetails(work.getInstanceId(), work.getLogsequenceNumber());
                } catch (final Exception e) {
                    s_logger.error("Problem during SG work " + work, e);
                    work.setStep(Step.Error);
                }
            }
        } catch (final InterruptedException e1) {
            s_logger.warn("SG work: caught InterruptException", e1);
        }
    }

    public void sendRulesetUpdates(final SecurityGroupWork work) {
        final Long userVmId = work.getInstanceId();
        final UserVm vm = _userVMDao.findById(userVmId);

        if (vm != null && vm.getState() == State.Running) {
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("SecurityGroupManager v2: found vm, " + userVmId + " state=" + vm.getState());
            }
            final Map<PortAndProto, Set<String>> ingressRules = generateRulesForVM(userVmId, SecurityRuleType.IngressRule);
            final Map<PortAndProto, Set<String>> egressRules = generateRulesForVM(userVmId, SecurityRuleType.EgressRule);
            final Long agentId = vm.getHostId();
            if (agentId != null) {
                final String privateIp = vm.getPrivateIpAddress();
                final NicVO nic = _nicDao.findByIp4AddressAndVmId(privateIp, vm.getId());
                List<String> nicSecIps = null;
                if (nic != null) {
                    if (nic.getSecondaryIp()) {
                        //get secondary ips of the vm
                        final long networkId = nic.getNetworkId();
                        nicSecIps = _nicSecIpDao.getSecondaryIpAddressesForNic(nic.getId());
                    }
                }
                final SecurityGroupRulesCmd cmd =
                        generateRulesetCmd(vm.getInstanceName(), vm.getPrivateIpAddress(), vm.getPrivateMacAddress(), vm.getId(), null, work.getLogsequenceNumber(),
                                ingressRules, egressRules, nicSecIps);
                cmd.setMsId(_serverId);
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("SecurityGroupManager v2: sending ruleset update for vm " + vm.getInstanceName() + ":ingress num rules=" +
                            cmd.getIngressRuleSet().length + ":egress num rules=" + cmd.getEgressRuleSet().length + " num cidrs=" + cmd.getTotalNumCidrs() + " sig=" +
                            cmd.getSignature());
                }
                final Commands cmds = new Commands(cmd);
                try {
                    _agentMgr.send(agentId, cmds, _answerListener);
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace("SecurityGroupManager v2: sent ruleset updates for " + vm.getInstanceName() + " curr queue size=" + _workQueue.size());
                    }
                } catch (final AgentUnavailableException e) {
                    s_logger.debug("Unable to send updates for vm: " + userVmId + "(agentid=" + agentId + ")");
                    _workTracker.handleException(agentId);
                }
            }
        } else {
            if (s_logger.isDebugEnabled()) {
                if (vm != null) {
                    s_logger.debug("No rules sent to vm " + vm + "state=" + vm.getState());
                } else {
                    s_logger.debug("Could not find vm: No rules sent to vm " + userVmId);
                }
            }
        }
    }

    /*
     * Same as the superclass, except that we use the  ip address(es) returned from the join
     * made with the nics table when retrieving the SecurityGroupVmMapVO. If a vm has a single
     * nic then that nic is the default and then this query is correct. If the vm has multiple nics
     * then we get all ips, including the default nic ip. This is also probably the correct behavior.
     */
    @Override
    protected Map<PortAndProto, Set<String>> generateRulesForVM(final Long userVmId, final SecurityRuleType type) {

        final Map<PortAndProto, Set<String>> allowed = new TreeMap<>();

        final List<SecurityGroupVMMapVO> groupsForVm = _securityGroupVMMapDao.listByInstanceId(userVmId);
        for (final SecurityGroupVMMapVO mapVO : groupsForVm) {
            final List<SecurityGroupRuleVO> rules = _securityGroupRuleDao.listBySecurityGroupId(mapVO.getSecurityGroupId(), type);
            for (final SecurityGroupRuleVO rule : rules) {
                final PortAndProto portAndProto = new PortAndProto(rule.getProtocol(), rule.getStartPort(), rule.getEndPort());
                Set<String> cidrs = allowed.get(portAndProto);
                if (cidrs == null) {
                    cidrs = new TreeSet<>(new CidrComparator());
                }
                if (rule.getAllowedNetworkId() != null) {
                    final List<SecurityGroupVMMapVO> allowedInstances = _securityGroupVMMapDao.listBySecurityGroup(rule.getAllowedNetworkId(), State.Running);
                    for (final SecurityGroupVMMapVO ngmapVO : allowedInstances) {
                        //here, we differ from the superclass: instead of creating N more queries to the
                        //nics table, we use what's already there in the VO since the listBySecurityGroup already
                        //did a join with the nics table
                        final String cidr = ngmapVO.getGuestIpAddress() + "/32";
                        cidrs.add(cidr);
                    }
                } else if (rule.getAllowedSourceIpCidr() != null) {
                    cidrs.add(rule.getAllowedSourceIpCidr());
                }
                if (cidrs.size() > 0) {
                    allowed.put(portAndProto, cidrs);
                }
            }
        }

        return allowed;
    }

    @Override
    public void cleanupFinishedWork() {
        //TODO: over time clean up op_vm_ruleset_log table for destroyed vms
    }

    public int getQueueSize() {
        return _workQueue.size();
    }

    public SecurityGroupWorkQueue getWorkQueue() {
        return _workQueue;
    }

    public void disableSchedulerForVm(final Long vmId, final boolean disable) {
        if (disable) {
            _disabledVms.add(vmId);
        } else {
            _disabledVms.remove(vmId);
        }
        s_logger.warn("JMX operation: Scheduler state for vm " + vmId + ": new state disabled=" + disable);
    }

    public Long[] getDisabledVmsForScheduler() {
        final Long[] result = new Long[_disabledVms.size()];
        return _disabledVms.toArray(result);
    }

    public void enableAllVmsForScheduler() {
        s_logger.warn("Cleared list of disabled VMs (JMX operation?)");
        _disabledVms.clear();
    }

    public void disableScheduler(final boolean disable) {
        _schedulerDisabled = disable;
        s_logger.warn("JMX operation: Scheduler state changed: new state disabled=" + disable);
    }

    public boolean isSchedulerDisabled() {
        return _schedulerDisabled;
    }

    public void clearWorkQueue() {
        _workQueue.clear();
        s_logger.warn("Cleared the work queue (possible JMX operation)");
    }

    protected class WorkerThread extends Thread {
        public WorkerThread(final String name) {
            super(name);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    _managedContext.runWithContext(new Runnable() {
                        @Override
                        public void run() {
                            work();
                        }
                    });
                } catch (final Throwable th) {
                    s_logger.error("SG Work: Caught this throwable, ", th);
                }
            }
        }
    }
}
