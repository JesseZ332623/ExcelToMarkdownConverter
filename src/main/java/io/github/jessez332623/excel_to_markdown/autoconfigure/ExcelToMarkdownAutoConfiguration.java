package io.github.jessez332623.excel_to_markdown.autoconfigure;

import io.github.jessez332623.excel_to_markdown.ConvertServicePoolManager;
import io.github.jessez332623.excel_to_markdown.impl.DefaultConvertServicePoolManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Excel -> Markdown 转换服务 Spring 自动配置类。*/
@Configuration
@ConditionalOnProperty(
    prefix         = "app.excel-to-markdown",
    name           = "enabled",
    havingValue    = "true",
    matchIfMissing = false
)
@EnableConfigurationProperties(ExcelToMarkdownProperties.class)
public class ExcelToMarkdownAutoConfiguration
{
    /** 最大服务进程数限制。*/
    final static int MAX_PROCESS = 32;

    /**
     * 按照 {@link ExcelToMarkdownProperties} 提供的配置，自动创建转换服务。
     */
    @Bean
    @ConditionalOnMissingBean(ConvertServicePoolManager.class)
    public ConvertServicePoolManager
    convertServicePoolManager(@NotNull ExcelToMarkdownProperties properties)
    {
        return new
        DefaultConvertServicePoolManager(
            Math.min(properties.getProcesses(), MAX_PROCESS),
            properties.getDestroy().getMaxWaitSeconds(),
            properties.getDestroy().getWaitIntervalMillis()
        );
    }
}