package org.infinispan.interceptors.xsite;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.commands.CommandsFactory;
import org.infinispan.commands.functional.ReadWriteKeyCommand;
import org.infinispan.commands.functional.ReadWriteKeyValueCommand;
import org.infinispan.commands.functional.ReadWriteManyCommand;
import org.infinispan.commands.functional.ReadWriteManyEntriesCommand;
import org.infinispan.commands.functional.WriteOnlyKeyCommand;
import org.infinispan.commands.functional.WriteOnlyKeyValueCommand;
import org.infinispan.commands.functional.WriteOnlyManyCommand;
import org.infinispan.commands.functional.WriteOnlyManyEntriesCommand;
import org.infinispan.commands.write.ComputeCommand;
import org.infinispan.commands.write.ComputeIfAbsentCommand;
import org.infinispan.commands.write.DataWriteCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.PutMapCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.infinispan.commands.write.WriteCommand;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.container.impl.InternalEntryFactory;
import org.infinispan.context.InvocationContext;
import org.infinispan.distribution.DistributionInfo;
import org.infinispan.distribution.LocalizedCacheTopology;
import org.infinispan.encoding.DataConversion;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.functional.impl.Params;
import org.infinispan.interceptors.InvocationSuccessFunction;
import org.infinispan.marshall.core.MarshallableFunctions;

/**
 * Handles x-site data backups for non-transactional caches.
 *
 * @author Mircea Markus
 * @author Pedro Ruivo
 * @since 5.2
 */
public class NonTransactionalBackupInterceptor extends BaseBackupInterceptor {

   private final InvocationSuccessFunction<DataWriteCommand> handleSingleKeyWriteReturn = this::handleSingleKeyWriteReturn;
   private final InvocationSuccessFunction<WriteCommand> handleMultipleKeysWriteReturn = this::handleMultipleKeysWriteReturn;

   @Inject CommandsFactory commandsFactory;
   @Inject InternalEntryFactory internalEntryFactory;

