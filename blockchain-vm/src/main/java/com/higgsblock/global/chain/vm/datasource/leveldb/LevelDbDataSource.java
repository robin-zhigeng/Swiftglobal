/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package com.higgsblock.global.chain.vm.datasource.leveldb;


import com.higgsblock.global.chain.vm.core.SystemProperties;
import com.higgsblock.global.chain.vm.datasource.DbSettings;
import com.higgsblock.global.chain.vm.datasource.DbSource;
import com.higgsblock.global.chain.vm.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.iq80.leveldb.*;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.higgsblock.global.chain.vm.util.ByteUtil.toHexString;
import static org.fusesource.leveldbjni.JniDBFactory.factory;


/**
 * @author Roman Mandeleil
 * @since 18.01.2015
 */

@Slf4j
public class LevelDbDataSource implements DbSource<byte[]> {

    //private static final LOGGER LOGGER = LOGGERFactory.getLOGGER("db");


    SystemProperties config = SystemProperties.getDefault(); // initialized for standalone test

    String name;
    DB db;
    boolean alive;

    DbSettings settings = DbSettings.DEFAULT;

    // The native LevelDB insert/update/delete are normally thread-safe
    // However close operation is not thread-safe and may lead to a native crash when
    // accessing a closed DB.
    // The leveldbJNI lib has a protection over accessing closed DB but it is not synchronized
    // This ReadWriteLock still permits concurrent execution of insert/delete/update operations
    // however blocks them on init/close/delete operations
    private ReadWriteLock resetDbLock = new ReentrantReadWriteLock();

    public LevelDbDataSource() {
    }

    public LevelDbDataSource(String name) {
        this.name = name;
        LOGGER.debug("New LevelDbDataSource: " + name);
    }

    @Override
    public void init() {
        init(DbSettings.DEFAULT);
    }

