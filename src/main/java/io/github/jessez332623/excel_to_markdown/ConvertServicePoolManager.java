package io.github.jessez332623.excel_to_markdown;

import io.github.jessez332623.excel_to_markdown.exception.exports.ScriptWorkerException;

import java.nio.file.Path;

/** Excel 表格转 Markdown Python 服务池管理器接口。*/
public interface ConvertServicePoolManager
{
    /**
     * 开放的执行转换接口 Excel -> Markdown
     *
     * @param tablePath 表格文件路径
     *
     * @return 转换完成后的 Markdown 文本
     *
     * @throws ScriptWorkerException 服务启动失败，转换失败最终抛出本异常
     */
    default String
    convertTableToMarkdown(Path tablePath) throws ScriptWorkerException
    {
        // 给一个安全的默认实现，
        // 避免使用者禁用依赖的时候出现注入错误。
        return null;
    }
}