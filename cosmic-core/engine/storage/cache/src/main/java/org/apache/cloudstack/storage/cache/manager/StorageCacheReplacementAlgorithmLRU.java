package org.apache.cloudstack.storage.cache.manager;

import com.cloud.configuration.Config;
import com.cloud.utils.DateUtil;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeDataFactory;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreVO;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreVO;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Calendar;
import java.util.Date;

public class StorageCacheReplacementAlgorithmLRU implements StorageCacheReplacementAlgorithm {
    @Inject
    ConfigurationDao configDao;
    @Inject
    TemplateDataFactory templateFactory;
    @Inject
    VolumeDataFactory volumeFactory;
    @Inject
    SnapshotDataFactory snapshotFactory;

    Integer unusedTimeInterval;

    public StorageCacheReplacementAlgorithmLRU() {

    }

    @PostConstruct
    public void initialize() {
        /* Avoid using configDao at this time, we can't be sure that the database is already upgraded
         * and there might be fatal errors when using a dao.
         */
        //unusedTimeInterval = NumbersUtil.parseInt(configDao.getValue(Config.StorageCacheReplacementLRUTimeInterval.key()), 30);
    }

    public void setUnusedTimeInterval(final Integer interval) {
        unusedTimeInterval = interval;
    }

    @Override
    public DataObject chooseOneToBeReplaced(final DataStore store) {
        if (unusedTimeInterval == null) {
            unusedTimeInterval = NumbersUtil.parseInt(configDao.getValue(Config.StorageCacheReplacementLRUTimeInterval.key()), 30);
        }
        final Calendar cal = Calendar.getInstance();
        cal.setTime(DateUtil.now());
        cal.add(Calendar.DAY_OF_MONTH, -unusedTimeInterval.intValue());
        final Date bef = cal.getTime();

        final QueryBuilder<TemplateDataStoreVO> sc = QueryBuilder.create(TemplateDataStoreVO.class);
        sc.and(sc.entity().getLastUpdated(), SearchCriteria.Op.LT, bef);
        sc.and(sc.entity().getState(), SearchCriteria.Op.EQ, ObjectInDataStoreStateMachine.State.Ready);
        sc.and(sc.entity().getDataStoreId(), SearchCriteria.Op.EQ, store.getId());
        sc.and(sc.entity().getDataStoreRole(), SearchCriteria.Op.EQ, store.getRole());
        sc.and(sc.entity().getRefCnt(), SearchCriteria.Op.EQ, 0);
        final TemplateDataStoreVO template = sc.find();
        if (template != null) {
            return templateFactory.getTemplate(template.getTemplateId(), store);
        }

        final QueryBuilder<VolumeDataStoreVO> volSc = QueryBuilder.create(VolumeDataStoreVO.class);
        volSc.and(volSc.entity().getLastUpdated(), SearchCriteria.Op.LT, bef);
        volSc.and(volSc.entity().getState(), SearchCriteria.Op.EQ, ObjectInDataStoreStateMachine.State.Ready);
        volSc.and(volSc.entity().getDataStoreId(), SearchCriteria.Op.EQ, store.getId());
        volSc.and(volSc.entity().getRefCnt(), SearchCriteria.Op.EQ, 0);
        final VolumeDataStoreVO volume = volSc.find();
        if (volume != null) {
            return volumeFactory.getVolume(volume.getVolumeId(), store);
        }

        final QueryBuilder<SnapshotDataStoreVO> snapshotSc = QueryBuilder.create(SnapshotDataStoreVO.class);
        snapshotSc.and(snapshotSc.entity().getLastUpdated(), SearchCriteria.Op.LT, bef);
        snapshotSc.and(snapshotSc.entity().getState(), SearchCriteria.Op.EQ, ObjectInDataStoreStateMachine.State.Ready);
        snapshotSc.and(snapshotSc.entity().getDataStoreId(), SearchCriteria.Op.EQ, store.getId());
        snapshotSc.and(snapshotSc.entity().getRole(), SearchCriteria.Op.EQ, store.getRole());
        snapshotSc.and(snapshotSc.entity().getRefCnt(), SearchCriteria.Op.EQ, 0);
        final SnapshotDataStoreVO snapshot = snapshotSc.find();
        if (snapshot != null) {
            return snapshotFactory.getSnapshot(snapshot.getSnapshotId(), store);
        }

        return null;
    }
}
