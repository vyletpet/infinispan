package org.infinispan.server.resp.commands.connection;

import io.netty.channel.ChannelHandlerContext;
import org.infinispan.server.resp.ByteBufferUtils;
import org.infinispan.server.resp.commands.Resp3Command;
import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespCommand;
import org.infinispan.server.resp.RespRequestHandler;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * @link https://redis.io/commands/select/
 * @since 14.0
 */
public class SELECT extends RespCommand implements Resp3Command {
   public SELECT() {
      super(-1, 0, 0, 0);
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(Resp3Handler handler,
                                                      ChannelHandlerContext ctx,
                                                      List<byte[]> arguments) {
      ByteBufferUtils
            .stringToByteBuf("-ERR Select not supported in cluster mode\r\n", handler.allocatorToUse());
      return handler.myStage();
   }
}
