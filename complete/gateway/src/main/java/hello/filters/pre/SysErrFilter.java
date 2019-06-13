package hello.filters.pre;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.constants.ZuulHeaders;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.util.HTTPRequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.zuul.filters.post.SendErrorFilter;


import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;


public class SysErrFilter extends ZuulFilter {

    private static Logger log = LoggerFactory.getLogger(SysErrFilter.class);

    private static DynamicIntProperty INITIAL_STREAM_BUFFER_SIZE = DynamicPropertyFactory
            .getInstance()
            .getIntProperty(ZuulConstants.ZUUL_INITIAL_STREAM_BUFFER_SIZE, 1024);

    @Value("${error.path:/error}")
    private String error;

    @Override
    public boolean shouldFilter() {
        log.info("this is errorFilter=========================");
        RequestContext ctx = RequestContext.getCurrentContext();
        log.info("boolean"+ctx.getThrowable().equals(null));
        // only forward to errorPath if it hasn't been forwarded to already
        return !ctx.getThrowable().equals(null);
    }

    @Override
    public int filterOrder() {
        return 3;
    }

    @Override
    public String filterType() {
        return "error";
    }

    @Override
    public Object run() {
        try {
            log.info("error filter run=============");
            RequestContext context = RequestContext.getCurrentContext();
            HttpServletResponse servletResponse = context.getResponse();
            OutputStream outStream = servletResponse.getOutputStream();
            InputStream body = RequestContext.getCurrentContext().getResponseDataStream();
            String tempStr = "hello error";
            body = new ByteArrayInputStream(tempStr.getBytes("UTF-8"));
            writeResponse(body,outStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void writeResponse() throws Exception {
        RequestContext context = RequestContext.getCurrentContext();
        HttpServletResponse servletResponse = context.getResponse();
        OutputStream outStream = servletResponse.getOutputStream();
        // there is no body to send
        if (context.getResponseBody() == null
                && context.getResponseDataStream() == null) {
            log.info("response body is null========================================");
            InputStream body = RequestContext.getCurrentContext().getResponseDataStream();
            writeResponse(body,outStream);
            return;
        }

        if (servletResponse.getCharacterEncoding() == null) { // only set if not set
            servletResponse.setCharacterEncoding("UTF-8");
        }

        InputStream is = null;
        try {
            if (RequestContext.getCurrentContext().getResponseBody() != null) {
                log.info("current context response body is null========================================");
                InputStream body = RequestContext.getCurrentContext().getResponseDataStream();
                writeResponse(body,outStream);
                return;
            }
            boolean isGzipRequested = false;
            final String requestEncoding = context.getRequest()
                    .getHeader(ZuulHeaders.ACCEPT_ENCODING);

            if (requestEncoding != null
                    && HTTPRequestUtils.getInstance().isGzipped(requestEncoding)) {
                isGzipRequested = true;
            }
            is = context.getResponseDataStream();
            InputStream inputStream = is;
            if (is != null) {
                if (context.sendZuulResponse()) {
                    // if origin response is gzipped, and client has not requested gzip,
                    // decompress stream
                    // before sending to client
                    // else, stream gzip directly to client
                    if (context.getResponseGZipped() && !isGzipRequested) {
                        // If origin tell it's GZipped but the content is ZERO bytes,
                        // don't try to uncompress
                        final Long len = context.getOriginContentLength();
                        if (len == null || len > 0) {
                            try {
                                inputStream = new GZIPInputStream(is);
                            }
                            catch (java.util.zip.ZipException ex) {
                                log.debug(
                                        "gzip expected but not "
                                                + "received assuming unencoded response "
                                                + RequestContext.getCurrentContext()
                                                .getRequest().getRequestURL()
                                                .toString());
                                inputStream = is;
                            }
                        }
                        else {
                            // Already done : inputStream = is;
                        }
                    }
                    else if (context.getResponseGZipped() && isGzipRequested) {
                        servletResponse.setHeader(ZuulHeaders.CONTENT_ENCODING, "gzip");
                    }
                    writeResponse(inputStream, outStream);
                }
            }
        }
        finally {
            try {
                if (is != null) {
                    is.close();
                }
                outStream.flush();
                // The container will close the stream for us
            }
            catch (IOException ex) {
            }
        }
    }

    private void writeResponse(InputStream zin, OutputStream out) throws Exception {
        byte[] bytes = new byte[INITIAL_STREAM_BUFFER_SIZE.get()];
        int bytesRead = -1;
        while ((bytesRead = zin.read(bytes)) != -1) {
            try {
                out.write(bytes, 0, bytesRead);
                out.flush();
            }
            catch (IOException ex) {
                // ignore
            }
            // doubles buffer size if previous read filled it
            if (bytesRead == bytes.length) {
                bytes = new byte[bytes.length * 2];
            }
        }
    }

}
