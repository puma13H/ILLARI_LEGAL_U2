package com.illarilegal.cliente;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.LoggerFactory;
import java.io.IOException;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
            throws ServletException,IOException {
        long start=System.nanoTime();
        try(var scope=new Correlation(req.getHeader("X-Trace-ID"),null)) {
            res.setHeader("X-Trace-ID",Correlation.current());
            try { chain.doFilter(req,res); }
            finally { LoggerFactory.getLogger("ILLARI_OBS").info("http_completed method={} status={} durationMs={}",
                req.getMethod(),res.getStatus(),(System.nanoTime()-start)/1000000); }
        }
    }
}
