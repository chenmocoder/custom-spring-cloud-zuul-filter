package hello.filters.pre;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;


public class HerderFilter extends ZuulFilter {

    private static Logger log = LoggerFactory.getLogger(HerderFilter.class);

    @Override
    public Object run() {
        log.info("this is headerFilter==");
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        log.info("request header ContentType="+request.getHeader("Content-Type"));
        log.info("request header userAgent="+ request.getHeader("User-Agent"));
        return null;
    }

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }
}
