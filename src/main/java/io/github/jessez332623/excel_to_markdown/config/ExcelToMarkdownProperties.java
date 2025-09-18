package io.github.jessez332623.excel_to_markdown.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring 依赖自动配置属性类。*/
@Data
@ConfigurationProperties(prefix = "app.excel-to-markdown")
public class ExcelToMarkdownProperties
{
    /** 是否启动本服务？（默认不启用）*/
    private boolean enabled = false;

    /** 服务最大进程数是？（默认为 4）*/
    private int processes = 4;

    private Destroy destroy = new Destroy();

    /**
     * 在关闭服务池前，
     * 等待所有服务处理完手头的任务相关的属性。
     */
    @Data
    @NoArgsConstructor
    public static class Destroy
    {
        /** 最多给池中的服务多少时间去处理完手头的任务？（默认 15 秒）*/
        private int maxWaitSeconds = 15;

        /** 每隔多久去检查池中服务的状态？（默认 500 毫秒）*/
        private int waitIntervalMillis = 500;
    }
}