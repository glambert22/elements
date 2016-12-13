/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.persist.hibernate;

import com.google.inject.Inject;
import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.resources.PersistenceListener;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.serialization.ObjectReference;
import net.e6tech.elements.persist.EvictCollectionRegion;
import net.e6tech.elements.persist.EvictEntity;
import net.e6tech.elements.persist.PersistenceInterceptor;
import net.e6tech.elements.persist.Watcher;
import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.Type;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * Created by futeh.
 */
public class Interceptor extends EmptyInterceptor implements PersistenceInterceptor {

    @Inject(optional = true)
    Resources resources;

    SessionFactoryImplementor sessionFactory;

    @Inject(optional = true)
    NotificationCenter center;

    public Resources getResources() {
        return resources;
    }

    @Override
    public void setResources(Resources resources) {
        this.resources = resources;
    }

    @Override
    public boolean onLoad(
            Object entity,
            Serializable id,
            Object[] state,
            String[] propertyNames,
            Type[] types) {
        // commented out because of performance impact
        // if (resources != null) resources.inject(entity);
        boolean modified = false;
        if (entity instanceof PersistenceListener) {
            if (resources != null) resources.inject(entity);
            long start = System.currentTimeMillis();
            modified = ((PersistenceListener) entity).onLoad(id, state, propertyNames);
            Watcher.addGracePeriod(System.currentTimeMillis() - start);
        }
        return modified;
    }

    /*
     * When persist is called ...
     */
    @Override
    public boolean onSave(
            Object entity,
            Serializable id,
            Object[] state,
            String[] propertyNames,
            Type[] types) {
        // commented out because of performance impact
        // if (resources != null) resources.inject(entity);
        boolean modified = false;
        if (entity instanceof PersistenceListener) {
            if (resources != null) resources.inject(entity);
            long start = System.currentTimeMillis();
            modified = ((PersistenceListener) entity).onSave(id, state, propertyNames);
            Watcher.addGracePeriod(System.currentTimeMillis() - start);
        }

        publishEntityChanged(entity, id);

        return modified;
    }

    /*
     * This call is triggered by execution of queries or commit
     */
    @Override
    public void preFlush(Iterator entities) {
        List<PersistenceListener> listeners = null;
        while (entities.hasNext()) {
            Object entity = entities.next();
            if (entity instanceof PersistenceListener) {
                if (listeners == null) listeners = new ArrayList<>();
                listeners.add((PersistenceListener) entity);
            }
        }
        if (listeners != null) {
            // this can be parallelized.
            try {
                long start = System.currentTimeMillis();
                listeners.stream().map(listener ->  CompletableFuture.runAsync(()-> listener.preFlush()))
                        .collect(Collectors.toList())
                        .forEach(future -> future.join());
                Watcher.addGracePeriod(System.currentTimeMillis() - start);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
        publishCollectionChanged(collection, key);
    }

    @Override
    public void onCollectionRemove(Object collection, Serializable key) throws CallbackException {
        publishCollectionChanged(collection, key);
    }

    protected void publishCollectionChanged(Object collection, Serializable key) {

        if (center != null && collection instanceof PersistentCollection) {
            PersistentCollection coll = (PersistentCollection) collection;
            boolean cached = false;
            if (sessionFactory != null) {
                cached = sessionFactory.getCollectionPersister(coll.getRole()).hasCache();
            }

            /* Another way of doing it
            EntityManager em = resources.getInstance(EntityManager.class);
            Cache cache = em.unwrap(Session.class).getSessionFactory().getCache();
            boolean cached = cache.containsCollection(coll.getRole(), key);
            */
            if (cached) {
                //publisher.publish(EntityManagerProvider.CACHE_EVICT_COLLECTION_REGION, coll.getRole());
                // center.fireNotification(new EvictCollectionRegion(coll.getRole()));
                center.publish(EvictCollectionRegion.class, new EvictCollectionRegion(coll.getRole()));
            }

        }
    }

    protected void publishEntityChanged(Object entity, Serializable key) {
        boolean cached = false;
        if (center != null) {
            if (sessionFactory != null) {
                String entityName = sessionFactory.getClassMetadata(entity.getClass()).getEntityName();
                cached = sessionFactory.getEntityPersister(entityName).hasCache();
            }
            if (cached) {
                // center.fireNotification(new EvictEntity(this, new ObjectReference(entity.getClass(), key)));
                center.publish(EvictEntity.class, new EvictEntity(new ObjectReference(entity.getClass(), key)));
            }
        }
    }
}