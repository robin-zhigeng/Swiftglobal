package com.higgsblock.global.chain.app.keyvalue.core;

import com.alibaba.fastjson.TypeReference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.higgsblock.global.chain.app.keyvalue.db.ILevelDbWriteBatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.iq80.leveldb.WriteBatch;
import org.springframework.data.keyvalue.core.ForwardingCloseableIterator;
import org.springframework.data.util.CloseableIterator;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author baizhengwen
 * @date 2018-08-29
 */
@Slf4j
public class TransactionAwareLevelDbAdapter extends BaseKeyValueAdapter implements ITransactionAwareKeyValueAdapter, IndexedKeyValueAdapter {


    private volatile boolean isAutoCommit = true;
    private volatile int transactionHolder;

    private ILevelDbWriteBatch writeBatch;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private Lock readLock = lock.readLock();
    private Lock writeLock = lock.writeLock();

    private MultiLevelDbKeyValueAdapter levelDbAdapter;
    private LevelDbBatchSupporter batchSupporter;

    public TransactionAwareLevelDbAdapter(MultiLevelDbKeyValueAdapter levelDbAdapter) {
        super(new IndexedSpelQueryEngine());
        this.levelDbAdapter = levelDbAdapter;
        batchSupporter = new LevelDbBatchSupporter(levelDbAdapter.getDb("_batch"));
    }

    @Override
    public void beginTransaction() {
        LOGGER.debug("try to get a WriteLock");
        writeLock.lock();
        LOGGER.debug("get a WriteLock");
        archive();
        if (transactionHolder == 0) {
            writeBatch = batchSupporter.createWriteBatch();
            batchSupporter.addBatchNo(writeBatch.getBatchNo());
            transactionHolder = 1;
            isAutoCommit = false;
        } else {
            transactionHolder++;
        }
    }

    @Override
    public void rollbackTransaction() {
        try {
            if (transactionHolder > 1) {
                transactionHolder--;
            } else {
                if (null != writeBatch) {
                    batchSupporter.removeWriteBatch(writeBatch.getBatchNo());
                    writeBatch = null;
                }
                transactionHolder = 0;
                isAutoCommit = true;
            }
        } finally {
            writeLock.unlock();
            LOGGER.debug("unlock a WriteLock");
        }
    }

    @Override
    public void commitTransaction() {
        if (transactionHolder > 1) {
            transactionHolder--;
        } else {
            batchSupporter.writeWriteBatch(writeBatch);
            writeBatch = null;
            transactionHolder = 0;
            isAutoCommit = true;
            archive();
            writeLock.unlock();
            LOGGER.debug("unlock a WriteLock");
        }
    }


    @Override
    public Object put(Serializable id, Object item, Serializable keyspace) {
        return doWithLock(writeLock, () -> {
            putEntityClass(keyspace, item.getClass());
            if (isAutoCommit) {
                levelDbAdapter.put(id, item, keyspace);
            } else {
                writeBatch.put(id, item, keyspace);
            }
            return item;
        });
    }

    @Override
    public boolean contains(Serializable id, Serializable keyspace) {
        return doWithLock(readLock, () -> super.contains(id, keyspace));
    }

    @Override
    public Object get(Serializable id, Serializable keyspace) {
        return doWithLock(readLock, () -> {
            Object result = null;
            if (!isAutoCommit && writeBatch.contains(id, keyspace)) {
                result = writeBatch.get(id, keyspace, getEntityClass(keyspace));
            }

            if (null == result) {
                result = getDataInBatches(id, keyspace);
            }

            if (null == result) {
                result = levelDbAdapter.get(id, keyspace);
            }

            return result;
        });
    }

    @Override
    public Object delete(Serializable id, Serializable keyspace) {
        return doWithLock(writeLock, () -> {
            Object result = get(id, keyspace);
            if (!isAutoCommit) {
                writeBatch.delete(id, keyspace);
            } else {
                levelDbAdapter.delete(id, keyspace);
            }
            return result;
        });
    }

