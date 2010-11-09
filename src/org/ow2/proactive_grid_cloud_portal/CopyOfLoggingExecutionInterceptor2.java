package org.ow2.proactive_grid_cloud_portal;

import java.io.IOException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;

import org.apache.log4j.Logger;
import org.jboss.resteasy.annotations.interception.Precedence;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.spi.interception.MessageBodyWriterContext;
import org.jboss.resteasy.spi.interception.MessageBodyWriterInterceptor;
import org.jboss.util.StopWatch;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.ow2.proactive.scheduler.common.util.SchedulerLoggers;

@Provider
@ServerInterceptor
@Precedence("HEADER_DECORATOR")
public class CopyOfLoggingExecutionInterceptor2 implements MessageBodyWriterInterceptor
{
   private static Logger logger = ProActiveLogger.getLogger(SchedulerLoggers.PREFIX + ".rest");



//   @SuppressWarnings("unchecked")
//   public ClientResponse execute(ClientExecutionContext ctx) throws Exception
//   {
//      String uri = ctx.getRequest().getUri();
//      logger.info(String.format("Reading url %s", uri));
//      StopWatch stopWatch = new StopWatch();
//      stopWatch.start();
//      ClientResponse response = ctx.proceed();
//      stopWatch.stop();
//      String contentLength = (String) response.getMetadata().getFirst(
//              HttpHeaderNames.CONTENT_LENGTH);
//      logger.info(String.format("Read url %s in %d ms size %s.", uri,
//              stopWatch.getTime(), contentLength));
//      System.out.println("SDDDDDDDDDDDDDDDDDDDDDDDSsssssssssssss");
//      return response;
//   }

   public void write(MessageBodyWriterContext ctx) throws IOException,
           WebApplicationException
   {



       System.out.println("reeeeeeeeeeeeeeee");
      StopWatch stopWatch = new StopWatch();
      stopWatch.start();
      try
      {
          ctx.proceed();
      }
      finally
      {
         stopWatch.stop();
         logger.info(String.format("Read mediaType %s as %s in %d ms.", ctx
                 .getMediaType().toString(), ctx.getType().getName(),
                 stopWatch.getTime()));
      }
   }
}