   @Override
   public Object visitPutKeyValueCommand(InvocationContext ctx, PutKeyValueCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitRemoveCommand(InvocationContext ctx, RemoveCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitReplaceCommand(InvocationContext ctx, ReplaceCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitComputeCommand(InvocationContext ctx, ComputeCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitComputeIfAbsentCommand(InvocationContext ctx, ComputeIfAbsentCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitWriteOnlyKeyCommand(InvocationContext ctx, WriteOnlyKeyCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitReadWriteKeyValueCommand(InvocationContext ctx, ReadWriteKeyValueCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitReadWriteKeyCommand(InvocationContext ctx, ReadWriteKeyCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitWriteOnlyManyEntriesCommand(InvocationContext ctx, WriteOnlyManyEntriesCommand command) {
      return handleMultipleKeysWriteCommand(ctx, command);
   }

   @Override
   public Object visitWriteOnlyKeyValueCommand(InvocationContext ctx, WriteOnlyKeyValueCommand command) {
      return handleSingleKeyWriteCommand(ctx, command);
   }

   @Override
   public Object visitWriteOnlyManyCommand(InvocationContext ctx, WriteOnlyManyCommand command) {
      return handleMultipleKeysWriteCommand(ctx, command);
   }

   @Override
   public Object visitReadWriteManyCommand(InvocationContext ctx, ReadWriteManyCommand command) {
      return handleMultipleKeysWriteCommand(ctx, command);
   }

   @Override
   public Object visitReadWriteManyEntriesCommand(InvocationContext ctx, ReadWriteManyEntriesCommand command) {
      return handleMultipleKeysWriteCommand(ctx, command);
   }

   @Override
   public Object visitPutMapCommand(InvocationContext ctx, PutMapCommand command) {
      return handleMultipleKeysWriteCommand(ctx, command);
   }

   private Object handleSingleKeyWriteCommand(InvocationContext ctx, DataWriteCommand command) {
      if (skipXSiteBackup(command)) {
         return invokeNext(ctx, command);
      }
      return invokeNextThenApply(ctx, command, handleSingleKeyWriteReturn);
   }

   private Object handleSingleKeyWriteReturn(InvocationContext ctx, DataWriteCommand dataWriteCommand, Object rv) {
      if (!dataWriteCommand.isSuccessful()) {
         if (trace) {
            log.tracef("Command %s is not successful, not replicating", dataWriteCommand);
         }
         return rv;
      }
      int segment = dataWriteCommand.getSegment();
      DistributionInfo dInfo = clusteringDependentLogic.getCacheTopology().getSegmentDistribution(segment);
      if (dInfo.isWriteOwner()) {
         //all owners has to keep track of the updates even if the primary owner is the only one who sends it.
         iracManager.trackUpdatedKey(segment, dataWriteCommand.getKey(), dataWriteCommand.getCommandInvocationId());
      }
      if (dInfo.isPrimary()) { //primary owner sends for sync backups
         CacheEntry<?,?> entry = ctx.lookupEntry(dataWriteCommand.getKey());
         WriteCommand crossSiteCommand = createCommandForXSite(entry, segment, dataWriteCommand.getFlagsBitSet());
         return backupSender.backupWrite(crossSiteCommand, dataWriteCommand).thenReturn(ctx, dataWriteCommand, rv);
      }
      return rv;
   }

   private WriteCommand createCommandForXSite(CacheEntry<?, ?> entry, int segment, long flagsBitSet) {
      return entry.isRemoved() ?
             commandsFactory.buildRemoveCommand(entry.getKey(), null, segment, flagsBitSet) :
             commandsFactory.buildPutKeyValueCommand(entry.getKey(), entry.getValue(), segment, entry.getMetadata(),
                   flagsBitSet);
   }

   private Object handleMultipleKeysWriteCommand(InvocationContext ctx, WriteCommand command) {
      if (trace) log.tracef("Processing %s", command);
      if (skipXSiteBackup(command)) {
         return invokeNext(ctx, command);
      }
      return invokeNextThenApply(ctx, command, handleMultipleKeysWriteReturn);
   }

   private Object handleMultipleKeysWriteReturn(InvocationContext ctx, WriteCommand writeCommand, Object rv) {
      if (trace) log.tracef("Processing post %s", writeCommand);
      if (!writeCommand.isSuccessful()) {
         if (trace) {
            log.tracef("Command %s is not successful, not replicating", writeCommand);
         }
         return rv;
      }
      Map<Object, Object> map = new HashMap<>(); // must support null values
      LocalizedCacheTopology localizedCacheTopology = clusteringDependentLogic.getCacheTopology();
      for (Object key : writeCommand.getAffectedKeys()) {
         DistributionInfo info = localizedCacheTopology.getDistribution(key);
         if (info.isWriteOwner()) { //all owners need to keep track.
            iracManager.trackUpdatedKey(info.segmentId(), key, writeCommand.getCommandInvocationId());
         }
         if (!info.isPrimary()) {
            if (trace) {
               log.tracef("Not replicating write to key %s as the primary owner is %s", key, info.primary());
            }
            continue;
         }
         CacheEntry<?,?> entry = ctx.lookupEntry(key);
         if (entry instanceof InternalCacheEntry) {
            map.put(key, ((InternalCacheEntry<?,?>) entry).toInternalCacheValue());
         } else {
            map.put(key, internalEntryFactory.createValue(entry));
         }
      }
      if (map.isEmpty()) {
         return rv;
      }
      //TODO: Converters
      WriteCommand crossSiteCommand = commandsFactory.buildWriteOnlyManyEntriesCommand(map,
            MarshallableFunctions.setInternalCacheValueConsumer(),
            Params.fromFlagsBitSet(writeCommand.getFlagsBitSet()), DataConversion.IDENTITY_KEY,
            DataConversion.IDENTITY_VALUE);
      return backupSender.backupWrite(crossSiteCommand, writeCommand).thenReturn(ctx, writeCommand, rv);
   }
}
