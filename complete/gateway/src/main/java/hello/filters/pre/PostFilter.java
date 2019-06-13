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
import org.springframework.util.ReflectionUtils;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;


public class PostFilter extends ZuulFilter {

    protected static final String SEND_ERROR_FILTER_RAN = "sendErrorFilter.ran";

    private static Logger log = LoggerFactory.getLogger(PostFilter.class);

    private static DynamicIntProperty INITIAL_STREAM_BUFFER_SIZE = DynamicPropertyFactory
            .getInstance()
            .getIntProperty(ZuulConstants.ZUUL_INITIAL_STREAM_BUFFER_SIZE, 1024);

    @Value("${error.path:/error}")
    private String errorPath;

    @Override
    public Object run() {
        try {
            RequestContext ctx = RequestContext.getCurrentContext();
            HttpServletRequest request = ctx.getRequest();

//            int statusCode = (Integer) ctx.get("error.status_code");
//            request.setAttribute("javax.servlet.error.status_code", statusCode);
//
//            if (ctx.containsKey("error.exception")) {
//                Object e = ctx.get("error.exception");
//                log.warn("Error during filtering", Throwable.class.cast(e));
//                request.setAttribute("javax.servlet.error.exception", e);
//            }
//
//            if (ctx.containsKey("error.message")) {
//                String message = (String) ctx.get("error.message");
//                request.setAttribute("javax.servlet.error.message", message);
//            }

            RequestDispatcher dispatcher = request.getRequestDispatcher(
                    this.errorPath);
            if (dispatcher != null) {
                ctx.set(SEND_ERROR_FILTER_RAN, true);
                if (!ctx.getResponse().isCommitted()) {
                    HttpServletResponse response = ctx.getResponse();
                    response.setContentType("application/json");
                    response.setStatus(400);
                    writeResponse();
                    //dispatcher.forward(request, response);
                }
            }
        }
        catch (Exception ex) {
            ReflectionUtils.rethrowRuntimeException(ex);
        }
        return null;
    }

    @Override
    public String filterType() {
        return "post";
    }

    @Override
    public int filterOrder() {
        return 100;
    }

    @Override
    public boolean shouldFilter() {
        log.info("this is post filter========================");
        RequestContext ctx = RequestContext.getCurrentContext();
        // only forward to errorPath if it hasn't been forwarded to already
        return ctx.containsKey("error.status_code")
                && !ctx.getBoolean(SEND_ERROR_FILTER_RAN, false);
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
