package com.higgsblock.global.chain.app.keyvalue.core;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.higgsblock.global.chain.app.keyvalue.db.ILevelDb;
import com.higgsblock.global.chain.app.keyvalue.db.ILevelDbWriteBatch;
import com.higgsblock.global.chain.app.keyvalue.db.LevelDb;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.WriteOptions;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.springframework.data.keyvalue.core.AbstractKeyValueAdapter;
import org.springframework.data.keyvalue.core.ForwardingCloseableIterator;
import org.springframework.data.util.CloseableIterator;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author baizhengwen
 * @date 2018-08-24
 */
@Slf4j
public class LevelDbKeyValueAdapter extends AbstractKeyValueAdapter implements IndexedKeyValueAdapter {

    protected String dataPath;
    protected ReadOptions readOptions = new ReadOptions();
    protected WriteOptions writeOptions = new WriteOptions().sync(true);
    protected Options options = new Options()
            .createIfMissing(true)
            .compressionType(CompressionType.SNAPPY);
    protected ILevelDb<String> db;

    public LevelDbKeyValueAdapter(String dataPath) {
        super(new IndexedSpelQueryEngine());
        this.dataPath = dataPath;
        db = new LevelDb<>(dataPath);
    }

    @Override
    public Object put(Serializable id, Object item, Serializable keyspace) {
        String key = KeyValueAdapterUtils.getFullKey(keyspace, id);
        db.put(key, String.valueOf(item), writeOptions);
        return item;
    }

    @Override
    public boolean contains(Serializable id, Serializable keyspace) {
        return null != get(id, keyspace);
    }

    @Override
    public Object get(Serializable id, Serializable keyspace) {
        String key = KeyValueAdapterUtils.getFullKey(keyspace, id);
        return db.get(key, readOptions);
    }

    @Override
    public <T> T get(Serializable id, Serializable keyspace, Class<T> type) {
        return (T) get(id, keyspace);
    }

    @Override
    public Object delete(Serializable id, Serializable keyspace) {
        Object value = get(id, keyspace);
        String key = KeyValueAdapterUtils.getFullKey(keyspace, id);
        db.delete(key, writeOptions);
        return value;
    }

    @Override
    public <T> T delete(Serializable id, Serializable keyspace, Class<T> type) {
        return (T) delete(id, keyspace);
    }

    @Override
    public Iterable<?> getAllOf(Serializable keyspace) {
        List<Object> list = Lists.newLinkedList();
        entries(keyspace).forEachRemaining(entry -> list.add(entry.getValue()));
        return list;
    }

    @Override
    public CloseableIterator<Map.Entry<Serializable, Object>> entries(Serializable keyspace) {
        String prefix = KeyValueAdapterUtils.getKeyPrefix(keyspace);
        Map<Serializable, Object> map = Maps.newHashMap();

        db.iterator(readOptions).forEachRemaining(entry -> {
            String key = entry.getKey();
            if (StringUtils.isNotEmpty(key) && key.startsWith(prefix)) {
                Object value = entry.getValue();
                if (null != value) {
                    map.put(key, value);
                }
            }
        });

        return new ForwardingCloseableIterator<>(map.entrySet().iterator());
    }

    @Override
    public void deleteAllOf(Serializable keyspace) {
        String prefix = KeyValueAdapterUtils.getKeyPrefix(keyspace);
        db.iterator(readOptions).forEachRemaining(entry -> {
            String key = entry.getKey();
            if (StringUtils.isNotEmpty(key) && key.startsWith(prefix)) {
                db.delete(key, writeOptions);
            }
        });
    }

    @Override
    public void clear() {
        try {
            destroy();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public long count(Serializable keyspace) {
        String prefix = KeyValueAdapterUtils.getKeyPrefix(keyspace);
        Iterator<Map.Entry<String, String>> iterator = db.iterator(readOptions);
        long num = 0L;
        String key = null;
        while (iterator.hasNext()) {
            key = iterator.next().getKey();
            if (StringUtils.isNotEmpty(key) && key.startsWith(prefix)) {
                num++;
            }
        }
        return num;
    }

    @Override
    public void destroy() throws Exception {
        Iq80DBFactory.factory.destroy(new File(dataPath), options);
    }

    public ILevelDbWriteBatch createWriteBatch() {
        return db.createWriteBatch();
    }

    public void write(ILevelDbWriteBatch writeBatch) {
        db.write(writeBatch, writeOptions);
    }

    @Override
    public Collection<Serializable> addIndex(String indexName, Serializable index, Serializable id, Serializable keyspace) {
        LOGGER.debug("addIndex: keyspace={}, indexName={}, index={}", keyspace, indexName, index);
        Collection<Serializable> ids = findIndex(indexName, index, keyspace);
        ids.add(id);
        db.put(KeyValueAdapterUtils.getFullKey(keyspace, indexName, index), KeyValueAdapterUtils.toJsonString(ids));
        return ids;
    }

    @Override
    public Collection<Serializable> deleteIndex(String indexName, Serializable index, Serializable id, Serializable keyspace) {
        LOGGER.debug("deleteIndex: keyspace={}, indexName={}, index={}", keyspace, indexName, index);
        Collection<Serializable> ids = findIndex(indexName, index, keyspace);
        ids.remove(id);
        db.put(KeyValueAdapterUtils.getFullKey(keyspace, indexName, index), KeyValueAdapterUtils.toJsonString(ids));
        return ids;
    }

    @Override
    public Collection<Serializable> findIndex(String indexName, Serializable index, Serializable keyspace) {
        LOGGER.debug("findIndex: keyspace={}, indexName={}, index={}", keyspace, indexName, index);
        String key = KeyValueAdapterUtils.getFullKey(keyspace, indexName, index);
        Collection<Serializable> ids = KeyValueAdapterUtils.parseJsonArrayString(db.get(key, readOptions), Serializable.class);
        if (null == ids) {
            ids = Sets.newHashSet();
        }
        return ids;
    }

}