    @Override
    public Iterable<?> getAllOf(Serializable keyspace) {
        return doWithLock(readLock, (Callable<Iterable<?>>) () -> super.getAllOf(keyspace));
    }

    @Override
    public CloseableIterator<Map.Entry<Serializable, Object>> entries(Serializable keyspace) {
        return doWithLock(readLock, () -> {
            Map<Serializable, Object> map = Maps.newHashMap();
            levelDbAdapter.entries(keyspace).forEachRemaining(entry -> map.put(entry.getKey(), entry.getValue()));
            Class<?> entityClass = getEntityClass(keyspace);
            map.putAll(getAllDataInBatches(keyspace, entityClass));

            if (!isAutoCommit) {
                map.putAll(writeBatch.copy(keyspace, entityClass));
            }

            Iterator<Map.Entry<Serializable, Object>> iterator = map.entrySet().stream()
                    .filter(entry -> null != entry.getValue())
                    .iterator();

            return new ForwardingCloseableIterator<>(iterator);
        });
    }

    @Override
    public void deleteAllOf(Serializable keyspace) {
        doWithLock(writeLock, () -> entries(keyspace).forEachRemaining(entry -> delete(entry.getKey(), keyspace)));
    }

    @Override
    public void clear() {
        doWithLock(writeLock, () -> {
            if (!isAutoCommit) {
                writeBatch.clear();
            }
            batchSupporter.clear();
            levelDbAdapter.clear();
        });
    }

    /**
     * Note: The statistics may not be accurate.
     *
     * @param keyspace
     * @return
     */
    @Override
    public long count(Serializable keyspace) {
        return doWithLock(readLock, () -> super.count(keyspace));
    }

    @Override
    public void destroy() {
        doWithLock(writeLock, () -> {
            if (!isAutoCommit) {
                writeBatch.clear();
            }
            batchSupporter.close();
            levelDbAdapter.destroy();
            return null;
        });
    }

    @Override
    public void write(Serializable keyspace, WriteBatch writeBatch) {
        levelDbAdapter.write(keyspace, writeBatch);
    }

    @Override
    public Collection<Serializable> saveIndex(String indexName, Serializable index, Collection<Serializable> ids, Serializable keyspace) {
        LOGGER.debug("saveIndex: keyspace={}, indexName={}, index={}", keyspace, indexName, index);
        return doWithLock(writeLock, () -> {
            if (isAutoCommit) {
                return levelDbAdapter.saveIndex(indexName, index, ids, keyspace);
            } else {
                String indexKeyspace = KeyValueAdapterUtils.getIndexKeyspace(keyspace, indexName);
                writeBatch.put(index, ids, indexKeyspace);
                return ids;
            }
        });
    }

    @Override
    public Collection<Serializable> addIndex(String indexName, Serializable index, Serializable id, Serializable keyspace) {
        LOGGER.debug("addIndex: keyspace={}, indexName={}, index={}", keyspace, indexName, index);
        return doWithLock(writeLock, () -> super.addIndex(indexName, index, id, keyspace));
    }

    @Override
    public Collection<Serializable> deleteIndex(String indexName, Serializable index, Serializable id, Serializable keyspace) {
        LOGGER.debug("deleteIndex: keyspace={}, indexName={}, index={}", keyspace, indexName, index);
        return doWithLock(writeLock, () -> {
            Collection<Serializable> ids = findIndex(indexName, index, keyspace);
            ids.remove(id);
            saveIndex(indexName, index, ids, keyspace);
            return ids;
        });
    }

