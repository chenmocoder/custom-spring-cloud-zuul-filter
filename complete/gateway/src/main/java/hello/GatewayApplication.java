package hello;

import hello.filters.pre.HerderFilter;
import hello.filters.pre.PostFilter;
import hello.filters.pre.SimpleFilter;
import hello.filters.pre.SysErrFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;


@EnableZuulProxy
@SpringBootApplication
public class GatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }

  @Bean
  public SimpleFilter simpleFilter() {
    return new SimpleFilter();
  }

  @Bean
  public HerderFilter herderFilter() {
    return new HerderFilter();
  }

  @Bean
  public SysErrFilter sysErrFilter() {
    return new SysErrFilter();
  }

  @Bean
  public PostFilter postFilter() {
    return new PostFilter();
  }

  @RequestMapping("/error")
  public String error() {
      return "error";
  }

}
