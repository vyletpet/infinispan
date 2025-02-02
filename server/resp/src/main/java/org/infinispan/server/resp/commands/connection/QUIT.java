package org.infinispan.server.resp.commands.connection;

import io.netty.channel.ChannelHandlerContext;
import org.infinispan.server.resp.commands.AuthResp3Command;
import org.infinispan.server.resp.commands.PubSubResp3Command;
import org.infinispan.server.resp.Resp3AuthHandler;
import org.infinispan.server.resp.RespCommand;
import org.infinispan.server.resp.RespRequestHandler;
import org.infinispan.server.resp.SubscriberHandler;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * @link https://redis.io/commands/quit/
 * @since 14.0
 */
public class QUIT extends RespCommand implements AuthResp3Command, PubSubResp3Command {

   public QUIT() {
      super(1, 0, 0, 0);
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(Resp3AuthHandler handler,
                                                      ChannelHandlerContext ctx,
                                                      List<byte[]> arguments) {
      ctx.close();
      return handler.myStage();
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(SubscriberHandler handler, ChannelHandlerContext ctx,
                                                                List<byte[]> arguments) {
      handler.removeAllListeners();
      return handler.resp3Handler().handleRequest(ctx, this, arguments);
   }
}