    @Override
    public Collection<Serializable> findIndex(String indexName, Serializable index, Serializable keyspace) {
        LOGGER.debug("findIndex: keyspace={}, indexName={}, index={}", keyspace, indexName, index);
        return doWithLock(readLock, () -> {
            Collection<Serializable> result = Sets.newHashSet();
            String indexKeyspace = KeyValueAdapterUtils.getIndexKeyspace(keyspace, indexName);
            if (!isAutoCommit && writeBatch.contains(index, indexKeyspace)) {
                result = (Collection<Serializable>) writeBatch.get(index, indexKeyspace, result.getClass());
            }

            if (CollectionUtils.isEmpty(result)) {
                result = getIndexInBatches(index, indexKeyspace);
            }

            if (CollectionUtils.isEmpty(result)) {
                result = levelDbAdapter.findIndex(indexName, index, keyspace);
            }
            return result;
        });
    }

    protected <T> T doWithLock(Lock lock, Callable<T> callable) {
        LOGGER.debug("try to get a {}", lock.getClass().getSimpleName());
        lock.lock();
        LOGGER.debug("get a {}", lock.getClass().getSimpleName());
        try {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            lock.unlock();
            LOGGER.debug("unlock a {}", lock.getClass().getSimpleName());
        }
    }

    protected void doWithLock(Lock lock, Runnable runnable) {
        LOGGER.debug("try to get a {}", lock.getClass().getSimpleName());
        lock.lock();
        LOGGER.debug("get a {}", lock.getClass().getSimpleName());
        try {
            try {
                runnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            lock.unlock();
            LOGGER.debug("unlock a {}", lock.getClass().getSimpleName());
        }
    }

    @Override
    protected void addEntityClass(Serializable keyspace, Class<?> clazz) {
        levelDbAdapter.addEntityClass(keyspace, clazz);
    }

    @Override
    protected Class<?> getEntityClass(Serializable keyspace) {
        Class<?> entityClass = super.getEntityClass(keyspace);
        if (null != entityClass) {
            return entityClass;
        }
        return levelDbAdapter.getEntityClass(keyspace);
    }

    private Collection<Serializable> getIndexInBatches(Serializable id, Serializable keyspace) {
        Collection<Serializable> result = (Collection<Serializable>) getDataInBatches(id, keyspace, TypeReference.LIST_STRING);
        return null == result ? Sets.newHashSet() : Sets.newHashSet(result);
    }

    private Object getDataInBatches(Serializable id, Serializable keyspace) {
        return getDataInBatches(id, keyspace, getEntityClass(keyspace));
    }

    private Map<Serializable, Object> getAllDataInBatches(Serializable keyspace, Type type) {
        return doWithLock(readLock, () -> {
            Map<Serializable, Object> map = Maps.newHashMap();
            List<String> batchNos = batchSupporter.getBatchNoList();
            ILevelDbWriteBatch batch = null;
            for (String batchNo : batchNos) {
                batch = batchSupporter.getWriteBatch(batchNo);
                if (null != batch) {
                    map.putAll(batch.copy(keyspace, type));
                    continue;
                }
            }
            return map;
        });
    }

    private Object getDataInBatches(Serializable id, Serializable keyspace, Type type) {
        return doWithLock(readLock, () -> {
            List<String> batchNos = batchSupporter.getBatchNoList();
            Collections.reverse(batchNos);
            Object value = null;
            ILevelDbWriteBatch batch = null;
            for (String batchNo : batchNos) {
                batch = batchSupporter.getWriteBatch(batchNo);
                if (null != batch && batch.contains(id, keyspace)) {
                    value = batch.get(id, keyspace, type);
                }
                if (null != value) {
                    return value;
                }
            }
            return null;
        });
    }

    private void archive() {
        doWithLock(writeLock, () -> {
            List<String> batchNos = batchSupporter.getBatchNoList();
            LOGGER.debug("archive: batchNos={}", batchNos);
            for (String batchNo : batchNos) {
                LOGGER.debug("archive: batchNo={}", batchNo);
                batchSupporter.getWriteBatch(batchNo).wrapperByKeyspace().entrySet()
                        .parallelStream()
                        .forEach(entry -> levelDbAdapter.write(entry.getKey(), entry.getValue()));
                batchSupporter.deleteWriteBatch(batchNo);
            }
            batchSupporter.deleteBatchNo(batchNos);
        });
    }
}