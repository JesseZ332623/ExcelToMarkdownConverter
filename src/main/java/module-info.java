// 基于 markitdown 开源库的高性能 Excel 表转 Markdown 服务池模块声明
module excel_to_markdown
{
    // Spring 相关依赖
    requires spring.core;
    requires spring.boot;
    requires spring.context;
    requires spring.boot.autoconfigure;
    requires spring.beans;

    // Lombok（编译时依赖）
    requires static lombok;

    // 注解相关
    requires static jakarta.annotation;
    requires static org.jetbrains.annotations;

    // 日志
    requires transitive org.slf4j;

    // 导出公共 API 包
    exports io.github.jessez332623.excel_to_markdown.exception.exports;
    exports io.github.jessez332623.excel_to_markdown.autoconfigure;
    exports io.github.jessez332623.excel_to_markdown;

    // 开放包给 Spring 反射
    opens io.github.jessez332623.excel_to_markdown.autoconfigure
        to spring.core, spring.context;
    opens io.github.jessez332623.excel_to_markdown.impl
        to spring.core, spring.context, spring.beans;
}