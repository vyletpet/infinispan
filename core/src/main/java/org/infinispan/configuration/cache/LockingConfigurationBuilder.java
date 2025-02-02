package org.infinispan.configuration.cache;

import static org.infinispan.configuration.cache.LockingConfiguration.CONCURRENCY_LEVEL;
import static org.infinispan.configuration.cache.LockingConfiguration.ISOLATION_LEVEL;
import static org.infinispan.configuration.cache.LockingConfiguration.LOCK_ACQUISITION_TIMEOUT;
import static org.infinispan.configuration.cache.LockingConfiguration.USE_LOCK_STRIPING;

import java.util.concurrent.TimeUnit;

import org.infinispan.commons.configuration.Builder;
import org.infinispan.commons.configuration.attributes.Attribute;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.util.concurrent.IsolationLevel;

/**
 * Defines the local, in-VM locking and concurrency characteristics of the cache.
 *
 * @author pmuir
 *
 */
public class LockingConfigurationBuilder extends AbstractConfigurationChildBuilder implements Builder<LockingConfiguration> {

   private final AttributeSet attributes;

   protected LockingConfigurationBuilder(ConfigurationBuilder builder) {
      super(builder);
      attributes = LockingConfiguration.attributeDefinitionSet();
   }

   @Override
   public AttributeSet attributes() {
      return attributes;
   }

   /**
    * Concurrency level for lock containers. Adjust this value according to the number of concurrent
    * threads interacting with Infinispan. Similar to the concurrencyLevel tuning parameter seen in
    * the JDK's ConcurrentHashMap.
    */
   public LockingConfigurationBuilder concurrencyLevel(int i) {
      attributes.attribute(CONCURRENCY_LEVEL).set(i);
      return this;
   }

   /**
    * Cache isolation level. Infinispan only supports READ_COMMITTED or REPEATABLE_READ isolation
    * levels. See <a href=
    * 'http://en.wikipedia.org/wiki/Isolation_level'>http://en.wikipedia.org/wiki/Isolation_level</a
    * > for a discussion on isolation levels.
    */
   public LockingConfigurationBuilder isolationLevel(IsolationLevel isolationLevel) {
      attributes.attribute(ISOLATION_LEVEL).set(isolationLevel);
      return this;
   }

   public IsolationLevel isolationLevel() {
      return attributes.attribute(ISOLATION_LEVEL).get();
   }

   /**
    * Maximum time to attempt a particular lock acquisition
    */
   public LockingConfigurationBuilder lockAcquisitionTimeout(long l) {
      attributes.attribute(LOCK_ACQUISITION_TIMEOUT).set(l);
      return this;
   }

   /**
    * Maximum time to attempt a particular lock acquisition
    */
   public LockingConfigurationBuilder lockAcquisitionTimeout(long l, TimeUnit unit) {
      return lockAcquisitionTimeout(unit.toMillis(l));
   }

   /**
    * If true, a pool of shared locks is maintained for all entries that need to be locked.
    * Otherwise, a lock is created per entry in the cache. Lock striping helps control memory
    * footprint but may reduce concurrency in the system.
    */
   public LockingConfigurationBuilder useLockStriping(boolean b) {
      attributes.attribute(USE_LOCK_STRIPING).set(b);
      return this;
   }

   @Override
   public void validate() {
      Attribute<IsolationLevel> isolationLevel = attributes.attribute(ISOLATION_LEVEL);
      if (getBuilder().clustering().cacheMode().isClustered() && isolationLevel.get() == IsolationLevel.NONE)
         isolationLevel.set(IsolationLevel.READ_COMMITTED);

      if (isolationLevel.get() == IsolationLevel.READ_UNCOMMITTED)
         isolationLevel.set(IsolationLevel.READ_COMMITTED);

      if (isolationLevel.get() == IsolationLevel.SERIALIZABLE)
         isolationLevel.set(IsolationLevel.REPEATABLE_READ);
   }

   @Override
   public void validate(GlobalConfiguration globalConfig) {
   }

   @Override
   public LockingConfiguration create() {
      return new LockingConfiguration(attributes.protect());
   }

   @Override
   public LockingConfigurationBuilder read(LockingConfiguration template) {
      this.attributes.read(template.attributes());
      return this;
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + "[" + attributes + "]";
   }
}
