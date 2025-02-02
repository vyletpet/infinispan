package org.infinispan.server.resp.commands.string;

import io.netty.channel.ChannelHandlerContext;
import org.infinispan.server.resp.commands.Resp3Command;
import org.infinispan.server.resp.ByteBufferUtils;
import org.infinispan.server.resp.Consumers;
import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespCommand;
import org.infinispan.server.resp.RespRequestHandler;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * @link https://redis.io/commands/append/
 * @since 15.0
 */
public class APPEND extends RespCommand implements Resp3Command {
   public APPEND() {
      super(3, 1, 1, 1);
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(Resp3Handler handler,
         ChannelHandlerContext ctx,
         List<byte[]> arguments) {
      if (arguments.size() != getArity() - 1) {
         ByteBufferUtils.stringToByteBuf("-ERR wrong number of arguments for 'append' command\r\n",
               handler.allocatorToUse());
         return handler.myStage();
      }
      return handler
            .stageToReturn(StringMutators.append(handler.cache(), arguments.get(0), arguments.get(1)),
                  ctx, Consumers.LONG_BICONSUMER);
   }
}
