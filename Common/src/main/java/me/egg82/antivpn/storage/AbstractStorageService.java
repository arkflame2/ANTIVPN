package me.egg82.antivpn.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.ebean.Database;
import io.ebean.DatabaseFactory;
import io.ebean.config.DatabaseConfig;
import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebean.config.dbplatform.sqlite.SQLitePlatform;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.persistence.PersistenceException;
import me.egg82.antivpn.storage.models.BaseModel;
import me.egg82.antivpn.storage.models.IPModel;
import me.egg82.antivpn.storage.models.PlayerModel;
import me.egg82.antivpn.storage.models.query.QIPModel;
import me.egg82.antivpn.storage.models.query.QPlayerModel;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractStorageService implements StorageService {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final String name;
    protected Database connection;
    protected HikariDataSource source;

    public @NonNull String getName() { return name; }

    private volatile boolean closed = false;
    private final ReadWriteLock queueLock = new ReentrantReadWriteLock();

    protected AbstractStorageService(@NonNull String name) {
        this.name = name;
    }

    public void close() {
        queueLock.writeLock().lock();
        closed = true;
        connection.shutdown(false, false);
        source.close();
        queueLock.writeLock().unlock();
    }

    public boolean isClosed() { return closed; }

    public void storeModel(@NonNull BaseModel model) {
        model.setModified(null);

        queueLock.readLock().lock();
        try {
            connection.save(model);
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public void storeModels(@NonNull Collection<? extends BaseModel> models) {
        for (BaseModel model : models) {
            model.setModified(null);
        }

        queueLock.readLock().lock();
        try {
            connection.saveAll(models);
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public void deleteModel(@NonNull BaseModel model) {
        queueLock.readLock().lock();
        try {
            connection.delete(model);
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @NonNull IPModel getOrCreateIpModel(@NonNull String ip, int type) {
        queueLock.readLock().lock();
        try {
            IPModel model = new QIPModel(connection)
                    .ip.equalTo(ip)
                    .findOne();
            if (model == null) {
                model = new IPModel();
                model.setIp(ip);
                model.setType(type);
                connection.save(model);
                model = new QIPModel(connection)
                        .ip.equalTo(ip)
                        .findOne();
                if (model == null) {
                    throw new PersistenceException("findOne() returned null after saving.");
                }
            }
            if (model.getType() != type) {
                model.setType(type);
                model.setModified(null);
                connection.save(model);
            }
            return model;
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @Nullable IPModel getIpModel(@NonNull String ip, long cacheTimeMillis) {
        queueLock.readLock().lock();
        try {
            return new QIPModel(connection)
                    .ip.equalTo(ip)
                    .modified.after(Instant.now().minusMillis(cacheTimeMillis))
                    .findOne();
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @Nullable IPModel getIpModel(long ipId, long cacheTimeMillis) {
        queueLock.readLock().lock();
        try {
            return new QIPModel(connection)
                    .id.equalTo(ipId)
                    .modified.after(Instant.now().minusMillis(cacheTimeMillis))
                    .findOne();
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @NonNull Set<IPModel> getAllIps(long cacheTimeMillis) {
        queueLock.readLock().lock();
        try {
            return new QIPModel(connection)
                    .modified.after(Instant.now().minusMillis(cacheTimeMillis))
                    .findSet();
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @NonNull Set<IPModel> getAllIps(int start, int end) {
        queueLock.readLock().lock();
        try {
            return new QIPModel(connection)
                    .id.between(start - 1, end + 1)
                    .findSet();
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @NonNull PlayerModel getOrCreatePlayerModel(@NonNull UUID player, boolean isMcLeaks) {
        queueLock.readLock().lock();
        try {
            PlayerModel model = new QPlayerModel(connection)
                    .uuid.equalTo(player)
                    .findOne();
            if (model == null) {
                model = new PlayerModel();
                model.setUuid(player);
                model.setMcleaks(isMcLeaks);
                connection.save(model);
                model = new QPlayerModel(connection)
                        .uuid.equalTo(player)
                        .findOne();
                if (model == null) {
                    throw new PersistenceException("findOne() returned null after saving.");
                }
            }
            if (model.isMcleaks() != isMcLeaks) {
                model.setMcleaks(isMcLeaks);
                model.setModified(null);
                connection.save(model);
            }
            return model;
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @Nullable PlayerModel getPlayerModel(@NonNull UUID player, long cacheTimeMillis) {
        queueLock.readLock().lock();
        try {
            return new QPlayerModel(connection)
                    .uuid.equalTo(player)
                    .modified.after(Instant.now().minusMillis(cacheTimeMillis))
                    .findOne();
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @Nullable PlayerModel getPlayerModel(long playerId, long cacheTimeMillis) {
        queueLock.readLock().lock();
        try {
            return new QPlayerModel(connection)
                    .id.equalTo(playerId)
                    .modified.after(Instant.now().minusMillis(cacheTimeMillis))
                    .findOne();
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @NonNull Set<PlayerModel> getAllPlayers(long cacheTimeMillis) {
        queueLock.readLock().lock();
        try {
            return new QPlayerModel(connection)
                    .modified.after(Instant.now().minusMillis(cacheTimeMillis))
                    .findSet();
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public @NonNull Set<PlayerModel> getAllPlayers(int start, int end) {
        queueLock.readLock().lock();
        try {
            return new QPlayerModel(connection)
                    .id.between(start - 1, end + 1)
                    .findSet();
        } finally {
            queueLock.readLock().unlock();
        }
    }

    protected final void createSource(HikariConfig config, DatabasePlatform platform, String scriptPath) {
        boolean isAutoCommit = config.isAutoCommit();
        if (isAutoCommit) {
            config.setAutoCommit(false);
            source = new HikariDataSource(config);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setDataSource(source);
            dbConfig.setDatabasePlatform(platform);
            dbConfig.setDefaultServer(false);
            dbConfig.setRegister(false);
            dbConfig.setName(UUID.randomUUID().toString());
            dbConfig.setClasses(Arrays.asList(BaseModel.class, IPModel.class, PlayerModel.class));
            connection = DatabaseFactory.createWithContextClassLoader(dbConfig, getClass().getClassLoader());
            connection.script().run(scriptPath);
            connection.shutdown(false, false);
            source.close();
            config.setAutoCommit(true);
        }

        source = new HikariDataSource(config);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setDataSource(source);
        dbConfig.setDatabasePlatform(new SQLitePlatform());
        dbConfig.setDefaultServer(false);
        dbConfig.setRegister(false);
        dbConfig.setName(name);
        dbConfig.setClasses(Arrays.asList(BaseModel.class, IPModel.class, PlayerModel.class));
        connection = DatabaseFactory.createWithContextClassLoader(dbConfig, getClass().getClassLoader());
        if (!isAutoCommit) {
            connection.script().run(scriptPath);
        }
    }
}
