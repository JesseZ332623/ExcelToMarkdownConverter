package cn.jesse.excel_to_markdown.service_pool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring 依赖自动配置属性类。
 *
 * <p>用法：</p>
 * <ol>
 *     <li>
 *         在配置文件中使用
 *              app.excel-to-markdown.enabled=true
 *         来启用本转换服务
 *     </li>
 *     <li>
 *         使用 app.excel-to-markdown.processes=?
 *         来控制服务进程的数量
 *     </li>
 * </ol>
 */
@Data
@ConfigurationProperties(prefix = "app.excel-to-markdown")
public class ExcelToMarkdownProperties
{
    /** 是否启动本服务？（默认不启用）*/
    private boolean enabled = false;

    /** 服务最大进程数是？（默认为 4）*/
    private int processes = 4;
}