    @Override
    public void init(DbSettings settings) {
        this.settings = settings;
        resetDbLock.writeLock().lock();
        try {
            LOGGER.debug("~> LevelDbDataSource.init(): " + name);

            if (isAlive()) return;

            if (name == null) throw new NullPointerException("no name set to the db");

            Options options = new Options();
            options.createIfMissing(true);
            options.compressionType(CompressionType.NONE);
            options.blockSize(10 * 1024 * 1024);
            options.writeBufferSize(10 * 1024 * 1024);
            options.cacheSize(0);
            options.paranoidChecks(true);
            options.verifyChecksums(true);
            options.maxOpenFiles(settings.getMaxOpenFiles());

            try {
                LOGGER.debug("Opening database");
                final Path dbPath = getPath();
                if (!Files.isSymbolicLink(dbPath.getParent())) Files.createDirectories(dbPath.getParent());

                LOGGER.debug("Initializing new or existing database: '{}'", name);
                try {
                    db = factory.open(dbPath.toFile(), options);
                } catch (IOException e) {
                    // database could be corrupted
                    // exception in std out may look:
                    // org.fusesource.leveldbjni.internal.NativeDB$DBException: Corruption: 16 missing files; e.g.: /Users/stan/ethereumj/database-test/block/000026.ldb
                    // org.fusesource.leveldbjni.internal.NativeDB$DBException: Corruption: checksum mismatch
                    if (e.getMessage().contains("Corruption:")) {
                        LOGGER.warn("Problem initializing database.", e);
                        LOGGER.info("LevelDB database must be corrupted. Trying to repair. Could take some time.");
                        factory.repair(dbPath.toFile(), options);
                        LOGGER.info("Repair finished. Opening database again.");
                        db = factory.open(dbPath.toFile(), options);
                    } else {
                        // must be db lock
                        // org.fusesource.leveldbjni.internal.NativeDB$DBException: IO error: lock /Users/stan/ethereumj/database-test/state/LOCK: Resource temporarily unavailable
                        throw e;
                    }
                }

                alive = true;
            } catch (IOException ioe) {
                LOGGER.error(ioe.getMessage(), ioe);
                throw new RuntimeException("Can't initialize database", ioe);
            }
            LOGGER.debug("<~ LevelDbDataSource.init(): " + name);
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    private Path getPath() {
        return Paths.get(config.databaseDir(), name);
    }

    @Override
    public void reset() {
        close();
        FileUtil.recursiveDelete(getPath().toString());
        init(settings);
    }

    @Override
    public byte[] prefixLookup(byte[] key, int prefixBytes) {
        throw new RuntimeException("LevelDbDataSource.prefixLookup() is not supported");
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    public void destroyDB(File fileLocation) {
        resetDbLock.writeLock().lock();
        try {
            LOGGER.debug("Destroying existing database: " + fileLocation);
            Options options = new Options();
            try {
                factory.destroy(fileLocation, options);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public byte[] get(byte[] key) {
        resetDbLock.readLock().lock();
        try {
            if (LOGGER.isTraceEnabled()) LOGGER.trace("~> LevelDbDataSource.get(): " + name + ", key: " + toHexString(key));
            try {
                byte[] ret = db.get(key);
                if (LOGGER.isTraceEnabled()) LOGGER.trace("<~ LevelDbDataSource.get(): " + name + ", key: " + toHexString(key) + ", " + (ret == null ? "null" : ret.length));
                return ret;
            } catch (DBException e) {
                LOGGER.warn("Exception. Retrying again...", e);
                byte[] ret = db.get(key);
                if (LOGGER.isTraceEnabled()) LOGGER.trace("<~ LevelDbDataSource.get(): " + name + ", key: " + toHexString(key) + ", " + (ret == null ? "null" : ret.length));
                return ret;
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void put(byte[] key, byte[] value) {
        resetDbLock.readLock().lock();
        try {
            if (LOGGER.isTraceEnabled()) LOGGER.trace("~> LevelDbDataSource.put(): " + name + ", key: " + toHexString(key) + ", " + (value == null ? "null" : value.length));
            db.put(key, value);
            if (LOGGER.isTraceEnabled()) LOGGER.trace("<~ LevelDbDataSource.put(): " + name + ", key: " + toHexString(key) + ", " + (value == null ? "null" : value.length));
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void delete(byte[] key) {
        resetDbLock.readLock().lock();
        try {
            if (LOGGER.isTraceEnabled()) LOGGER.trace("~> LevelDbDataSource.delete(): " + name + ", key: " + toHexString(key));
            db.delete(key);
            if (LOGGER.isTraceEnabled()) LOGGER.trace("<~ LevelDbDataSource.delete(): " + name + ", key: " + toHexString(key));
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public Set<byte[]> keys() {
        resetDbLock.readLock().lock();
        try {
            if (LOGGER.isTraceEnabled()) LOGGER.trace("~> LevelDbDataSource.keys(): " + name);
            try (DBIterator iterator = db.iterator()) {
                Set<byte[]> result = new HashSet<>();
                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    result.add(iterator.peekNext().getKey());

                    LOGGER.info("key: " + Hex.toHexString(iterator.peekNext().getKey()));
                    LOGGER.info("value: " + Hex.toHexString(db.get(iterator.peekNext().getKey())));
                }
                if (LOGGER.isTraceEnabled()) LOGGER.trace("<~ LevelDbDataSource.keys(): " + name + ", " + result.size());
                return result;
            } catch (IOException e) {
                LOGGER.error("Unexpected", e);
                throw new RuntimeException(e);
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    private void updateBatchInternal(Map<byte[], byte[]> rows) throws IOException {
        try (WriteBatch batch = db.createWriteBatch()) {
            for (Map.Entry<byte[], byte[]> entry : rows.entrySet()) {
                if (entry.getValue() == null) {
                    batch.delete(entry.getKey());
                } else {
                    LOGGER.info("key===" + Hex.toHexString(entry.getKey()) + "--- value : " + Hex.toHexString(entry.getValue()));
                    batch.put(entry.getKey(), entry.getValue());
                }
            }
            db.write(batch);
        }
    }

    @Override
    public void updateBatch(Map<byte[], byte[]> rows) {
        resetDbLock.readLock().lock();
        try {
            if (LOGGER.isTraceEnabled()) LOGGER.trace("~> LevelDbDataSource.updateBatch(): " + name + ", " + rows.size());
            try {
                updateBatchInternal(rows);
                if (LOGGER.isTraceEnabled()) LOGGER.trace("<~ LevelDbDataSource.updateBatch(): " + name + ", " + rows.size());
            } catch (Exception e) {
                LOGGER.error("Error, retrying one more time...", e);
                // try one more time
                try {
                    updateBatchInternal(rows);
                    if (LOGGER.isTraceEnabled()) LOGGER.trace("<~ LevelDbDataSource.updateBatch(): " + name + ", " + rows.size());
                } catch (Exception e1) {
                    LOGGER.error("Error", e);
                    throw new RuntimeException(e);
                }
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public boolean flush() {
        return false;
    }

    /**
     * get state hash
     *
     * @return hash
     */
    @Override
    public String getStateHash() {
        return null;
    }

    @Override
    public void close() {
        resetDbLock.writeLock().lock();
        try {
            if (!isAlive()) return;

            try {
                LOGGER.debug("Close db: {}", name);
                db.close();

                alive = false;
            } catch (IOException e) {
                LOGGER.error("Failed to find the db file on the close: {} ", name);
            }
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }
}